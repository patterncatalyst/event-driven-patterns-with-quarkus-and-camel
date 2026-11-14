package com.patterncatalyst.shipping.enrich;

import java.time.Duration;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.Carrier;

/** Plain Kafka clients for the tests: produce a record, read a topic from the start, read a group's committed offset. */
final class Kafka {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Kafka() {
    }

    private static String bootstrap() {
        return ConfigProvider.getConfig().getValue("kafka.bootstrap.servers", String.class);
    }

    /** Sends one record and returns its offset; a null value is a tombstone. */
    static long send(String topic, String key, Object value) throws Exception {
        String json = value == null ? null : MAPPER.writeValueAsString(value);
        var props = Map.<String, Object>of("bootstrap.servers", bootstrap());
        try (var producer = new KafkaProducer<>(props, new StringSerializer(), new StringSerializer())) {
            return producer.send(new ProducerRecord<>(topic, key, json)).get().offset();
        }
    }

    static long schedule(String topic, String orderId, Carrier carrier) throws Exception {
        return send(topic, orderId, Samples.scheduled(orderId, carrier));
    }

    /** The last value per key on a one-partition topic. */
    static Map<String, String> read(String topic) {
        var props = new HashMap<String, Object>();
        props.put("bootstrap.servers", bootstrap());
        props.put("group.id", "test-reader-" + System.nanoTime());
        try (var consumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
            var partition = new TopicPartition(topic, 0);
            consumer.assign(List.of(partition));
            consumer.seekToBeginning(List.of(partition));
            long end = consumer.endOffsets(List.of(partition)).get(partition);
            var records = new HashMap<String, String>();
            while (consumer.position(partition) < end) {
                consumer.poll(Duration.ofMillis(200)).forEach(r -> records.put(r.key(), r.value()));
            }
            return records;
        }
    }

    static long committed(String group, String topic) throws Exception {
        try (var admin = Admin.create(Map.<String, Object>of("bootstrap.servers", bootstrap()))) {
            var offsets = admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get();
            var meta = offsets.get(new TopicPartition(topic, 0));
            return meta == null ? 0 : meta.offset();
        }
    }
}
