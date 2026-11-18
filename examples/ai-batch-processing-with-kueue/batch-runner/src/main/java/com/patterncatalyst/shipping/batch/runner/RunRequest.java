package com.patterncatalyst.shipping.batch.runner;

import java.time.Instant;

/** The body of POST /runs. */
public record RunRequest(Instant from, Instant to, String priority) {
}
