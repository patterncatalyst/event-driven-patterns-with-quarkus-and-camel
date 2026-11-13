package com.patterncatalyst.shipping.projections;

import java.time.Instant;

import com.patterncatalyst.shipping.Shipment;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Applies the six events to {@code orders.order_view}. Every method is one upsert that sets only the columns its event
 * owns, so the events of different owners can arrive in any order and the same event can arrive twice. Hibernate has
 * no upsert, so the statements are native SQL through the EntityManager.
 */
@ApplicationScoped
public class OrderViewProjector {

    private static final String SHIPMENT_FORWARD_ONLY =
            " WHERE orders.shipment_rank(EXCLUDED.shipment_status) > orders.shipment_rank(order_view.shipment_status)";

    @Inject
    EntityManager em;

    @Inject
    DemoSwitches demo;

    @Transactional
    public void placed(Events.OrderPlaced event, Instant at) {
        demo.delay();
        var order = event.order();
        em.createNativeQuery("""
                INSERT INTO orders.order_view (order_id, customer_id, item_sku, amount, order_status, last_event_at)
                VALUES (:id, :customer, :sku, :amount, 'PLACED', :at)
                ON CONFLICT (order_id) DO UPDATE SET
                    customer_id = EXCLUDED.customer_id, item_sku = EXCLUDED.item_sku, amount = EXCLUDED.amount,
                    order_status = CASE WHEN order_view.order_status = 'CANCELLED' THEN 'CANCELLED' ELSE 'PLACED' END,
                    last_event_at = GREATEST(order_view.last_event_at, EXCLUDED.last_event_at), updated_at = now()
                """)
                .setParameter("id", order.id()).setParameter("customer", order.customerId())
                .setParameter("sku", order.itemSku()).setParameter("amount", order.amount())
                .setParameter("at", at).executeUpdate();
    }

    @Transactional
    public void cancelled(Events.OrderCancelled event, Instant at) {
        demo.delay();
        em.createNativeQuery("""
                INSERT INTO orders.order_view (order_id, order_status, last_event_at)
                VALUES (:id, 'CANCELLED', :at)
                ON CONFLICT (order_id) DO UPDATE SET
                    order_status = 'CANCELLED',
                    last_event_at = GREATEST(order_view.last_event_at, EXCLUDED.last_event_at), updated_at = now()
                """)
                .setParameter("id", event.orderId()).setParameter("at", at).executeUpdate();
    }

    @Transactional
    public void paid(Events.PaymentProcessed event, Instant at) {
        demo.delay();
        var payment = event.payment();
        em.createNativeQuery("""
                INSERT INTO orders.order_view (order_id, payment_status, last_event_at)
                VALUES (:id, :status, :at)
                ON CONFLICT (order_id) DO UPDATE SET
                    payment_status = EXCLUDED.payment_status,
                    last_event_at = GREATEST(order_view.last_event_at, EXCLUDED.last_event_at), updated_at = now()
                """)
                .setParameter("id", payment.orderId()).setParameter("status", payment.status().name())
                .setParameter("at", at).executeUpdate();
    }

    @Transactional
    public void scheduled(Events.ShipmentScheduled event, Instant at) {
        shipment(event.shipment(), at);
    }

    @Transactional
    public void inTransit(Events.ShipmentInTransit event, Instant at) {
        shipment(event.shipment(), at);
    }

    @Transactional
    public void delivered(Events.ShipmentDelivered event, Instant at) {
        shipment(event.shipment(), at);
    }

    /** The shipment topics are separate, so a later status can arrive first: the row only moves forward. */
    private void shipment(Shipment shipment, Instant at) {
        demo.delay();
        em.createNativeQuery("""
                INSERT INTO orders.order_view (order_id, shipment_status, carrier, tracking_number, last_event_at)
                VALUES (:id, :status, :carrier, :tracking, :at)
                ON CONFLICT (order_id) DO UPDATE SET
                    shipment_status = EXCLUDED.shipment_status, carrier = EXCLUDED.carrier,
                    tracking_number = EXCLUDED.tracking_number,
                    last_event_at = GREATEST(order_view.last_event_at, EXCLUDED.last_event_at), updated_at = now()
                """ + (demo.guardOn() ? SHIPMENT_FORWARD_ONLY : ""))
                .setParameter("id", shipment.orderId()).setParameter("status", shipment.status().name())
                .setParameter("carrier", shipment.carrier().name()).setParameter("tracking", shipment.trackingNumber())
                .setParameter("at", at).executeUpdate();
    }
}
