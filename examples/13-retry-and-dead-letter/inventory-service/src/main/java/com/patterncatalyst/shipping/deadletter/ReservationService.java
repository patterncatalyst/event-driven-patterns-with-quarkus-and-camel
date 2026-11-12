package com.patterncatalyst.shipping.deadletter;

import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

/** Takes the ordered quantity out of stock. One call is one transaction, so a retry starts a new one. */
@ApplicationScoped
public class ReservationService {

    private static final Logger LOG = Logger.getLogger(ReservationService.class);

    @Transactional
    public void reserve(OrderPlaced order) {
        LOG.infof("reserving %s: %d of %s", order.orderId(), order.quantity(), order.itemSku());
        if (order.quantity() <= 0) {
            throw new IllegalArgumentException("quantity must be positive: " + order.quantity());
        }
        // Waits at most 2 s for a row another transaction holds, then fails with a PessimisticLockException.
        InventoryItemEntity item = InventoryItemEntity.<InventoryItemEntity>find("sku", order.itemSku())
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .withHint("jakarta.persistence.lock.timeout", 2000)
                .firstResult();
        if (item == null || item.quantityOnHand < order.quantity()) {
            throw new IllegalArgumentException("cannot reserve " + order.quantity() + " of " + order.itemSku());
        }
        item.quantityOnHand -= order.quantity();
    }
}
