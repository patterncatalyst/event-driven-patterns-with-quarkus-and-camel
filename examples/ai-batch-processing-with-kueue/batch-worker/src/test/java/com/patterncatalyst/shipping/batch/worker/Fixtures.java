package com.patterncatalyst.shipping.batch.worker;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.persistence.EntityManager;

/** The window 12:00 to 13:00 with three partitions of input, seeded once per test class. */
final class Fixtures {

    static final Instant T0 = Instant.parse("2026-06-01T12:00:00Z");
    static final Instant T1 = Instant.parse("2026-06-01T13:00:00Z");
    static final String SCHEDULED = "edp.shipping.scheduled";
    static final String DELIVERED = "edp.shipping.delivered";

    private Fixtures() {
    }

    static String bootstrap() {
        return ConfigProvider.getConfig().getValue("kafka.bootstrap.servers", String.class);
    }

    /**
     * Per partition p: one scheduled record before the window, three inside (A and C FASTLINE, B AIRCO) and, except in
     * partition 2, one after it. Delivered: A and B inside, none after; partition 1 has only a record before the window.
     */
    static void seed(String tag) throws Exception {
        try (var producer = new KafkaProducer<>(Map.<String, Object>of("bootstrap.servers", bootstrap()),
                new StringSerializer(), new StringSerializer())) {
            for (int p = 0; p < 3; p++) {
                String id = "O-" + tag + p;
                send(producer, SCHEDULED, p, T0.minusSeconds(60), id + "X", "FASTLINE");
                send(producer, SCHEDULED, p, T0.plusSeconds(60), id + "A", "FASTLINE");
                send(producer, SCHEDULED, p, T0.plusSeconds(120), id + "B", "AIRCO");
                send(producer, SCHEDULED, p, T0.plusSeconds(180), id + "C", "FASTLINE");
                if (p != 2) {
                    send(producer, SCHEDULED, p, T1.plusSeconds(60), id + "D", "FASTLINE");
                }
                if (p == 1) {
                    send(producer, DELIVERED, p, T0.minusSeconds(30), id + "X", "FASTLINE");
                } else {
                    send(producer, DELIVERED, p, T0.plusSeconds(600), id + "A", "FASTLINE");
                    send(producer, DELIVERED, p, T0.plusSeconds(660), id + "B", "AIRCO");
                }
            }
            producer.flush();
        }
    }

    private static void send(KafkaProducer<String, String> producer, String topic, int partition, Instant at, String order,
            String carrier) throws Exception {
        String json = """
                {"shipment":{"id":"S-%s","orderId":"%s","carrier":"%s","status":"SCHEDULED"},"occurredAt":"%s"}"""
                .formatted(order, order, carrier, at);
        producer.send(new ProducerRecord<>(topic, partition, at.toEpochMilli(), order, json)).get();
    }

    /** FAILED keeps the row out of the one-live-run-per-window index, so every test has its own run of the same window. */
    static String newRun(EntityManager em) {
        String id = UUID.randomUUID().toString().substring(0, 10);
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                insert into reconciliation.run (id, window_from, window_to, status, created_at)
                values (?1, ?2, ?3, 'FAILED', now())""").setParameter(1, id)
                .setParameter(2, java.sql.Timestamp.from(T0)).setParameter(3, java.sql.Timestamp.from(T1))
                .executeUpdate());
        return id;
    }

    static void env(String runId, String... pairs) {
        TestEnv.VALUES.clear();
        TestEnv.VALUES.put("RUN_ID", runId);
        for (int i = 0; i < pairs.length; i += 2) {
            TestEnv.VALUES.put(pairs[i], pairs[i + 1]);
        }
    }

    /** Everything on the output topic, key to value. */
    static Map<String, String> readAll(String topic) {
        Map<String, Object> config = new HashMap<>();
        config.put("bootstrap.servers", bootstrap());
        config.put("group.id", "test-" + UUID.randomUUID());
        config.put("auto.offset.reset", "earliest");
        Map<String, String> out = new HashMap<>();
        try (var c = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            c.subscribe(List.of(topic));
            long stop = System.nanoTime() + Duration.ofSeconds(15).toNanos();
            while (System.nanoTime() < stop) {
                c.poll(Duration.ofMillis(300)).forEach(r -> out.put(r.key(), r.value()));
                if (!out.isEmpty()) {
                    c.poll(Duration.ofMillis(500)).forEach(r -> out.put(r.key(), r.value()));
                    break;
                }
            }
        }
        return out;
    }
}
