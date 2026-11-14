package com.patterncatalyst.shipping.saga.payment;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.persistence.EntityManager;

/** Test helpers: ids, the rows to read, and records on a topic. */
final class Fixtures {

    private Fixtures() {
    }

    static String newId() {
        return "O-" + UUID.randomUUID();
    }

    static InventoryReserved reserved(String orderId, String amount) {
        return new InventoryReserved(orderId, "SKU-22", 1, new BigDecimal(amount));
    }

    static String paymentStatus(String orderId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            PaymentEntity row = PaymentEntity.findById(orderId);
            return row == null ? "NONE" : row.status;
        });
    }

    static long outbox(EntityManager em, String type, String orderId) {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) em.createNativeQuery(
                "select count(*) from payments.outboxevent where type = ?1 and aggregateid = ?2")
                .setParameter(1, type).setParameter(2, orderId).getSingleResult()).longValue());
    }

    static boolean cancelledRow(EntityManager em, String orderId) {
        return QuarkusTransaction.requiringNew().call(() -> !em.createNativeQuery(
                "select 1 from payments.cancelled_order where order_id = ?1").setParameter(1, orderId).getResultList().isEmpty());
    }

    static void send(String bootstrapServers, String topic, String key, String json) throws Exception {
        try (var producer = new KafkaProducer<String, String>(Map.<String, Object>of(
                "bootstrap.servers", bootstrapServers,
                "key.serializer", StringSerializer.class.getName(),
                "value.serializer", StringSerializer.class.getName()))) {
            producer.send(new ProducerRecord<>(topic, key, json)).get();
        }
    }

    static void await(Supplier<Boolean> condition) {
        Awaitility.await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(250)).until(condition::get);
    }
}
