package com.patterncatalyst.shipping.snapshots;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/** The Camel route is the only writer of snapshots in this profile. */
@QuarkusTest
@TestProfile(SnapshotterProfile.class)
class SnapshotterRouteTest {

    @Inject
    ReservationCommands commands;

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

    @Test
    void theSchedulerSnapshotsAStreamThatIsPastNAndCommandsDoNot() {
        String sku = Ledgers.newSku();
        Ledgers.seed(em, sku, 250);
        commands.reserve(sku, "O-1", 1);
        assertNull(snapshot(sku), "the command path wrote no snapshot");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            var row = snapshot(sku);
            assertEquals("camel", row == null ? null : row.takenBy);
            assertEquals(251, row.version);
        });
    }
}
