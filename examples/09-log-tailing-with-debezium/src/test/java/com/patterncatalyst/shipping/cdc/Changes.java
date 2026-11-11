package com.patterncatalyst.shipping.cdc;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import com.patterncatalyst.shipping.Topics;

/** Test helpers: write Debezium-shaped change records, read the documents the route publishes. */
final class Changes {

    private Changes() {
    }

    /** A change envelope as Kafka Connect writes it with schemas off; the source block is trimmed. */
    static String envelope(String op, String sku, Integer qtyBefore, Integer qtyAfter) {
        return """
                {"before":%s,"after":%s,"source":{"lsn":1},"op":"%s","ts_ms":1}"""
                .formatted(op.equals("r") || op.equals("c") ? "null" : row(sku, qtyBefore),
                        op.equals("d") ? "null" : row(sku, qtyAfter), op);
    }

    private static String row(String sku, Integer qty) {
        return """
                {"item_sku":"%s","qty_on_hand":%s,"last_changed":"2026-11-11T10:00:00.000000Z"}"""
                .formatted(sku, qty);
    }

    static String key(String sku) {
        return """
                {"item_sku":"%s"}""".formatted(sku);
    }

    /** Sends one record to the change topic; a null value is Debezium's tombstone. */
    static void send(String bootstrapServers, String sku, String value) throws ExecutionException, InterruptedException {
        try (var producer = new KafkaProducer<>(Map.<String, Object>of("bootstrap.servers", bootstrapServers),
                new StringSerializer(), new StringSerializer())) {
            producer.send(new ProducerRecord<>(Topics.INVENTORY_ITEM_CHANGES, key(sku), value)).get();
        }
    }

    /** Every record on the documents topic for one SKU, oldest first, as "value" ("null" for a tombstone). */
    static List<String> documents(String bootstrapServers, String sku) {
        List<String> values = new ArrayList<>();
        try (var consumer = new KafkaConsumer<>(Map.<String, Object>of(
                "bootstrap.servers", bootstrapServers,
                "group.id", "test-" + UUID.randomUUID(),
                "auto.offset.reset", "earliest"), new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(Topics.INVENTORY_ITEMS));
            for (int i = 0; i < 3; i++) {
                for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofSeconds(1))) {
                    if (sku.equals(r.key())) {
                        values.add(r.value() == null ? "null" : r.value());
                    }
                }
            }
        }
        return values;
    }
}
