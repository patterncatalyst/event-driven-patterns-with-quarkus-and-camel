package com.patterncatalyst.shipping.crudvsevents;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.exception.ConstraintViolationException;

import com.patterncatalyst.shipping.Consignment;
import com.patterncatalyst.shipping.ConsignmentStatus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/** One command, two storage outcomes: update the row in place, append an event to the stream. */
@ApplicationScoped
public class ConsignmentService {

    static final String BOOKED = "ConsignmentBooked";
    static final String STATUS_CHANGED = "ConsignmentStatusChanged";

    @Transactional
    public void book(Consignment consignment) {
        ConsignmentRow row = new ConsignmentRow();
        row.id = consignment.id();
        row.origin = consignment.origin();
        row.destination = consignment.destination();
        row.weightKg = consignment.weightKg();
        row.carrier = consignment.carrier();
        row.status = ConsignmentStatus.BOOKED;
        row.persist();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", consignment.id());
        payload.put("origin", consignment.origin());
        payload.put("destination", consignment.destination());
        payload.put("weightKg", consignment.weightKg().toPlainString());
        payload.put("carrier", consignment.carrier());
        append(consignment.id(), 0, BOOKED, payload);
    }

    /** Applies the command to both models in one transaction. */
    @Transactional
    public void change(ChangeConsignmentStatus command) {
        updateRow(command);
        appendEvent(command);
    }

    /** CRUD: the previous status is overwritten. */
    void updateRow(ChangeConsignmentStatus command) {
        ConsignmentRow row = ConsignmentRow.findById(command.consignmentId());
        if (row == null) {
            throw new ConsignmentNotFoundException(command.consignmentId());
        }
        requireAllowed(row.status, command.to());
        row.status = command.to();
    }

    /** Event sourcing: the transition is checked against the replayed state, then a fact is appended. */
    void appendEvent(ChangeConsignmentStatus command) {
        List<ConsignmentEvent> stream = stream(command.consignmentId());
        ConsignmentDocument current = ConsignmentDocument.replay(stream);
        if (current == null) {
            throw new ConsignmentNotFoundException(command.consignmentId());
        }
        requireAllowed(current.status(), command.to());
        int expected = command.expectedVersion() != null ? command.expectedVersion() : stream.size();
        append(command.consignmentId(), expected, STATUS_CHANGED,
                Map.of("from", current.status().name(), "to", command.to().name()));
    }

    /** Inserts version expectedVersion + 1; the unique (stream_id, version) constraint rejects a stale writer. */
    void append(String streamId, int expectedVersion, String type, Map<String, Object> payload) {
        ConsignmentEvent event = new ConsignmentEvent();
        event.streamId = streamId;
        event.version = expectedVersion + 1;
        event.type = type;
        event.payload = payload;
        event.occurredAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        try {
            event.persistAndFlush();
        } catch (ConstraintViolationException e) {
            throw new ConcurrentAppendException(
                    "stream " + streamId + " already has version " + event.version, e);
        }
    }

    public ConsignmentDocument fromRow(String id) {
        ConsignmentRow row = ConsignmentRow.findById(id);
        if (row == null) {
            throw new ConsignmentNotFoundException(id);
        }
        return ConsignmentDocument.of(row);
    }

    public ConsignmentDocument fromEvents(String id) {
        ConsignmentDocument document = ConsignmentDocument.replay(stream(id));
        if (document == null) {
            throw new ConsignmentNotFoundException(id);
        }
        return document;
    }

    public StatusHistory history(String id) {
        List<ConsignmentEvent> stream = stream(id);
        if (stream.isEmpty()) {
            throw new ConsignmentNotFoundException(id);
        }
        List<StatusHistory.Entry> entries = stream.stream()
                .map(e -> new StatusHistory.Entry(e.version, statusOf(e), e.occurredAt))
                .toList();
        return new StatusHistory(id, entries);
    }

    private static ConsignmentStatus statusOf(ConsignmentEvent event) {
        return BOOKED.equals(event.type)
                ? ConsignmentStatus.BOOKED
                : ConsignmentStatus.valueOf((String) event.payload.get("to"));
    }

    private static List<ConsignmentEvent> stream(String id) {
        return ConsignmentEvent.list("streamId = ?1 order by version", id);
    }

    private static void requireAllowed(ConsignmentStatus from, ConsignmentStatus to) {
        if (!Lifecycle.allows(from, to)) {
            throw new IllegalTransitionException(from + " to " + to + " is not allowed");
        }
    }
}
