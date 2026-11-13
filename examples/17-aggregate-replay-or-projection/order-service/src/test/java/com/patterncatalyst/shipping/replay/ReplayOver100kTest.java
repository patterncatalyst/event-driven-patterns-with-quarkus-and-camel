package com.patterncatalyst.shipping.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * The replay over 100,000 seeded orders (360,000 events) in the 256 MB heap of the test JVM: the stream folds one
 * aggregate at a time. The seed is the one verify.sh loads. Afterwards the table is emptied (the append-only
 * triggers are switched off for that one statement, in a test database only) so the other tests see a small store.
 */
@QuarkusTest
class ReplayOver100kTest {

    @Inject
    AgroalDataSource ds;

    @Inject
    ReplayReport replay;

    @Inject
    ProjectionBatch batch;

    @Test
    void theReplayFoldsHundredThousandOrdersAndMatchesTheSqlOracle() throws Exception {
        String seed = Files.readString(Path.of("..", "seed.sql")).replaceAll("(?<!:):lo\\b", "1").replaceAll("(?<!:):hi\\b", "100000");
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("TRUNCATE orders.carrier_daily_shipments");
            st.execute("ALTER TABLE orders.events DISABLE TRIGGER USER");
            st.execute("TRUNCATE orders.events");
            st.execute("ALTER TABLE orders.events ENABLE TRIGGER USER");
            st.execute(seed);

            ReplayReport.Result result = replay.run();

            Map<String, Long> oracle = new TreeMap<>();
            try (ResultSet rs = st.executeQuery("""
                    SELECT e.payload->>'carrier' || '|' || (e.occurred_at AT TIME ZONE 'UTC')::date, count(*)
                    FROM orders.events e
                    WHERE e.type = 'OrderShipped'
                      AND NOT EXISTS (SELECT 1 FROM orders.events c WHERE c.stream_id = e.stream_id AND c.type = 'OrderCancelled')
                    GROUP BY 1
                    """)) {
                while (rs.next()) {
                    oracle.put(rs.getString(1), rs.getLong(2));
                }
            }
            assertEquals(360_000, result.eventsRead());
            assertEquals(oracle, asMap(result));
            st.execute("ALTER TABLE orders.events DISABLE TRIGGER USER");
            st.execute("TRUNCATE orders.events");
            st.execute("ALTER TABLE orders.events ENABLE TRIGGER USER");
        }
        batch.reset();
    }

    private static Map<String, Long> asMap(ReplayReport.Result result) {
        Map<String, Long> out = new TreeMap<>();
        result.rows().forEach(r -> out.put(r.carrier() + "|" + r.day(), r.shipments()));
        return out;
    }
}
