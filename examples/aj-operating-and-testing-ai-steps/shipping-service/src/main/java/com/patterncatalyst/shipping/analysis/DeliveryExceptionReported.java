package com.patterncatalyst.shipping.analysis;

import java.time.Instant;

import com.patterncatalyst.shipping.Shipment;

/**
 * A carrier's note about a parcel it could not deliver, reused unchanged from chapter 24. The attempt number
 * travels with the note, so an analysis is a function of the reported event alone.
 */
public record DeliveryExceptionReported(String exceptionId, Shipment shipment, int attempt, String note, Instant occurredAt) {
}
