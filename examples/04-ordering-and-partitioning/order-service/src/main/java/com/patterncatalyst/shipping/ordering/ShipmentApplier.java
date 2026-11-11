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

    @Transactional
    public void apply(ShipmentEvent event) {
        OrderStatus status = statusFor(event.type());
        OrderStatusRow row = OrderStatusRow.findById(event.orderId(), LockModeType.PESSIMISTIC_WRITE);
        boolean created = row == null;
        if (created) {
            row = new OrderStatusRow();
            row.orderId = event.orderId();
        }
        row.status = status;
        row.lastEvent = event.type();
        if (created) {
            row.persist();
        }

        var history = new StatusHistoryRow();
        history.orderId = event.orderId();
        history.eventType = event.type();
        history.sequence = event.sequence();
        history.status = status;
        history.replica = replica;
        history.persist();
    }
}
