package com.patterncatalyst.shipping.ordering;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.OrderStatus;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

@QuarkusTest
@TestProfile(ResequenceOrderTest.Direct.class)
class ResequenceOrderTest {

    public static class Direct implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("order.resequence.from", "direct:funnel", "order.resequence.timeout", "5000");
        }
    }

    @Inject
    ProducerTemplate producer;

    @Test
    void eventsArrivingOutOfOrderAreAppliedInStreamSequence() {
        producer.sendBody("direct:funnel", Fixtures.json("RSQ-1", "ShipmentDelivered", 3, 3));
        producer.sendBody("direct:funnel", Fixtures.json("RSQ-1", "ShipmentScheduled", 1, 1));
        producer.sendBody("direct:funnel", Fixtures.json("RSQ-1", "ShipmentInTransit", 2, 2));
        Fixtures.awaitApplied("RSQ-1", 3);
        assertEquals(List.of("ShipmentScheduled", "ShipmentInTransit", "ShipmentDelivered"),
                Fixtures.appliedTypes("RSQ-1"));
        assertEquals(OrderStatus.DELIVERED, Fixtures.statusOf("RSQ-1"));
    }
}
