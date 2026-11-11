package com.patterncatalyst.shipping.ordering;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.OrderStatus;
import com.patterncatalyst.shipping.Topics;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ArrivalOrderTest {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    private void send(String orderId, String type, int sequence) {
        try (var producer = new KafkaProducer<>(Map.<String, Object>of("bootstrap.servers", bootstrapServers),
                new StringSerializer(), new StringSerializer())) {
            producer.send(new ProducerRecord<>(Topics.SHIPPING_SHIPMENTS, orderId,
                    Fixtures.json(orderId, type, sequence)));
        }
    }

    @Test
    void inLifecycleOrderTheOrderEndsDelivered() {
        send("ARR-1", "ShipmentScheduled", 1);
        send("ARR-1", "ShipmentInTransit", 2);
        send("ARR-1", "ShipmentDelivered", 3);
        Fixtures.awaitApplied("ARR-1", 3);
        assertEquals(List.of("ShipmentScheduled", "ShipmentInTransit", "ShipmentDelivered"),
                Fixtures.appliedTypes("ARR-1"));
        assertEquals(OrderStatus.DELIVERED, Fixtures.statusOf("ARR-1"));
    }

    @Test
    void deliveredArrivingBeforeScheduledLeavesTheOrderShipped() {
        // Last write wins: the status follows arrival, not the lifecycle.
        send("ARR-2", "ShipmentDelivered", 3);
        send("ARR-2", "ShipmentScheduled", 1);
        Fixtures.awaitApplied("ARR-2", 2);
        assertEquals(OrderStatus.SHIPPED, Fixtures.statusOf("ARR-2"));
    }
}
