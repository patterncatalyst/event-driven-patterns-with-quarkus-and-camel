package com.patterncatalyst.shipping.ordering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.apache.camel.ProducerTemplate;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

@QuarkusTest
@TestProfile(ResequenceGapTest.ShortTimeout.class)
class ResequenceGapTest {

    static final long TIMEOUT_MS = 2000;

    public static class ShortTimeout implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("order.resequence.from", "direct:funnel",
                    "order.resequence.timeout", Long.toString(TIMEOUT_MS));
        }
    }

    @Inject
    ProducerTemplate producer;

    @Test
    void aMissingSequenceNumberHoldsLaterEventsUntilTheTimeoutReleasesThem() {
        // Stream sequence 2 never arrives.
        producer.sendBody("direct:funnel", Fixtures.json("GAP-1", "ShipmentScheduled", 1, 1));
        Fixtures.awaitApplied("GAP-1", 1);

        long sent = System.nanoTime();
        producer.sendBody("direct:funnel", Fixtures.json("GAP-1", "ShipmentDelivered", 3, 3));
        assertEquals(List.of("ShipmentScheduled"), Fixtures.appliedTypes("GAP-1"),
                "event 3 is held while 2 is missing");

        Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100))
                .until(() -> Fixtures.appliedTypes("GAP-1").size() == 2);
        long heldMs = Duration.ofNanos(System.nanoTime() - sent).toMillis();
        assertTrue(heldMs >= TIMEOUT_MS - 200, "held about the timeout, not released early: " + heldMs + " ms");
        assertEquals(List.of("ShipmentScheduled", "ShipmentDelivered"), Fixtures.appliedTypes("GAP-1"));
    }
}
