package com.patterncatalyst.shipping.inbox;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ReservationService {

    @Inject
    EntityManager em;

    @Inject
    DemoFailure demoFailure;

    /**
     * Applies the event once. The inbox row and the decrement are one transaction: a second delivery of the
     * same event id inserts nothing and returns false, and a failed decrement leaves no inbox row.
     */
    @Transactional
    public boolean reserveOnce(UUID eventId, OrderPlaced event) {
        int inserted = em.createNativeQuery(
                "INSERT INTO inventory.inbox (event_id) VALUES (:id) ON CONFLICT (event_id) DO NOTHING")
                .setParameter("id", eventId)
                .executeUpdate();
        if (inserted == 0) {
            return false;
        }
        reserve(event);
        return true;
    }

    /** Decrements the stock and nothing else: the effect on its own. */
    @Transactional
    public void reserve(OrderPlaced event) {
        long updated = InventoryItemEntity.update("quantityOnHand = quantityOnHand - ?1 where sku = ?2",
                event.quantity(), event.itemSku());
        if (updated == 0) {
            throw new IllegalArgumentException("unknown sku " + event.itemSku());
        }
        demoFailure.afterEffect(event.orderId());
    }
}
