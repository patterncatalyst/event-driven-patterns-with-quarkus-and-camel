package com.patterncatalyst.shipping.deliverycounts;

import java.time.Instant;

/** One answer of the query: the deliveries of a carrier in the window that starts at {@code windowStart}. */
public record WindowCount(String carrier, Instant windowStart, long count) {
}
