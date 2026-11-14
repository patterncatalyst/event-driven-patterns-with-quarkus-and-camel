package com.patterncatalyst.shipping.decisions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.ConfigProvider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** Plain Kafka clients for the tests: produce a record, read a one-partition topic from the start, read a committed offset. */
final class Kafka {

    static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private Kafka() {
    }

    private static String bootstrap() {
        return ConfigProvider.getConfig().getValue("kafka.bootstrap.servers", String.class);
    }

    static long send(String topic, String key, Object value) throws Exception {
        var props = Map.<String, Object>of("bootstrap.servers", bootstrap());
        try (var producer = new KafkaProducer<>(props, new StringSerializer(), new StringSerializer())) {
            return producer.send(new ProducerRecord<>(topic, key, MAPPER.writeValueAsString(value))).get().offset();
        }
    }

    static List<JsonNode> read(String topic) {
        var props = new HashMap<String, Object>();
        props.put("bootstrap.servers", bootstrap());
        props.put("group.id", "test-reader-" + System.nanoTime());
        try (var consumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
            var partition = new TopicPartition(topic, 0);
            consumer.assign(List.of(partition));
            consumer.seekToBeginning(List.of(partition));
            long end = consumer.endOffsets(List.of(partition)).get(partition);
            var records = new ArrayList<JsonNode>();
            while (consumer.position(partition) < end) {
                consumer.poll(Duration.ofMillis(200)).forEach(r -> {
                    try {
                        records.add(MAPPER.readTree(r.value()));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });
            }
            return records;
        }
    }

    static JsonNode find(String topic, String exceptionId) {
        return read(topic).stream().filter(n -> exceptionId.equals(n.get("exceptionId").asText())).findFirst().orElse(null);
    }

    static long committed(String group, String topic) throws Exception {
        try (var admin = Admin.create(Map.<String, Object>of("bootstrap.servers", bootstrap()))) {
            var offsets = admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get();
            var meta = offsets.get(new TopicPartition(topic, 0));
            return meta == null ? 0 : meta.offset();
        }
    }
}
