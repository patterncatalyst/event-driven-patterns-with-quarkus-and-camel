package com.patterncatalyst.shipping.ordering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(OrderIdKeyTest.OrderIdKey.class)
class OrderIdKeyTest {

    public static class OrderIdKey implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("shipping.partition-key", "order-id");
        }
    }

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Test
    void oneOrdersEventsShareThePartitionTheHashPredicts() {
        for (int n = 1; n <= 6; n++) {
            String orderId = "OID-" + n;
            List<Receipt> receipts = Placement.publish(orderId, null);
            assertEquals(List.of("ShipmentScheduled", "ShipmentInTransit", "ShipmentDelivered"),
                    receipts.stream().map(Receipt::type).toList());
            assertEquals(List.of(1, 2, 3), receipts.stream().map(Receipt::sequence).toList());
            assertTrue(receipts.stream().allMatch(r -> r.key().equals(orderId)));
            assertTrue(receipts.stream().allMatch(r -> r.partition() == Placement.expected(orderId)),
                    "partition equals murmur2(order id) mod 3");
            assertTrue(receipts.get(0).offset() < receipts.get(1).offset()
                    && receipts.get(1).offset() < receipts.get(2).offset(), "offsets increase in lifecycle order");
        }
    }

    @Test
    void topicRecordsCarryTheSamePlacementAndIncreasingOffsets() {
        Placement.publish("OIDREAD-1", null);
        var records = Placement.readTopic(bootstrapServers, "OIDREAD-1");
        assertEquals(3, records.size());
        assertEquals(1, records.stream().map(r -> r.partition()).distinct().count());
        assertEquals(Placement.expected("OIDREAD-1"), records.getFirst().partition());
        var offsets = records.stream().map(r -> r.offset()).collect(Collectors.toList());
        assertEquals(offsets.stream().sorted().toList(), offsets);
        assertTrue(records.get(0).value().contains("ShipmentScheduled")
                && records.get(2).value().contains("ShipmentDelivered"));
    }
}
