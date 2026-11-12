package com.patterncatalyst.shipping.snapshots;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.hibernate.exception.ConstraintViolationException;
import org.jboss.logging.Logger;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;

/** Loads an InventoryItemAggregate from its latest usable snapshot plus the events after it, and appends events. */
@ApplicationScoped
public class StockLedger {

    private static final Logger LOG = Logger.getLogger(StockLedger.class);
    private static final String TAIL = "streamId = ?1 and version > ?2 order by version";
    private static final int FETCH_SIZE = 500;

    /** What a load cost: which snapshot it started from, how many events it replayed and how long it took. */
    public record LoadStats(String source, int snapshotVersion, long eventsReplayed, long loadNanos) {
    }

    public record Loaded(InventoryItemAggregate aggregate, LoadStats stats) {
    }

    /** The projection: Hibernate returns payloads only, so no replayed event stays in the persistence context. */
    public record Payload(StockEvent payload) {
    }

    @Inject
    SnapshotConfig config;

    @Inject
    MeterRegistry meters;

    /** Reads never write: a snapshot is only ever written by a command or by the snapshotter. */
    @Transactional
    public Loaded load(String sku, boolean useSnapshot) {
        long started = System.nanoTime();
        SnapshotRow snapshot = useSnapshot ? SnapshotRow.findById(sku) : null;
        if (snapshot != null && snapshot.schemaVersion != config.snapshot().schema()) {
            meters.counter("inventory.snapshot.ignored").increment();
            LOG.infof("snapshot schema %d ignored for %s", snapshot.schemaVersion, sku);
            snapshot = null;
        }
        int from = snapshot == null ? 0 : snapshot.version;
        long[] replayed = {0};
        InventoryItemAggregate aggregate;
        try (Stream<StockEvent> tail = tail(sku, from).peek(event -> replayed[0]++)) {
            aggregate = snapshot == null
                    ? InventoryItemAggregate.replay(sku, tail)
                    : InventoryItemAggregate.from(snapshot.state, snapshot.version, tail);
        }
        long nanos = System.nanoTime() - started;
        String source = snapshot == null ? "full" : "snapshot";
        Timer.builder("inventory.rehydrate").tag("source", source).register(meters)
                .record(nanos, TimeUnit.NANOSECONDS);
        return new Loaded(aggregate, new LoadStats(source, from, replayed[0], nanos));
    }

    private Stream<StockEvent> tail(String sku, int afterVersion) {
        return switch (config.demo().fetch()) {
            case projection -> StoredStockEvent.find(TAIL, sku, afterVersion).project(Payload.class)
                    .withHint("org.hibernate.fetchSize", FETCH_SIZE).stream().map(Payload::payload);
            // Demo only: managed entities pile up in the persistence context while the stream is read.
            case entities -> StoredStockEvent.<StoredStockEvent>find(TAIL, sku, afterVersion)
                    .withHint("org.hibernate.fetchSize", FETCH_SIZE).stream().map(row -> row.payload);
        };
    }

    /** Appends one event at expectedVersion + 1. The unique (stream_id, version) constraint decides a race. */
    public void append(String sku, int expectedVersion, StockEvent event) {
        var row = new StoredStockEvent();
        row.eventId = UUID.randomUUID();
        row.streamType = "InventoryItem";
        row.streamId = sku;
        row.version = expectedVersion + 1;
        row.type = event.getClass().getSimpleName();
        row.payload = event;
        row.occurredAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        try {
            row.persistAndFlush();
        } catch (PersistenceException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && "events_stream_version".equals(violation.getConstraintName())) {
                    meters.counter("inventory.command.conflicts").increment();
                    throw new ConcurrencyConflict(sku + " moved past version " + expectedVersion, failure);
                }
            }
            throw failure;
        }
    }
}
