package com.patterncatalyst.shipping.analysis;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Reads a topic from the first offset to the end it had when the read started, with a consumer that has no group
 * state, reused unchanged from chapter 24. */
final class RecordedTopic {

    private RecordedTopic() {
    }

    static <T> List<T> read(String brokers, String topic, Class<T> type, ObjectMapper mapper) {
        var config = Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers,
                ConsumerConfig.GROUP_ID_CONFIG, "replay-" + UUID.randomUUID(),
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            var partitions = consumer.partitionsFor(topic).stream()
                    .map(p -> new TopicPartition(topic, p.partition())).toList();
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            var ends = consumer.endOffsets(partitions);
            var records = new ArrayList<T>();
            while (partitions.stream().anyMatch(p -> consumer.position(p) < ends.get(p))) {
                for (var record : consumer.poll(Duration.ofMillis(200))) {
                    records.add(mapper.readValue(record.value(), type));
                }
            }
            return records;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
