package com.patterncatalyst.shipping.deadletter;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import com.patterncatalyst.shipping.Topics;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.persistence.EntityManager;

/** Test helpers: produce raw records, read the dead letter topic, read stock, hold a row lock. */
final class Records {

    private Records() {
    }

    static String valid(String id, String sku, int quantity) {
        return "{\"orderId\":\"" + id + "\",\"customerId\":\"CU-77\",\"itemSku\":\"" + sku + "\",\"quantity\":" + quantity + "}";
    }

    /** Sends one record and returns its offset in the source topic. */
    static long send(String bootstrap, String topic, String id, String value) throws Exception {
        try (var producer = new KafkaProducer<>(Map.<String, Object>of("bootstrap.servers", bootstrap),
                new StringSerializer(), new ByteArraySerializer())) {
            return producer.send(new ProducerRecord<>(topic, id, value.getBytes(StandardCharsets.UTF_8))).get().offset();
        }
    }

    /** The dead letters whose value mentions the order id, read from the beginning for up to the given time. */
    static List<ConsumerRecord<String, byte[]>> deadLetters(String bootstrap, String id, Duration wait) {
        var found = new ArrayList<ConsumerRecord<String, byte[]>>();
        var props = Map.<String, Object>of("bootstrap.servers", bootstrap,
                "group.id", "test-" + UUID.randomUUID(), "auto.offset.reset", "earliest");
        try (var consumer = new KafkaConsumer<>(props, new StringDeserializer(), new ByteArrayDeserializer())) {
            consumer.subscribe(List.of(Topics.ORDERS_PLACED_DLQ));
            long end = System.nanoTime() + wait.toNanos();
            while (System.nanoTime() < end) {
                consumer.poll(Duration.ofMillis(500)).forEach(r -> {
                    if (new String(r.value(), StandardCharsets.UTF_8).contains(id)) {
                        found.add(r);
                    }
                });
            }
        }
        return found;
    }

    static String header(ConsumerRecord<String, byte[]> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    static int stock(EntityManager em, String sku) {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) em
                .createNativeQuery("select quantity_on_hand from inventory.inventory_item where sku = ?1")
                .setParameter(1, sku).getSingleResult()).intValue());
    }

    /** Holds a row lock on the SKU for the given time in another transaction; returns once the lock is held. */
    static void lockFor(EntityManager em, String sku, Duration hold) throws InterruptedException {
        var held = new CountDownLatch(1);
        Thread.ofVirtual().start(() -> QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("select 1 from inventory.inventory_item where sku = ?1 for update")
                    .setParameter(1, sku).getSingleResult();
            held.countDown();
            try {
                Thread.sleep(hold);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        held.await();
    }
}
