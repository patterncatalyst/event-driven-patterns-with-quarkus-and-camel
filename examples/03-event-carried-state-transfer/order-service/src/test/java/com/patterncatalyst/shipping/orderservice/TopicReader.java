package com.patterncatalyst.shipping.orderservice;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

/** Reads a topic from the beginning with a throwaway consumer group. */
final class TopicReader {

    private TopicReader() {
    }

    static List<ConsumerRecord<String, String>> read(String bootstrap, String topic, Duration wait) {
        var props = Map.<String, Object>of("bootstrap.servers", bootstrap, "group.id", "test-" + UUID.randomUUID(),
                "auto.offset.reset", "earliest");
        var records = new ArrayList<ConsumerRecord<String, String>>();
        try (var consumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(topic));
            long end = System.nanoTime() + wait.toNanos();
            while (System.nanoTime() < end) {
                consumer.poll(Duration.ofMillis(300)).forEach(records::add);
            }
        }
        return records;
    }

    static List<ConsumerRecord<String, String>> keyed(String bootstrap, String topic, String key) {
        return read(bootstrap, topic, Duration.ofSeconds(4)).stream().filter(r -> key.equals(r.key())).toList();
    }
}
