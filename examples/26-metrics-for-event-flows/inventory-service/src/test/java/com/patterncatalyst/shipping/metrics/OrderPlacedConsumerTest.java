package com.patterncatalyst.shipping.metrics;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.Topics;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/** The consumer against a real broker: a reservation, and a dead letter that keeps what a redrive needs. */
@QuarkusTest
class OrderPlacedConsumerTest {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrap;

    @Inject
    EntityManager em;

    private static String value(String id, String sku) {
        return "{\"orderId\":\"" + id + "\",\"customerId\":\"CU-26\",\"itemSku\":\"" + sku + "\",\"quantity\":1}";
    }

    private void send(String id, String value, Instant occurredAt) throws Exception {
        try (var producer = new KafkaProducer<>(Map.<String, Object>of("bootstrap.servers", bootstrap),
                new StringSerializer(), new ByteArraySerializer())) {
            var record = new ProducerRecord<>(Topics.ORDERS_PLACED, id, value.getBytes(StandardCharsets.UTF_8));
            record.headers().add(new RecordHeader(FlowAge.OCCURRED_AT, occurredAt.toString().getBytes(StandardCharsets.UTF_8)));
            producer.send(record).get();
        }
    }

    private List<ConsumerRecord<String, byte[]>> deadLetters(String id, Duration wait) {
        var found = new ArrayList<ConsumerRecord<String, byte[]>>();
        var props = Map.<String, Object>of("bootstrap.servers", bootstrap, "group.id", "test-" + UUID.randomUUID(),
                "auto.offset.reset", "earliest");
        try (var consumer = new KafkaConsumer<>(props, new StringDeserializer(), new ByteArrayDeserializer())) {
            consumer.subscribe(List.of(Topics.ORDERS_PLACED_DLQ));
            long end = System.nanoTime() + wait.toNanos();
            while (System.nanoTime() < end && found.isEmpty()) {
                consumer.poll(Duration.ofMillis(500)).forEach(r -> {
                    if (id.equals(r.key())) {
                        found.add(r);
                    }
                });
            }
        }
        return found;
    }

    private int stock(String sku) {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) em
                .createNativeQuery("select quantity_on_hand from inventory.inventory_item where sku = ?1")
                .setParameter(1, sku).getSingleResult()).intValue());
    }

    @Test
    void anOrderForAKnownSkuIsReserved() throws Exception {
        int before = stock("SKU-26");
        send("O-" + UUID.randomUUID(), value("O-ok", "SKU-26"), Instant.now());
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertEquals(before - 1, stock("SKU-26")));
    }

    @Test
    void anOrderForAnUnknownSkuLandsOnTheDeadLetterTopicWithItsOriginalHeader() throws Exception {
        String id = "O-" + UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-11-14T12:00:00.123456Z");
        String bytes = value(id, "SKU-26-LATE");
        send(id, bytes, occurredAt);

        var dead = deadLetters(id, Duration.ofSeconds(30));
        assertEquals(1, dead.size());
        assertArrayEquals(bytes.getBytes(StandardCharsets.UTF_8), dead.get(0).value());
        var header = dead.get(0).headers().lastHeader(FlowAge.OCCURRED_AT);
        assertNotNull(header, "the dead letter keeps the original headers, so a redrive keeps the event time");
        assertEquals(occurredAt.toString(), new String(header.value(), StandardCharsets.UTF_8));
    }

    @Test
    void aMalformedRecordLandsOnTheDeadLetterTopicToo() throws Exception {
        String id = "O-" + UUID.randomUUID();
        send(id, "{\"orderId\":\"" + id + "\",", Instant.now());
        assertTrue(deadLetters(id, Duration.ofSeconds(30)).size() == 1);
    }
}
