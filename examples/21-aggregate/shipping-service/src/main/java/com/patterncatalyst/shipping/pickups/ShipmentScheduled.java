package com.patterncatalyst.shipping.pickups;

import com.patterncatalyst.shipping.Shipment;
import java.time.Instant;
import java.util.Objects;

/** The event on {@code edp.shipping.scheduled}: a shipment was booked with its carrier at {@code occurredAt}. */
public record ShipmentScheduled(Shipment shipment, Instant occurredAt) implements HasEventTime {

    public ShipmentScheduled {
        Objects.requireNonNull(shipment, "shipment");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
