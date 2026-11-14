package com.patterncatalyst.shipping.orchestration.inventory;

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
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

/** Test helpers: send a command with a type header, read the records of a reply topic for one order. */
final class Records {

    private Records() {
    }

    static void command(String bootstrap, String topic, String type, String orderId, String json, Map<String, String> headers) throws Exception {
        try (var producer = new KafkaProducer<>(Map.<String, Object>of("bootstrap.servers", bootstrap),
                new StringSerializer(), new StringSerializer())) {
            var record = new ProducerRecord<>(topic, orderId, json);
            record.headers().add("type", type.getBytes(StandardCharsets.UTF_8));
            headers.forEach((k, v) -> record.headers().add(k, v.getBytes(StandardCharsets.UTF_8)));
            producer.send(record).get();
        }
    }

    static List<ConsumerRecord<String, String>> replies(String bootstrap, String topic, String orderId, Duration wait) {
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        try (var consumer = new KafkaConsumer<>(Map.<String, Object>of(
                "bootstrap.servers", bootstrap, "group.id", "test-" + UUID.randomUUID(), "auto.offset.reset", "earliest"),
                new StringDeserializer(), new StringDeserializer())) {
            var tp = new org.apache.kafka.common.TopicPartition(topic, 0);
            consumer.assign(List.of(tp));
            long end = System.nanoTime() + wait.toNanos();
            while (System.nanoTime() < end && found.isEmpty()) {
                for (var r : consumer.poll(Duration.ofMillis(300))) {
                    if (orderId.equals(r.key())) {
                        found.add(r);
                    }
                }
            }
        }
        return found;
    }

    static String header(ConsumerRecord<String, String> record, String name) {
        var h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }
}
