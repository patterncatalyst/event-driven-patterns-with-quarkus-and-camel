package com.patterncatalyst.shipping.ordering;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.transaction.Transactional;

/**
 * Resequencer: stores each event, then applies the order's buffered events from its next expected
 * sequence number onward. The lock on the order row serialises the replicas per order.
 */
@ApplicationScoped
@Named("shipmentBuffer")
public class ShipmentBuffer {

    @Inject
    ShipmentApplier applier;
    @Inject
    ObjectMapper json;

    @Transactional
    public void accept(String payload) throws JsonProcessingException {
        ShipmentEvent event = json.readValue(payload, ShipmentEvent.class);
        OrderStatusRow order = applier.lockOrCreate(event.orderId());

        boolean alreadyApplied = event.sequence() < order.nextSequence;
        boolean alreadyHeld = PendingShipmentEvent.count("orderId = ?1 and sequence = ?2",
                event.orderId(), event.sequence()) > 0;
        if (alreadyApplied || alreadyHeld) {
            return;
        }
        var held = new PendingShipmentEvent();
        held.orderId = event.orderId();
        held.sequence = event.sequence();
        held.payload = payload;
        held.persist();

        PendingShipmentEvent next;
        while ((next = PendingShipmentEvent.<PendingShipmentEvent>find("orderId = ?1 and sequence = ?2",
                order.orderId, order.nextSequence).firstResult()) != null) {
            applier.record(order, json.readValue(next.payload, ShipmentEvent.class));
            next.delete();
            order.nextSequence++;
        }
    }
}
