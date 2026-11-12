package com.patterncatalyst.shipping.aggregate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.PersistenceException;

/**
 * The two operations an event store needs: read a stream in version order, and append events after an expected
 * version. The unique constraint on (stream_id, version) is the concurrency check.
 */
@ApplicationScoped
public class EventStore implements PanacheRepositoryBase<StoredEvent, Long> {

    private static final String VERSION_CONSTRAINT = "events_stream_version";

    public List<StoredEvent> load(String streamId) {
        return list("streamId = ?1 order by version", streamId);
    }

    /** Appends the events as versions expectedVersion + 1 onwards, or throws {@link ConcurrencyConflict}. */
    public void append(String streamType, String streamId, int expectedVersion, List<OrderEvent> events) {
        int version = expectedVersion;
        for (OrderEvent event : events) {
            StoredEvent row = new StoredEvent();
            row.eventId = UUID.randomUUID();
            row.streamType = streamType;
            row.streamId = streamId;
            row.version = ++version;
            row.type = event.type();
            row.payload = event;
            row.occurredAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
            try {
                persistAndFlush(row);
            } catch (PersistenceException e) {
                if (violatesVersionConstraint(e)) {
                    throw new ConcurrencyConflict(streamId, expectedVersion, e);
                }
                throw e;
            }
        }
    }

    private static boolean violatesVersionConstraint(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && VERSION_CONSTRAINT.equals(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
