package com.patterncatalyst.shipping.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;

@QuarkusTest
class ProjectionTest extends ProjectionBase {

    @InjectSpy
    CarrierDailyStore spy;

    private void history(int year) throws Exception {
        try (Connection c = ds.getConnection()) {
            for (int i = 0; i < 12; i++) {
                String order = "O-P" + year + "-" + i;
                Histories.shippedOrder(c, order, i % 3 == 0 ? "AIRCO" : "FASTLINE", year + "-06-0" + (1 + i % 4) + "T09:00:00Z");
                if (i % 4 == 0) {
                    Histories.delivered(c, order);
                }
            }
            Histories.placed(c, "O-P" + year + "-x");
            Histories.paid(c, "O-P" + year + "-x");
            Histories.cancelled(c, "O-P" + year + "-x");
        }
    }

    @Test
    void theProjectionEqualsTheReplayAfterCatchUp() throws Exception {
        history(2002);
        rebuild(1000);

        assertEquals(0, batch.lagEvents());
        var projected = Histories.ofYear(store.rows(), 2002);
        assertFalse(projected.isEmpty());
        assertEquals(12, Histories.total(projected), "12 shipped orders, the cancelled one is not counted");
        assertEquals(Histories.ofYear(replay.run().rows(), 2002), projected);
    }

    @Test
    void batchesOfThreeCountEveryEventOnce() throws Exception {
        history(2003);
        rebuild(3);

        assertEquals(Histories.ofYear(replay.run().rows(), 2003), Histories.ofYear(store.rows(), 2003));
    }

    @Test
    void aFailureInsideABatchRollsBackTheRowsAndTheCheckpoint() throws Exception {
        history(2004);
        batch.reset();
        ProjectionBatch.Position start = batch.checkpoint();
        Mockito.doCallRealMethod().doThrow(new IllegalStateException("second upsert fails"))
                .when(spy).add(Mockito.anyString(), Mockito.any(LocalDate.class), Mockito.anyLong());

        assertThrows(IllegalStateException.class, () -> batch.run(1000));

        assertEquals(start, batch.checkpoint(), "the checkpoint did not move");
        assertEquals(0, store.rows().size(), "the first upsert was rolled back with the batch");
        Mockito.reset(spy);
        drain(1000);
        assertEquals(Histories.ofYear(replay.run().rows(), 2004), Histories.ofYear(store.rows(), 2004));
    }

    @Test
    void concurrentTicksSerializeOnTheCheckpointRow() throws Exception {
        history(2005);
        batch.reset();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<Integer>> ticks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Callable<Integer> tick = () -> batch.run(5);
            ticks.add(pool.submit(tick));
        }
        for (Future<Integer> tick : ticks) {
            tick.get();
        }
        pool.shutdown();
        drain(1000);

        assertEquals(Histories.ofYear(replay.run().rows(), 2005), Histories.ofYear(store.rows(), 2005),
                "four overlapping batches counted every event once");
    }

    @Test
    void rebuildingDropsTheRowsAndTheProjectionCatchesUpAgain() throws Exception {
        history(2006);
        rebuild(1000);
        batch.reset();

        assertEquals(0, store.rows().size());
        assertEquals(new ProjectionBatch.Position(0, 0), batch.checkpoint());
        assertEquals(true, batch.lagEvents() > 0);
        drain(1000);
        assertEquals(0, batch.lagEvents());
        assertEquals(Histories.ofYear(replay.run().rows(), 2006), Histories.ofYear(store.rows(), 2006));
    }
}
