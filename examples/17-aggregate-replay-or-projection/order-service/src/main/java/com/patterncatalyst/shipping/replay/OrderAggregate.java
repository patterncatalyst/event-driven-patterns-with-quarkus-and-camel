package com.patterncatalyst.shipping.replay;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import com.patterncatalyst.shipping.Carrier;
import com.patterncatalyst.shipping.OrderStatus;

/**
 * The order aggregate of chapter 14, reduced to what this chapter needs: it is rebuilt from its stream, decides a
 * command against its state and returns the events that follow. A replay folds one of these per stream.
 */
public final class OrderAggregate {

    private int version;
    private OrderStatus status;
    private String paymentId;
    private Carrier carrier;
    private Instant shippedAt;

    private OrderAggregate() {
    }

    public static OrderAggregate start() {
        return new OrderAggregate();
    }

    public static OrderAggregate rehydrate(List<StoredEvent> stream) {
        OrderAggregate order = start();
        for (StoredEvent stored : stream) {
            order.apply(stored.payload, stored.occurredAt);
        }
        return order;
    }

    /** Folds one stored event into the state. */
    public void apply(OrderEvent event, Instant occurredAt) {
        version++;
        switch (event) {
            case OrderEvent.OrderPlaced placed -> status = OrderStatus.PLACED;
            case OrderEvent.OrderPaid paid -> {
                status = OrderStatus.PAID;
                paymentId = paid.paymentId();
            }
            case OrderEvent.OrderShipped shipped -> {
                status = OrderStatus.SHIPPED;
                carrier = shipped.carrier();
                shippedAt = occurredAt;
            }
            case OrderEvent.OrderDelivered delivered -> status = OrderStatus.DELIVERED;
            case OrderEvent.OrderCancelled cancelled -> status = OrderStatus.CANCELLED;
        }
    }

    public int version() {
        return version;
    }

    public OrderStatus status() {
        return status;
    }

    /** True once the order has left the warehouse: shipped, and possibly delivered since. */
    public boolean hasShipped() {
        return shippedAt != null;
    }

    public Carrier carrier() {
        return carrier;
    }

    /** The UTC day of the shipment. */
    public LocalDate shippedOn() {
        return shippedAt.atZone(ZoneOffset.UTC).toLocalDate();
    }

    /** The events this command produces against the current state; the aggregate applies them to itself. */
    public List<OrderEvent> decide(Command command) {
        List<OrderEvent> events = new ArrayList<>();
        switch (command) {
            case Command.PlaceOrder place -> {
                if (status != null) {
                    throw new CommandRefused("order exists");
                }
                events.add(new OrderEvent.OrderPlaced(place.orderId()));
            }
            case Command.PayOrder pay -> {
                moveTo(OrderStatus.PAID);
                events.add(new OrderEvent.OrderPaid(pay.paymentId()));
            }
            case Command.ShipOrder ship -> {
                moveTo(OrderStatus.SHIPPED);
                events.add(new OrderEvent.OrderShipped("S-" + ship.orderId(), ship.carrier()));
            }
            case Command.CancelOrder cancel -> {
                moveTo(OrderStatus.CANCELLED);
                events.add(new OrderEvent.OrderCancelled(cancel.reason()));
            }
        }
        Instant now = Instant.now();
        events.forEach(event -> apply(event, now));
        return events;
    }

    private void moveTo(OrderStatus next) {
        if (status == null) {
            throw new CommandRefused("no such order");
        }
        if (!status.canMoveTo(next)) {
            throw new CommandRefused(status + " to " + next);
        }
    }
}
