package com.patterncatalyst.shipping.analysis;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Plain Kafka clients for {@link StabilityHarnessIT}. A {@code @QuarkusIntegrationTest} is a black box with no CDI
 * container of its own, so unlike {@link Kafka} this takes the bootstrap address as a parameter instead of reading
 * it from MicroProfile Config.
 */
final class LiveKafka {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private LiveKafka() {
    }

    static void send(String bootstrapServers, String topic, String key, Object value) throws Exception {
        var props = Map.<String, Object>of("bootstrap.servers", bootstrapServers);
        try (var producer = new KafkaProducer<>(props, new StringSerializer(), new StringSerializer())) {
            producer.send(new ProducerRecord<>(topic, key, MAPPER.writeValueAsString(value))).get();
        }
    }

    static JsonNode find(String bootstrapServers, String topic, String exceptionId) {
        var props = new HashMap<String, Object>();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("group.id", "live-reader-" + System.nanoTime());
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
            return records.stream().filter(n -> exceptionId.equals(n.get("exceptionId").asText())).reduce((a, b) -> b).orElse(null);
        }
    }
}
