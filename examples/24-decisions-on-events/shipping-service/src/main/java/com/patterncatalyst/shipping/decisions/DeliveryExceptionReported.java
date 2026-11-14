package com.patterncatalyst.shipping.decisions;

import java.time.Instant;

import com.patterncatalyst.shipping.Shipment;

/**
 * A carrier's note about a parcel it could not deliver. The attempt number travels with the note, so a decision is a
 * function of the recorded analysis alone.
 */
public record DeliveryExceptionReported(String exceptionId, Shipment shipment, int attempt, String note, Instant occurredAt) {
}
