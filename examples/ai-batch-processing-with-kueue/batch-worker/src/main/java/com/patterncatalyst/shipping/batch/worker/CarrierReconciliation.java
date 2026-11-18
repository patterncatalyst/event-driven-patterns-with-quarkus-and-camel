package com.patterncatalyst.shipping.batch.worker;

import java.time.Instant;

/** The record published per carrier when a run is aggregated; the key is runId:carrier. */
public record CarrierReconciliation(String runId, String carrier, long scheduled, long delivered, long open,
        Instant windowFrom, Instant windowTo) {
}
