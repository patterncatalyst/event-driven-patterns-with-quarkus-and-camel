package com.patterncatalyst.shipping.snapshots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/** The snapshot policy against PostgreSQL from Dev Services, with the default of a snapshot every 100 events. */
@QuarkusTest
class SnapshotPolicyTest {

    @Inject
    ReservationCommands commands;

    @Inject
    StockLedger ledger;

    @Inject
    SnapshotStore store;

    @Inject
    EntityManager em;

    private SnapshotRow snapshot(String sku) {
        return QuarkusTransaction.requiringNew().call(() -> {
            SnapshotRow row = SnapshotRow.findById(sku);
            if (row != null) {
                em.detach(row);
            }
            return row;
        });
    }

    private long events(String sku) {
        return QuarkusTransaction.requiringNew().call(() -> StoredStockEvent.count("streamId", sku));
    }

    @Test
    void aCommandWritesASnapshotEveryNEventsAndALoadReplaysOnlyTheTail() {
        String sku = Ledgers.newSku();
        int expected = Ledgers.seed(em, sku, 1000);
        var firstLoad = ledger.load(sku, true);
        assertEquals(1000, firstLoad.stats().eventsReplayed(), "bulk-loaded history is read without a snapshot");
        assertEquals(expected, firstLoad.aggregate().quantityOnHand());
        assertNull(snapshot(sku), "a read writes nothing");

        commands.reserve(sku, "O-1", 1);
        var written = snapshot(sku);
        assertEquals(1001, written.version);
        assertEquals("inline", written.takenBy);
        assertEquals(1, written.schemaVersion);

        for (int i = 0; i < 99; i++) {
            commands.reserve(sku, "O-" + (i + 2), 1);
        }
        assertEquals(1001, snapshot(sku).version, "99 more events are still under the threshold");
        var tail = ledger.load(sku, true);
        assertEquals(99, tail.stats().eventsReplayed());

        commands.reserve(sku, "O-101", 1);
        assertEquals(1101, snapshot(sku).version);
        var after = ledger.load(sku, true);
        assertEquals(0, after.stats().eventsReplayed());
        assertEquals(expected - 101, after.aggregate().quantityOnHand());
        assertEquals(ledger.load(sku, false).aggregate().quantityOnHand(), after.aggregate().quantityOnHand());
    }

    @Test
    void aSnapshotWithAnotherSchemaIsIgnoredAndTheStateIsUnchanged() {
        String sku = Ledgers.newSku();
        Ledgers.seed(em, sku, 300);
        commands.reserve(sku, "O-1", 1);
        var before = ledger.load(sku, true);
        assertEquals(0, before.stats().eventsReplayed());
        QuarkusTransaction.requiringNew().run(
                () -> em.createNativeQuery("UPDATE inventory.snapshots SET schema_version = 0 WHERE stream_id = ?1")
                        .setParameter(1, sku).executeUpdate());
        var after = ledger.load(sku, true);
        assertEquals(301, after.stats().eventsReplayed(), "the whole stream is replayed");
        assertEquals(before.aggregate().quantityOnHand(), after.aggregate().quantityOnHand());
        assertEquals(before.aggregate().version(), after.aggregate().version());
    }

    @Test
    void theGuardedUpsertNeverLowersTheVersion() {
        String sku = Ledgers.newSku();
        Ledgers.seed(em, sku, 200);
        var older = ledger.load(sku, false).aggregate();
        commands.reserve(sku, "O-1", 1);
        assertEquals(201, snapshot(sku).version, "the command wrote a snapshot at 201");
        int stale = QuarkusTransaction.requiringNew().call(() -> store.upsert(older, "camel"));
        assertEquals(0, stale, "the snapshot at 200 is refused");
        assertEquals(201, snapshot(sku).version);
        assertEquals("inline", snapshot(sku).takenBy);
    }

    @Test
    void aRefusedReservationAppendsNothingAndLeavesTheSnapshot() {
        String sku = Ledgers.newSku();
        Ledgers.seed(em, sku, 150);
        commands.reserve(sku, "O-1", 1);
        var snapshotBefore = snapshot(sku);
        long eventsBefore = events(sku);
        assertThrows(OutOfStock.class, () -> commands.reserve(sku, "O-2", 1_000_000));
        assertEquals(eventsBefore, events(sku));
        var snapshotAfter = snapshot(sku);
        assertEquals(snapshotBefore.version, snapshotAfter.version);
        assertEquals(snapshotBefore.takenAt, snapshotAfter.takenAt);
    }

    @Test
    void eightWritersOnOneSkuLoseNothing() throws Exception {
        String sku = Ledgers.newSku();
        int start = Ledgers.seed(em, sku, 2000);
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                int writer = t;
                futures.add(pool.submit((Callable<Void>) () -> {
                    for (int i = 0; i < 25; i++) {
                        commands.reserve(sku, "O-" + writer + "-" + i, 1);
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        }
        var loaded = ledger.load(sku, false);
        assertEquals(start - 200, loaded.aggregate().quantityOnHand());
        assertEquals(2200, loaded.aggregate().version());
        assertEquals(loaded.aggregate().quantityOnHand(), ledger.load(sku, true).aggregate().quantityOnHand());
        assertNotNull(snapshot(sku));
        assertTrue(snapshot(sku).version <= 2200);
    }
}
