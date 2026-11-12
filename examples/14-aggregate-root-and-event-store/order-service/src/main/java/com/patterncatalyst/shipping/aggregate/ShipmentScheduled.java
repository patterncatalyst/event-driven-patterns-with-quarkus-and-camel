package com.patterncatalyst.shipping.aggregate;

import com.patterncatalyst.shipping.Shipment;

/** The payload of a record on edp.shipping.scheduled. */
public record ShipmentScheduled(Shipment shipment) {
}
