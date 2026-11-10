package com.patterncatalyst.shipping;

import java.time.Instant;
import java.util.Objects;

/** Something that happened to a consignment at a location, such as LOAD or DISCHARGE. */
public record HandlingEvent(String id, String consignmentId, String type, String location, Instant occurredAt) {

    public HandlingEvent {
        Checks.text(id, "id");
        Checks.text(consignmentId, "consignmentId");
        Checks.text(type, "type");
        Checks.text(location, "location");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
