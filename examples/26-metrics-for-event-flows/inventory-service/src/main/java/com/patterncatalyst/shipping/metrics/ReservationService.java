package com.patterncatalyst.shipping.metrics;

import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/** Takes the ordered quantity out of stock. An unknown SKU or too little stock fails, and the record goes to the dead letter topic. */
@ApplicationScoped
public class ReservationService {

    private static final Logger LOG = Logger.getLogger(ReservationService.class);

    @Transactional
    public void reserve(OrderPlaced order) {
        LOG.infof("reserving %s: %d of %s", order.orderId(), order.quantity(), order.itemSku());
        InventoryItemEntity item = InventoryItemEntity.findById(order.itemSku());
        if (item == null || item.quantityOnHand < order.quantity()) {
            throw new IllegalArgumentException("cannot reserve " + order.quantity() + " of " + order.itemSku());
        }
        item.quantityOnHand -= order.quantity();
    }
}
