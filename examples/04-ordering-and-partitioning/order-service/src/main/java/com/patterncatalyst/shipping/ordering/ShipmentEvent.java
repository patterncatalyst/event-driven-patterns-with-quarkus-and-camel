package com.patterncatalyst.shipping.ordering;

import com.patterncatalyst.shipping.Carrier;

/**
 * One shipment status change, as the shipping service publishes it. {@code sequence} numbers the events
 * of one order (1, 2, 3); {@code streamSequence} numbers every event the producer publishes.
 */
public record ShipmentEvent(String orderId, String type, int sequence, long streamSequence, Carrier carrier) {
}
