package com.patterncatalyst.shipping.inbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/** The inbox in the effect's transaction: once per event id, nothing left behind by a failed effect, safe under a race. */
@QuarkusTest
class InboxTest {

    @Inject
    ReservationService reservations;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Test
    void theSameEventIdIsAppliedOnce() {
        String sku = Fixtures.newSku();
        UUID id = UUID.randomUUID();
        assertTrue(reservations.reserveOnce(id, Fixtures.order("O-1", sku, 3)));
        assertFalse(reservations.reserveOnce(id, Fixtures.order("O-1", sku, 3)));
        assertEquals(97, Fixtures.stock(sku));
        assertEquals(1, Fixtures.inboxRows(id));
    }

    @Test
    void aFailedEffectLeavesNoInboxRow() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> reservations.reserveOnce(id, Fixtures.order("O-2", "SKU-UNKNOWN", 1)));
        assertEquals(0, Fixtures.inboxRows(id));
    }

    @Test
    void twoThreadsWithTheSameEventIdApplyItOnce() throws Exception {
        String sku = Fixtures.newSku();
        try (var pool = Executors.newFixedThreadPool(2)) {
            for (int round = 1; round <= 20; round++) {
                UUID id = UUID.randomUUID();
                var barrier = new CyclicBarrier(2);
                List<Future<Boolean>> results = new ArrayList<>();
                for (int t = 0; t < 2; t++) {
                    results.add(pool.submit(() -> {
                        barrier.await();
                        return reservations.reserveOnce(id, Fixtures.order("O-3", sku, 1));
                    }));
                }
                int applied = 0;
                for (Future<Boolean> result : results) {
                    if (result.get()) {
                        applied++;
                    }
                }
                assertEquals(1, applied, "round " + round);
                assertEquals(100 - round, Fixtures.stock(sku), "round " + round);
            }
        }
    }

    @Test
    void aDuplicateRecordIsAppliedOnce() throws Exception {
        String sku = Fixtures.newSku();
        UUID duplicated = UUID.randomUUID();
        Fixtures.send(bootstrapServers, duplicated, Fixtures.order("O-4", sku, 3));
        Fixtures.send(bootstrapServers, duplicated, Fixtures.order("O-4", sku, 3));
        Fixtures.send(bootstrapServers, UUID.randomUUID(), Fixtures.order("O-5", sku, 2));
        Fixtures.awaitStock(sku, 95);
        Fixtures.settle();
        assertEquals(95, Fixtures.stock(sku));
        assertEquals(1, Fixtures.inboxRows(duplicated));
    }
}
