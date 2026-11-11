package com.patterncatalyst.shipping.ordering;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.patterncatalyst.shipping.OrderStatus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

/** Applies a shipment event to the order: the status mapping, last write wins, and one history row. */
@ApplicationScoped
@Named("shipmentApplier")
public class ShipmentApplier {

    @ConfigProperty(name = "order.replica")
    String replica;

    static OrderStatus statusFor(String eventType) {
        return switch (eventType) {
            case "ShipmentScheduled", "ShipmentInTransit" -> OrderStatus.SHIPPED;
            case "ShipmentDelivered" -> OrderStatus.DELIVERED;
            default -> throw new IllegalArgumentException("unknown shipment event " + eventType);
        };
    }

    /** Applies the event as it arrives. */
    @Transactional
    public void apply(ShipmentEvent event) {
        record(lockOrCreate(event.orderId()), event);
    }

    /** The order's row, locked for the rest of the transaction; created if this is the order's first event. */
    OrderStatusRow lockOrCreate(String orderId) {
        OrderStatusRow row = OrderStatusRow.findById(orderId, LockModeType.PESSIMISTIC_WRITE);
        if (row == null) {
            row = new OrderStatusRow();
            row.orderId = orderId;
            row.status = OrderStatus.PLACED;
            row.lastEvent = "";
            row.persist();
        }
        return row;
    }

    void record(OrderStatusRow row, ShipmentEvent event) {
        OrderStatus status = statusFor(event.type());
        row.status = status;
        row.lastEvent = event.type();

        var history = new StatusHistoryRow();
        history.orderId = event.orderId();
        history.eventType = event.type();
        history.sequence = event.sequence();
        history.status = status;
        history.replica = replica;
        history.persist();
    }
}
