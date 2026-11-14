package com.patterncatalyst.shipping.decisions;

import java.time.Instant;

/**
 * The recorded stage-two result: the decision with the rule that made it. {@code occurredAt} is the analysis's, so
 * deciding the same analysis again gives the same record.
 */
public record DeliveryExceptionDecided(String exceptionId, String orderId, Resolution decision, String rule,
        String rulesVersion, Instant occurredAt) {
}
