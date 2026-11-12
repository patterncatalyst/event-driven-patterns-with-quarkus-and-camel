package com.patterncatalyst.shipping.snapshots;

import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/** Writes the latest snapshot of a stream with one upsert that never lowers the stored version. */
@ApplicationScoped
public class SnapshotStore {

    private static final String UPSERT = """
            INSERT INTO inventory.snapshots (stream_id, version, schema_version, state, taken_at, taken_by)
            VALUES (?1, ?2, ?3, cast(?4 as jsonb), now(), ?5)
            ON CONFLICT (stream_id) DO UPDATE SET
                version = excluded.version, schema_version = excluded.schema_version,
                state = excluded.state, taken_at = excluded.taken_at, taken_by = excluded.taken_by
            """;
    private static final String GUARD = " WHERE excluded.version > inventory.snapshots.version";

    @Inject
    EntityManager em;

    @Inject
    ObjectMapper json;

    @Inject
    MeterRegistry meters;

    @Inject
    SnapshotConfig config;

    /** Returns the rows written: 0 when a newer snapshot is already stored. */
    public int upsert(InventoryItemAggregate aggregate, String writer) {
        long started = System.nanoTime();
        try {
            String sql = config.snapshot().guard() ? UPSERT + GUARD : UPSERT;
            return em.createNativeQuery(sql)
                    .setParameter(1, aggregate.sku())
                    .setParameter(2, aggregate.version())
                    .setParameter(3, config.snapshot().schema())
                    .setParameter(4, json.writeValueAsString(aggregate.snapshot()))
                    .setParameter(5, writer)
                    .executeUpdate();
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException(failure);
        } finally {
            Timer.builder("inventory.snapshot.write").tag("writer", writer).register(meters)
                    .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
        }
    }
}
