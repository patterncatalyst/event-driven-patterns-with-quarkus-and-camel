package com.patterncatalyst.shipping.snapshots;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/** A snapshot that fails in the Camel route leaves the command path alone, and a later tick takes it. */
@QuarkusTest
@TestProfile(SnapshotterProfile.class)
class SnapshotterRetryTest {

    @Inject
    ReservationCommands commands;

    @Inject
    EntityManager em;

    @InjectSpy
    Snapshots snapshots;

    private SnapshotRow snapshot(String sku) {
        return QuarkusTransaction.requiringNew().call(() -> {
            SnapshotRow row = SnapshotRow.findById(sku);
            if (row != null) {
                em.detach(row);
            }
            return row;
        });
    }

    @Test
    void aSnapshotThatFailsOnceLeavesCommandsWorkingAndALaterTickWritesIt() {
        String sku = Ledgers.newSku();
        Ledgers.seed(em, sku, 250);
        doThrow(new IllegalStateException("snapshot failed")).doCallRealMethod().when(snapshots).take(sku);

        // The first tick that finds the stream fails; no snapshot exists yet.
        verify(snapshots, timeout(20_000)).take(sku);
        assertNull(snapshot(sku), "the failed take wrote nothing");

        var result = commands.reserve(sku, "O-1", 1);
        assertEquals(251, result.version(), "the command succeeds while the snapshotter is failing");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            var row = snapshot(sku);
            assertEquals("camel", row == null ? null : row.takenBy);
            assertTrue(row.version >= 250, "the retry wrote a snapshot at the head it saw");
        });
        verify(snapshots, atLeast(2)).take(sku);
    }
}
