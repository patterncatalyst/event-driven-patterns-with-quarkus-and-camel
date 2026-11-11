package com.patterncatalyst.shipping.ordering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(EventTypeKeyTest.EventTypeKey.class)
class EventTypeKeyTest {

    public static class EventTypeKey implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("shipping.partition-key", "event-type");
        }
    }

    @Test
    void oneOrderIsSpreadAcrossPartitionsByEventType() {
        var receipts = Placement.publish("ETY-1", null);
        for (Receipt r : receipts) {
            assertEquals(r.type(), r.key());
            assertEquals(Placement.expected(r.type()), r.partition(), "partition equals murmur2(event type) mod 3");
        }
        long partitions = receipts.stream().map(Receipt::partition).distinct().count();
        assertTrue(partitions >= 2, "one order spans at least two partitions");
        // The stall demonstration needs Scheduled and Delivered on different partitions.
        assertNotEquals(Placement.expected("ShipmentScheduled"), Placement.expected("ShipmentDelivered"));
    }
}
