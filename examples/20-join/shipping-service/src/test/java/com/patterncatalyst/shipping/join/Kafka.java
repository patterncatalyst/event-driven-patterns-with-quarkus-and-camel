package com.patterncatalyst.shipping.join;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.patterncatalyst.shipping.Payment;
import com.patterncatalyst.shipping.PaymentStatus;
import com.patterncatalyst.shipping.Topics;

/** Plain Kafka clients for the tests: producers for the two input topics and a reader of the two outputs. */
final class Kafka {

    private Kafka() {
    }

    static void reserve(String bootstrap, String orderId, String time) throws Exception {
        send(bootstrap, Topics.INVENTORY_RESERVED, orderId,
                Times.mapper().writeValueAsString(new InventoryReserved(orderId, "SKU-20", 1, Times.at(time))));
    }

    static void pay(String bootstrap, String orderId, String time) throws Exception {
        send(bootstrap, Topics.PAYMENTS_PROCESSED, orderId, Times.mapper().writeValueAsString(new PaymentProcessed(
                new Payment("P-" + orderId, orderId, new java.math.BigDecimal("25.00"), PaymentStatus.COMPLETED), Times.at(time))));
    }

    static void send(String bootstrap, String topic, String key, String json) throws Exception {
        try (var producer = new KafkaProducer<>(Map.<String, Object>of("bootstrap.servers", bootstrap),
                new StringSerializer(), new StringSerializer())) {
            producer.send(new ProducerRecord<>(topic, key, json)).get(10, TimeUnit.SECONDS);
        }
    }

    /** Every Readiness record on a topic, read from the beginning by a consumer of its own. */
    static List<Readiness> readiness(String bootstrap, String topic) throws JsonProcessingException {
        List<Readiness> seen = new ArrayList<>();
        try (var consumer = new KafkaConsumer<>(Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap,
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(topic));
            long end = System.nanoTime() + Duration.ofSeconds(3).toNanos();
            while (System.nanoTime() < end) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    seen.add(Times.mapper().readValue(record.value(), Readiness.class));
                }
            }
        }
        return seen;
    }

    static List<Readiness> forOrder(List<Readiness> all, String orderId) {
        return all.stream().filter(r -> r.orderId().equals(orderId)).toList();
    }

    static void await(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < end) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(250);
        }
        throw new AssertionError("condition not met within 60 seconds");
    }
}
