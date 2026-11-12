package com.patterncatalyst.shipping.aggregate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.patterncatalyst.shipping.OrderStatus;

/**
 * The root of the order aggregate. It owns every rule about an order: it is rebuilt from the stream, decides a
 * command against its current state and returns the events that follow, or refuses.
 */
public final class OrderAggregate {

    private int version;
    private OrderStatus status;
    private BigDecimal amount;
    private String paymentId;

    private OrderAggregate() {
    }

    public static OrderAggregate rehydrate(List<StoredEvent> stream) {
        OrderAggregate order = new OrderAggregate();
        for (StoredEvent stored : stream) {
            order.apply(stored.payload);
            order.version = stored.version;
        }
        return order;
    }

    public int version() {
        return version;
    }

    public OrderStatus status() {
        return status;
    }

    /** The events this command produces against the current state; the aggregate applies them to itself. */
    public List<OrderEvent> decide(Command command) {
        List<OrderEvent> events = new ArrayList<>();
        switch (command) {
            case Command.Place place -> {
                if (status != null) {
                    throw new CommandRefused("order exists");
                }
                events.add(new OrderEvent.OrderPlaced(place.orderId(), place.customerId(), place.itemSku(),
                        place.quantity(), place.amount()));
            }
            case Command.MarkPaid pay -> {
                if (pay.paymentId().equals(paymentId)) {
                    return events; // the payment is already recorded: a redelivery changes nothing
                }
                if (paymentId != null) {
                    throw new CommandRefused("already paid by " + paymentId);
                }
                moveTo(OrderStatus.PAID);
                if (pay.amount().compareTo(amount) != 0) {
                    throw new CommandRefused("amount " + pay.amount() + " differs from order amount " + amount);
                }
                events.add(new OrderEvent.OrderPaid(pay.paymentId(), pay.amount()));
            }
            case Command.MarkShipped ship -> {
                moveTo(OrderStatus.SHIPPED);
                events.add(new OrderEvent.OrderShipped(ship.shipmentId(), ship.carrier()));
            }
            case Command.Cancel cancel -> {
                moveTo(OrderStatus.CANCELLED);
                events.add(new OrderEvent.OrderCancelled(cancel.reason()));
            }
        }
        events.forEach(this::apply);
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

    private void apply(OrderEvent event) {
        switch (event) {
            case OrderEvent.OrderPlaced placed -> {
                status = OrderStatus.PLACED;
                amount = placed.amount();
            }
            case OrderEvent.OrderPaid paid -> {
                status = OrderStatus.PAID;
                paymentId = paid.paymentId();
            }
            case OrderEvent.OrderShipped shipped -> status = OrderStatus.SHIPPED;
            case OrderEvent.OrderCancelled cancelled -> status = OrderStatus.CANCELLED;
        }
    }
}
