package com.patterncatalyst.shipping.replay;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.jpa.HibernateHints;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.PersistenceException;

/**
 * The event store of chapter 14: read a stream in version order, append after an expected version, and one pass over
 * every stream for a replay. The unique constraint on (stream_id, version) is the concurrency check.
 */
@ApplicationScoped
public class EventStore implements PanacheRepositoryBase<StoredEvent, Long> {

    private static final String VERSION_CONSTRAINT = "events_stream_version";

    /** One event of a replay: the stream it belongs to, what happened and when. */
    public record Row(String streamId, int version, OrderEvent payload, Instant occurredAt) {
    }

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

    /**
     * Every event, stream by stream in version order, so one aggregate is folded at a time. A projection to a record
     * leaves nothing in the persistence context, and the fetch size keeps the driver from loading the whole result.
     * The caller closes the stream inside its transaction.
     */
    public Stream<Row> replayStream() {
        return find("select e.streamId, e.version, e.payload, e.occurredAt from StoredEvent e order by e.streamId, e.version")
                .project(Row.class).withHint(HibernateHints.HINT_FETCH_SIZE, 1000).stream();
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
