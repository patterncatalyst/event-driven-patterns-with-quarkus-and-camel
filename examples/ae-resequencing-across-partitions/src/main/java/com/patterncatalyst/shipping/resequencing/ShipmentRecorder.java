package com.patterncatalyst.shipping.resequencing;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import jakarta.transaction.Transactional;

/** Applies a shipment event: one row, in the order the resequencer released it. */
@ApplicationScoped
@Named("shipmentRecorder")
public class ShipmentRecorder {

    @Transactional
    public void record(ShipmentEvent event) {
        var row = new AppliedShipment();
        row.orderId = event.orderId();
        row.eventType = event.type();
        row.sequence = event.sequence();
        row.globalSequence = event.globalSequence();
        row.persist();
    }
}
