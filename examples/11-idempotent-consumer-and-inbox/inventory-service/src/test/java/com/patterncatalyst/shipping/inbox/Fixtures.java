package com.patterncatalyst.shipping.inbox;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;

import com.patterncatalyst.shipping.Topics;

import io.quarkus.narayana.jta.QuarkusTransaction;

/** Test helpers: stock rows, records with an id header, and the tables to read. */
final class Fixtures {

    private Fixtures() {
    }

    static String newSku() {
        String sku = "SKU-" + UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> InventoryItemEntity.of(sku, 100).persist());
        return sku;
    }

    static OrderPlaced order(String orderId, String sku, int quantity) {
        return new OrderPlaced(orderId, "CU-77", sku, quantity, new BigDecimal("59.90"));
    }

    static int stock(String sku) {
        return QuarkusTransaction.requiringNew().call(() -> ((InventoryItemEntity) InventoryItemEntity.findById(sku)).quantityOnHand);
    }

    static long inboxRows(UUID eventId) {
        return QuarkusTransaction.requiringNew().call(() -> InboxEntry.count("eventId", eventId));
    }

    /** Publishes OrderPlaced records, each with the id header the outbox would set. */
    static void send(String bootstrapServers, UUID eventId, OrderPlaced event) throws Exception {
        String json = """
                {"orderId":"%s","customerId":"%s","itemSku":"%s","quantity":%d,"amount":%s}"""
                .formatted(event.orderId(), event.customerId(), event.itemSku(), event.quantity(), event.amount());
        try (var producer = new KafkaProducer<String, String>(Map.<String, Object>of(
                "bootstrap.servers", bootstrapServers,
                "key.serializer", StringSerializer.class.getName(),
                "value.serializer", StringSerializer.class.getName()))) {
            var record = new ProducerRecord<>(Topics.ORDERS_PLACED, event.orderId(), json);
            record.headers().add("id", eventId.toString().getBytes(StandardCharsets.UTF_8));
            producer.send(record).get();
        }
    }

    static void awaitStock(String sku, int expected) {
        Awaitility.await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(250))
                .until(() -> stock(sku) == expected);
    }

    static void settle() throws InterruptedException {
        Thread.sleep(3000);
    }
}
