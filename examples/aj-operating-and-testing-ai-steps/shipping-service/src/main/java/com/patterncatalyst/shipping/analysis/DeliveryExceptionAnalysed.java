package com.patterncatalyst.shipping.analysis;

import java.time.Instant;

import com.patterncatalyst.shipping.Carrier;

/**
 * The recorded result of stage one: the reason with the model and the prompt that produced it. {@code
 * derivedFromPromptVersion} and {@code derivedFromModelDigest} are null on a fresh model call; {@link
 * MemoizedAnalysisService} sets them to this record's own promptVersion and modelDigest when the event was served
 * from an already-committed row instead of a new call, so a consumer can tell a replay of an earlier answer from a
 * new one without reading the database itself.
 */
public record DeliveryExceptionAnalysed(String exceptionId, String orderId, String shipmentId, Carrier carrier, int attempt,
        Reason reason, String evidence, String model, String modelDigest, String promptVersion, Instant occurredAt,
        String derivedFromPromptVersion, String derivedFromModelDigest) {
}
