package com.patterncatalyst.shipping.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/** With the watermark, a row that committed after a later position waits until the earlier transaction ends. */
@QuarkusTest
class CommitOrderGapTest extends GapBase {

    @Test
    void theWatermarkCountsNeitherOrderUntilTheOpenTransactionCommits() throws Exception {
        batch.reset();

        long[] counted = gap(2007, "O-G1", "O-G2");

        assertEquals(0, counted[0], "B committed, A is still open: nothing past A is read");
        assertEquals(2, counted[1], "A committed: both are read");
        assertEquals(Histories.ofYear(replay.run().rows(), 2007), Histories.ofYear(store.rows(), 2007));
    }
}
