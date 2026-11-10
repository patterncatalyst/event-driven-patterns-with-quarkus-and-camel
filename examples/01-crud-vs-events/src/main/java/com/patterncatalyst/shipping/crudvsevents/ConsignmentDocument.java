package com.patterncatalyst.shipping.crudvsevents;

import java.math.BigDecimal;

import com.patterncatalyst.shipping.Consignment;
import com.patterncatalyst.shipping.ConsignmentStatus;

/** The Document Message a reader receives: the Consignment and where it stands now. */
public record ConsignmentDocument(Consignment consignment, ConsignmentStatus status) {

    /** weight_kg is numeric(12, 3). */
    private static final int WEIGHT_SCALE = 3;

    static ConsignmentDocument of(ConsignmentRow row) {
        return new ConsignmentDocument(
                new Consignment(row.id, row.origin, row.destination, row.weightKg.setScale(WEIGHT_SCALE), row.carrier), row.status);
    }

    /** Rebuilds the document by folding a stream from the first event to the last. */
    static ConsignmentDocument replay(Iterable<ConsignmentEvent> stream) {
        Consignment consignment = null;
        ConsignmentStatus status = null;
        for (ConsignmentEvent event : stream) {
            switch (event.type) {
                case ConsignmentService.BOOKED -> {
                    consignment = new Consignment(
                            (String) event.payload.get("id"),
                            (String) event.payload.get("origin"),
                            (String) event.payload.get("destination"),
                            new BigDecimal((String) event.payload.get("weightKg")).setScale(WEIGHT_SCALE),
                            (String) event.payload.get("carrier"));
                    status = ConsignmentStatus.BOOKED;
                }
                case ConsignmentService.STATUS_CHANGED ->
                    status = ConsignmentStatus.valueOf((String) event.payload.get("to"));
                default -> throw new IllegalStateException("unknown event type " + event.type);
            }
        }
        return consignment == null ? null : new ConsignmentDocument(consignment, status);
    }
}
