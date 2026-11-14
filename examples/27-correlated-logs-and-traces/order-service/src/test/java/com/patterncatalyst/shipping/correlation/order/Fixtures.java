package com.patterncatalyst.shipping.correlation.order;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;

import com.patterncatalyst.shipping.Order;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.persistence.EntityManager;

/** Test helpers: orders in a given saga state, the rows to read, and records with an id header. */
final class Fixtures {

    private Fixtures() {
    }

    static String newId() {
        return "O-" + UUID.randomUUID();
    }

    static Order order(String id) {
        return new Order(id, "CU-27", "SKU-27", 1, new BigDecimal("25.00"));
    }

    static SagaEntity saga(String orderId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            SagaEntity saga = SagaEntity.findById(orderId);
            return saga;
        });
    }

    static SagaState state(String orderId) {
        return saga(orderId).state;
    }

    static String orderStatus(String orderId) {
        return QuarkusTransaction.requiringNew().call(() -> ((OrderEntity) OrderEntity.findById(orderId)).status.name());
    }

    /** The saga's deadline moved into the past, so the sweeper finds it. */
    static void expire(String orderId) {
        QuarkusTransaction.requiringNew().run(() -> SagaEntity.update("deadline = ?1 where orderId = ?2",
                Instant.now().minusSeconds(60), orderId));
    }

    static long outbox(EntityManager em, String type, String orderId) {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) em.createNativeQuery(
                "select count(*) from orders.outboxevent where type = ?1 and aggregateid = ?2")
                .setParameter(1, type).setParameter(2, orderId).getSingleResult()).longValue());
    }

    static void send(String bootstrapServers, String topic, String key, UUID eventId, String json) throws Exception {
        try (var producer = new KafkaProducer<String, String>(Map.<String, Object>of(
                "bootstrap.servers", bootstrapServers,
                "key.serializer", StringSerializer.class.getName(),
                "value.serializer", StringSerializer.class.getName()))) {
            var record = new ProducerRecord<>(topic, key, json);
            record.headers().add("id", eventId.toString().getBytes(StandardCharsets.UTF_8));
            producer.send(record).get();
        }
    }

    static void await(Supplier<Boolean> condition) {
        Awaitility.await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(250)).until(condition::get);
    }
}
