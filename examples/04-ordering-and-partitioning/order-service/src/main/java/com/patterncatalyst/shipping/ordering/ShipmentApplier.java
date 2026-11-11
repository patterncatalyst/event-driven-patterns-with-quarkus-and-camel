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

    /**
     * The order's row, locked for the rest of the transaction. The row is created first with an
     * insert that does nothing when it already exists, so two replicas handling the order's first
     * event cannot both insert, and the lock then serialises them on a row that is certain to exist.
     */
    OrderStatusRow lockOrCreate(String orderId) {
        OrderStatusRow.getEntityManager().createNativeQuery("""
                INSERT INTO orders.order_status (order_id, status, last_event, next_sequence)
                VALUES (:id, :status, '', 1)
                ON CONFLICT (order_id) DO NOTHING
                """)
                .setParameter("id", orderId)
                .setParameter("status", OrderStatus.PAID.name())
                .executeUpdate();
        return OrderStatusRow.findById(orderId, LockModeType.PESSIMISTIC_WRITE);
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
