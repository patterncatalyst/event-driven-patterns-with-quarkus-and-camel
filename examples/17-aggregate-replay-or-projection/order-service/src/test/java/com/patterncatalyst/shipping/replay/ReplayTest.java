package com.patterncatalyst.shipping.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/** The replay against a small history whose answer is worked out by hand. */
@QuarkusTest
class ReplayTest {

    @Inject
    AgroalDataSource ds;

    @Inject
    ReplayReport replay;

    @Test
    void replayCountsShippedAndDeliveredOrdersByCarrierAndUtcDay() throws Exception {
        try (Connection c = ds.getConnection()) {
            Histories.shippedOrder(c, "O-R1", "FASTLINE", "2001-03-04T23:30:00Z");
            Histories.shippedOrder(c, "O-R2", "FASTLINE", "2001-03-04T00:30:00Z");
            Histories.shippedOrder(c, "O-R3", "AIRCO", "2001-03-04T12:00:00Z");
            Histories.delivered(c, "O-R3");
            // 01:00 at +02:00 is 23:00 UTC the day before: the day is the UTC day.
            Histories.shippedOrder(c, "O-R4", "AIRCO", "2001-03-05T01:00:00+02:00");
            Histories.shippedOrder(c, "O-R5", "AIRCO", "2001-03-05T00:00:00Z");
            Histories.placed(c, "O-R6");
            Histories.paid(c, "O-R6");
            Histories.cancelled(c, "O-R6");
            Histories.placed(c, "O-R7");
        }

        ReplayReport.Result result = replay.run();

        assertEquals(Map.of("AIRCO|2001-03-04", 2L, "AIRCO|2001-03-05", 1L, "FASTLINE|2001-03-04", 2L),
                Histories.ofYear(result.rows(), 2001));
        assertEquals(true, result.eventsRead() >= 3 * 5 + 1 + 3 + 1);
    }
}
