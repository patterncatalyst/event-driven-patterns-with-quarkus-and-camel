package com.patterncatalyst.shipping.crudvsevents;

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
            switch (event.payload) {
                case ConsignmentEventPayload.ConsignmentBooked booked -> {
                    consignment = new Consignment(booked.id(), booked.origin(), booked.destination(),
                            booked.weightKg().setScale(WEIGHT_SCALE), booked.carrier());
                    status = ConsignmentStatus.BOOKED;
                }
                case ConsignmentEventPayload.ConsignmentStatusChanged changed -> status = changed.to();
            }
        }
        return consignment == null ? null : new ConsignmentDocument(consignment, status);
    }
}
