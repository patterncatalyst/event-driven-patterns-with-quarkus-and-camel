package com.patterncatalyst.shipping.crudvsevents;

import java.time.Instant;
import java.util.List;

import com.patterncatalyst.shipping.ConsignmentStatus;

/** Every status a Consignment held and when it entered it. Only the event stream can answer this. */
public record StatusHistory(String consignmentId, List<Entry> entries) {

    public record Entry(int version, ConsignmentStatus status, Instant occurredAt) {
    }
}
