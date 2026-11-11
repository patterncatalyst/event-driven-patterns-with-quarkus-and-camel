package com.patterncatalyst.shipping.ordering;

import com.patterncatalyst.shipping.Carrier;

/**
 * One shipment status change. {@code sequence} numbers the events of one order (1, 2, 3);
 * {@code streamSequence} numbers every event the service publishes, across orders.
 */
public record ShipmentEvent(String orderId, String type, int sequence, long streamSequence, Carrier carrier) {
}
