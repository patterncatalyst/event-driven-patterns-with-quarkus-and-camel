package com.patterncatalyst.shipping.batch.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkerStepsTest {

    @Inject
    PlanCommand plan;
    @Inject
    ReconcileCommand reconcile;
    @Inject
    AggregateCommand aggregate;
    @Inject
    EntityManager em;

    @BeforeAll
    static void seed() throws Exception {
        Fixtures.seed("44");
    }

    private static RunPartition row(String run, int partition) {
        return QuarkusTransaction.requiringNew()
                .call(() -> RunPartition.<RunPartition> findById(new RunPartition.Key(run, partition)));
    }

    private static Map<String, long[]> results(String run, int partition) {
        return QuarkusTransaction.requiringNew().call(() -> {
            var out = new java.util.TreeMap<String, long[]>();
            PartitionResult.<PartitionResult> list("runId = ?1 and partition = ?2", run, partition)
                    .forEach(r -> out.put(r.carrier, new long[] { r.scheduled, r.delivered, r.open }));
            return out;
        });
    }

    private String planned() {
        String run = Fixtures.newRun(em);
        Fixtures.env(run);
        assertEquals(0, plan.call());
        return run;
    }

    private int reconcile(String run, int index, String... env) {
        String[] all = new String[env.length + 2];
        System.arraycopy(env, 0, all, 2, env.length);
        all[0] = "JOB_COMPLETION_INDEX";
        all[1] = String.valueOf(index);
        Fixtures.env(run, all);
        return reconcile.call();
    }

    @Test
    @Order(1)
    void planRecordsTheOffsetsOfTheWindowAndFallsBackToTheEndOffset() {
        String run = planned();
        // partition 0: the record before the window is offset 0, the window starts at 1 and the record after it is 4
        RunPartition p0 = row(run, 0);
        assertEquals(1, p0.scheduledFrom);
        assertEquals(4, p0.scheduledTo);
        assertEquals(0, p0.deliveredFrom);
        // no delivered record is late enough for the end of the window: the end offset
        assertEquals(2, p0.deliveredTo);
        // partition 2 has no scheduled record after the window: offsetsForTimes returns null, the end offset is used
        RunPartition p2 = row(run, 2);
        assertEquals(1, p2.scheduledFrom);
        assertEquals(4, p2.scheduledTo);
        // partition 1 delivered has one record, before the window: both ends are the end offset
        RunPartition p1 = row(run, 1);
        assertEquals(1, p1.deliveredFrom);
        assertEquals(1, p1.deliveredTo);
        assertEquals("PENDING", p1.status);
        assertEquals(0, p1.attempts);
    }

    @Test
    @Order(2)
    void reconcileCountsOrdersPerCarrierInTheRecordedRange() {
        String run = planned();
        assertEquals(0, reconcile(run, 0));
        assertEquals(0, reconcile(run, 1));
        var p0 = results(run, 0);
        assertEquals("[2, 1, 1]", java.util.Arrays.toString(p0.get("FASTLINE")));
        assertEquals("[1, 1, 0]", java.util.Arrays.toString(p0.get("AIRCO")));
        assertEquals("[2, 0, 2]", java.util.Arrays.toString(results(run, 1).get("FASTLINE")));
        assertEquals("[1, 0, 1]", java.util.Arrays.toString(results(run, 1).get("AIRCO")));
        RunPartition done = row(run, 0);
        assertEquals("DONE", done.status);
        assertEquals(5, done.recordsRead, "three scheduled and two delivered inside the window");
        assertEquals(1, done.attempts);
        assertEquals(3, row(run, 1).recordsRead);
    }

    @Test
    @Order(3)
    void aPartitionThatIsDoneIsSkippedAndARepeatChangesNothing() {
        String run = planned();
        assertEquals(0, reconcile(run, 0));
        Instant completed = row(run, 0).completedAt;
        var before = results(run, 0);

        assertEquals(0, reconcile(run, 0));
        assertEquals(1, row(run, 0).attempts, "a skipped run is not an attempt");
        assertEquals(completed, row(run, 0).completedAt);
        assertEquals(before.keySet(), results(run, 0).keySet());
        before.forEach((carrier, counts) -> assertEquals(java.util.Arrays.toString(counts),
                java.util.Arrays.toString(results(run, 0).get(carrier))));

        // with the guard off the partition is read again; the upsert leaves the same rows, which is why totals stay right
        assertEquals(0, reconcile(run, 0, "REPLAY_GUARD", "off"));
        assertEquals(2, row(run, 0).attempts);
        assertNotEquals(null, row(run, 0).completedAt);
        before.forEach((carrier, counts) -> assertEquals(java.util.Arrays.toString(counts),
                java.util.Arrays.toString(results(run, 0).get(carrier))));
        assertEquals(2, QuarkusTransaction.requiringNew().call(() -> PartitionResult.count("runId = ?1 and partition = 0", run)));
    }

    @Test
    @Order(4)
    void theFaultSwitchesExitWithTheCodesOfTheContract() {
        String run = planned();
        assertEquals(1, reconcile(run, 1, "FAULT_PARTITION", "1", "FAULT_MODE", "once", "JOB_INDEX_FAILURE_COUNT", "0"));
        assertEquals(1, row(run, 1).attempts);
        assertEquals("PENDING", row(run, 1).status);
        assertEquals(0, reconcile(run, 1, "FAULT_PARTITION", "1", "FAULT_MODE", "once", "JOB_INDEX_FAILURE_COUNT", "1"));
        assertEquals(2, row(run, 1).attempts, "the retried index shows two attempts");
        assertEquals(0, reconcile(run, 0, "FAULT_PARTITION", "1", "FAULT_MODE", "always"), "other indexes are unaffected");

        String poison = planned();
        assertEquals(42, reconcile(poison, 1, "FAULT_PARTITION", "1", "FAULT_MODE", "always"));
        assertEquals("PENDING", row(poison, 1).status);
    }

    @Test
    @Order(5)
    void aggregateRefusesUntilEveryPartitionIsDoneThenPublishesOneRecordPerCarrier() {
        String run = planned();
        assertEquals(0, reconcile(run, 0));
        assertEquals(0, reconcile(run, 1));
        Fixtures.env(run);
        assertEquals(1, aggregate.call(), "partition 2 is not DONE");
        assertEquals(0L, QuarkusTransaction.requiringNew().call(() -> CarrierTotal.count("runId", run)));

        assertEquals(0, reconcile(run, 2));
        Fixtures.env(run);
        assertEquals(0, aggregate.call());
        assertEquals(0, aggregate.call(), "a repeated aggregate leaves the same rows");
        var totals = QuarkusTransaction.requiringNew().call(() -> {
            var out = new java.util.TreeMap<String, String>();
            CarrierTotal.<CarrierTotal> list("runId", run)
                    .forEach(t -> out.put(t.carrier, t.scheduled + "/" + t.delivered + "/" + t.open));
            return out;
        });
        assertEquals(Map.of("AIRCO", "3/2/1", "FASTLINE", "6/2/4"), totals);

        var published = Fixtures.readAll("edp.shipping.reconciliations");
        assertTrue(published.containsKey(run + ":FASTLINE"), published.keySet().toString());
        assertTrue(published.get(run + ":FASTLINE").contains("\"open\":4"), published.get(run + ":FASTLINE"));
        assertTrue(published.get(run + ":AIRCO").contains("\"scheduled\":3"));
    }
}
