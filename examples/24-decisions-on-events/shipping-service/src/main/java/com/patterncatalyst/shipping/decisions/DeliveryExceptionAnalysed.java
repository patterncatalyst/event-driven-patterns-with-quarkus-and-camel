package com.patterncatalyst.shipping.decisions;

import java.time.Instant;

import com.patterncatalyst.shipping.Carrier;

/** The recorded stage-one result: the reason with the model and the prompt that produced it. */
public record DeliveryExceptionAnalysed(String exceptionId, String orderId, String shipmentId, Carrier carrier, int attempt,
        Reason reason, String evidence, String model, String modelDigest, String promptVersion, Instant occurredAt) {
}
