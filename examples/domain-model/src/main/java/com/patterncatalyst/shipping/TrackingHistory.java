package com.patterncatalyst.shipping;

import java.util.List;

/** The handling events recorded for one consignment. */
public record TrackingHistory(String consignmentId, List<HandlingEvent> events) {

    public TrackingHistory {
        Checks.text(consignmentId, "consignmentId");
        events = Checks.list(events, "events");
        for (HandlingEvent event : events) {
            if (!consignmentId.equals(event.consignmentId())) {
                throw new IllegalArgumentException("event " + event.id() + " belongs to another consignment");
            }
        }
    }
}
