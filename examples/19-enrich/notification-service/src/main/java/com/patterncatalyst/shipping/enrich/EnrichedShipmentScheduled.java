package com.patterncatalyst.shipping.enrich;

import com.patterncatalyst.shipping.Shipment;

/** The shipment with the carrier's mode and the customer's tracking link. Both are null when no reference was found. */
public record EnrichedShipmentScheduled(Shipment shipment, String carrierMode, String trackingUrl) {
}
