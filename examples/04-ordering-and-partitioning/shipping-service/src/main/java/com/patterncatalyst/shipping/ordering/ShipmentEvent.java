package com.patterncatalyst.shipping.ordering;

import com.patterncatalyst.shipping.Carrier;

/** One shipment status change. {@code sequence} numbers the events of one order (1, 2, 3). */
public record ShipmentEvent(String orderId, String type, int sequence, Carrier carrier) {
}
