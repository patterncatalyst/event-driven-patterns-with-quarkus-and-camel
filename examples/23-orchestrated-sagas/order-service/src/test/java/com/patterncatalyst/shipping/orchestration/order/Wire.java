package com.patterncatalyst.shipping.orchestration.order;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

/** Test helpers: play a participant by hand, and read what an orchestrator sent. */
final class Wire {

    private Wire() {
    }

    static void send(String bootstrap, String topic, String orderId, String json, Map<String, String> headers) throws Exception {
        try (var producer = new KafkaProducer<>(Map.<String, Object>of("bootstrap.servers", bootstrap),
                new StringSerializer(), new StringSerializer())) {
            var record = new ProducerRecord<>(topic, orderId, json);
            headers.forEach((k, v) -> record.headers().add(k, v.getBytes(StandardCharsets.UTF_8)));
            producer.send(record).get();
        }
    }

    /** A reply as a participant sends it: the saga header and the instance id of the command, CloudEvent attributes always, and a payload. */
    static void reply(String bootstrap, String topic, String type, String orderId, String outcome, ConsumerRecord<String, String> command) throws Exception {
        var headers = new java.util.HashMap<String, String>();
        headers.put("type", type);
        headers.put("orderId", orderId);
        String saga = header(command, "Long-Running-Action");
        if (saga != null) {
            headers.put("Long-Running-Action", saga);
        }
        headers.put("ce_specversion", "1.0");
        headers.put("ce_id", UUID.randomUUID().toString());
        headers.put("ce_source", "/test");
        headers.put("ce_type", type);
        headers.put("content-type", "application/json");
        String instance = header(command, "flowinstanceid");
        if (instance != null) {
            headers.put("ce_flowinstanceid", instance);
        }
        send(bootstrap, topic, orderId, "{\"orderId\":\"" + orderId + "\",\"outcome\":\"" + outcome + "\"}", headers);
    }

    /** Waits for at least {@code count} records of one order on a one-partition topic and returns them. */
    static List<ConsumerRecord<String, String>> records(String bootstrap, String topic, String orderId, int count, Duration wait) {
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        try (var consumer = new KafkaConsumer<>(Map.<String, Object>of(
                "bootstrap.servers", bootstrap, "group.id", "test-" + UUID.randomUUID(), "auto.offset.reset", "earliest"),
                new StringDeserializer(), new StringDeserializer())) {
            consumer.assign(List.of(new TopicPartition(topic, 0)));
            long end = System.nanoTime() + wait.toNanos();
            while (System.nanoTime() < end && found.size() < count) {
                for (var r : consumer.poll(Duration.ofMillis(500))) {
                    if (orderId.equals(r.key())) {
                        found.add(r);
                    }
                }
            }
        }
        return found;
    }

    static ConsumerRecord<String, String> command(String bootstrap, String topic, String orderId, String type) {
        return org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(30)).pollDelay(Duration.ZERO)
                .until(() -> records(bootstrap, topic, orderId, 1, Duration.ofSeconds(2)).stream()
                        .filter(r -> type.equals(header(r, "type"))).findFirst(), java.util.Optional::isPresent)
                .orElseThrow();
    }

    static String header(ConsumerRecord<String, String> record, String name) {
        var h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }
}
