package com.patterncatalyst.shipping.aggregate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.persistence.EntityManager;

/** Helpers shared by the tests: read the tables, build commands, produce records, wait. */
final class Streams {

    private Streams() {
    }

    static List<String> stream(EntityManager em, String orderId) {
        return QuarkusTransaction.requiringNew().call(() -> em
                .createNativeQuery("select version || ':' || type from orders.events where stream_id = ?1 order by version")
                .setParameter(1, orderId).getResultList().stream().map(Object::toString).toList());
    }

    static List<String> outbox(EntityManager em, String orderId) {
        return QuarkusTransaction.requiringNew().call(() -> em
                .createNativeQuery("select type || '->' || topic from orders.outboxevent where aggregateid = ?1 order by \"timestamp\"")
                .setParameter(1, orderId).getResultList().stream().map(Object::toString).toList());
    }

    static Command.Place place(String id) {
        return new Command.Place(id, "CU-1", "SKU-1", 1, new BigDecimal("25.00"));
    }

    static Command.MarkPaid pay(String id) {
        return new Command.MarkPaid(id, "P-" + id, new BigDecimal("25.00"));
    }

    static Command.MarkShipped ship(String id) {
        return new Command.MarkShipped(id, "S-" + id, "FASTLINE");
    }

    static Command.Cancel cancel(String id) {
        return new Command.Cancel(id, "customer");
    }

    static String payment(String paymentId, String orderId, String amount) {
        return "{\"payment\":{\"id\":\"" + paymentId + "\",\"orderId\":\"" + orderId + "\",\"amount\":" + amount
                + ",\"status\":\"COMPLETED\"}}";
    }

    static String shipment(String orderId) {
        return "{\"shipment\":{\"id\":\"S-" + orderId + "\",\"orderId\":\"" + orderId
                + "\",\"carrier\":\"FASTLINE\",\"trackingNumber\":\"FL-" + orderId + "\",\"status\":\"SCHEDULED\"}}";
    }

    static void send(String bootstrap, String topic, String key, String json) throws Exception {
        try (var producer = new KafkaProducer<>(Map.<String, Object>of("bootstrap.servers", bootstrap),
                new StringSerializer(), new StringSerializer())) {
            producer.send(new ProducerRecord<>(topic, key, json)).get();
        }
    }

    /** Records on partition 0 that the group has not committed past. */
    static long lag(String bootstrap, String group, String topic) throws Exception {
        try (var admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", bootstrap))) {
            var partition = new TopicPartition(topic, 0);
            var committed = admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get().get(partition);
            long end = admin.listOffsets(Map.of(partition, OffsetSpec.latest())).all().get().get(partition).offset();
            return end - (committed == null ? 0 : committed.offset());
        }
    }

    static void await(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < end) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("condition not met within 60 seconds");
    }
}
