package com.patterncatalyst.shipping.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/** Demo switch PROJECTION_WATERMARK=off: the same two writers lose a row. This test asserts the bad outcome. */
@QuarkusTest
@TestProfile(PositionCheckpointGapTest.Off.class)
class PositionCheckpointGapTest extends GapBase {

    public static class Off implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("projection.watermark", "off");
        }
    }

    @Test
    void aCheckpointByPositionPassesTheOpenTransactionAndNeverCountsItsRow() throws Exception {
        batch.reset();

        long[] counted = gap(2008, "O-G3", "O-G4");

        assertEquals(1, counted[0], "B is counted and the checkpoint moves past A's position");
        assertEquals(1, counted[1], "A committed afterwards and is never counted");
        assertEquals(2, Histories.total(Histories.ofYear(replay.run().rows(), 2008)), "a replay counts both");
    }
}
