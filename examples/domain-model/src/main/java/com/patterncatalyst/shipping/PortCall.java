package com.patterncatalyst.shipping;

import java.time.Instant;
import java.util.Objects;

/** A scheduled stop at a port. */
public record PortCall(String port, Instant arrival, Instant departure) {

    public PortCall {
        Checks.text(port, "port");
        Objects.requireNonNull(arrival, "arrival");
        Objects.requireNonNull(departure, "departure");
        if (departure.isBefore(arrival)) {
            throw new IllegalArgumentException("departure must not be before arrival");
        }
    }
}
