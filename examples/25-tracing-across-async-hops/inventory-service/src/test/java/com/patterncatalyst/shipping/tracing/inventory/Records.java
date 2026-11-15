package com.patterncatalyst.shipping.tracing.inventory;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import com.patterncatalyst.shipping.Topics;

import io.quarkus.narayana.jta.QuarkusTransaction;

/** Test helpers: a stock row, and an OrderPlaced record with or without a trace context in its headers. */
final class Records {

    private Records() {
    }

    static String hex(int bytes) {
        byte[] b = new byte[bytes];
        ThreadLocalRandom.current().nextBytes(b);
        b[0] |= 1;
        return HexFormat.of().formatHex(b);
    }

    static void stock(String sku, int quantity) {
        QuarkusTransaction.requiringNew().run(() -> StockItem.of(sku, quantity).persist());
    }

    static int onHand(String sku) {
        return QuarkusTransaction.requiringNew().call(() -> ((StockItem) StockItem.findById(sku)).quantityOnHand);
    }

    /** Publishes OrderPlaced for the order; traceparent is null for a record that carries no context. */
    static void placed(String bootstrap, String orderId, String sku, int quantity, String traceparent) {
        String json = "{\"orderId\":\"%s\",\"customerId\":\"CU-25\",\"itemSku\":\"%s\",\"quantity\":%d,\"amount\":10.00}"
                .formatted(orderId, sku, quantity);
        try (var producer = new KafkaProducer<>(Map.<String, Object>of("bootstrap.servers", bootstrap),
                new StringSerializer(), new StringSerializer())) {
            var record = new ProducerRecord<>(Topics.ORDERS_PLACED, orderId, json);
            if (traceparent != null) {
                record.headers().add("traceparent", traceparent.getBytes(StandardCharsets.UTF_8));
            }
            producer.send(record);
            producer.flush();
        }
    }
}
