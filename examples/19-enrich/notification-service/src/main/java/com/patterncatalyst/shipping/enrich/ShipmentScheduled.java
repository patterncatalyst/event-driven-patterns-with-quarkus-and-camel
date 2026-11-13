package com.patterncatalyst.shipping.enrich;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.patterncatalyst.shipping.Shipment;

/** The event shipping-service publishes when a shipment is booked: the carrier and the tracking number, nothing more. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShipmentScheduled(Shipment shipment) {
}
