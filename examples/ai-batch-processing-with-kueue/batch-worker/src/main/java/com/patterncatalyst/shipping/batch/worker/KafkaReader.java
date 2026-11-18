package com.patterncatalyst.shipping.batch.worker;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.Carrier;

import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Bounded reads. A consumer group cannot read a fixed range, so this assigns the partition, seeks to the recorded start
 * and stops at the recorded end. The connection settings are the ones Quarkus injects as default-kafka-broker.
 */
@ApplicationScoped
public class KafkaReader {

    private static final Duration DEADLINE = Duration.ofSeconds(90);

    @Inject
    @Identifier("default-kafka-broker")
    Map<String, Object> broker;

    @Inject
    ObjectMapper json;

    KafkaConsumer<String, String> consumer() {
        Map<String, Object> config = new HashMap<>(broker);
        config.put("enable.auto.commit", "false");
        config.put("auto.offset.reset", "none");
        return new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer());
    }

    /** First offset at or after each time, per partition; the end offset when no record is that late. */
    Map<Integer, long[]> offsetsAt(String topic, Instant... times) {
        try (var c = consumer()) {
            List<TopicPartition> partitions = c.partitionsFor(topic).stream()
                    .map(p -> new TopicPartition(topic, p.partition())).toList();
            Map<TopicPartition, Long> ends = c.endOffsets(partitions);
            Map<Integer, long[]> out = new HashMap<>();
            partitions.forEach(tp -> out.put(tp.partition(), new long[times.length]));
            for (int i = 0; i < times.length; i++) {
                Map<TopicPartition, Long> query = new HashMap<>();
                final int index = i;
                partitions.forEach(tp -> query.put(tp, times[index].toEpochMilli()));
                var found = c.offsetsForTimes(query);
                for (TopicPartition tp : partitions) {
                    var at = found.get(tp);
                    out.get(tp.partition())[i] = at == null ? ends.get(tp) : at.offset();
                }
            }
            return out;
        }
    }

    /** Reads [from, to) of one partition and hands each record to the sink; returns how many it read. */
    long read(String topic, int partition, long from, long to, Consumer<ConsumerRecord<String, String>> sink) {
        if (from >= to) {
            return 0;
        }
        TopicPartition tp = new TopicPartition(topic, partition);
        long count = 0;
        long stop = System.nanoTime() + DEADLINE.toNanos();
        try (var c = consumer()) {
            c.assign(Set.of(tp));
            c.seek(tp, from);
            while (c.position(tp) < to) {
                if (System.nanoTime() > stop) {
                    throw new IllegalStateException("timed out reading " + topic + "-" + partition + " at "
                            + c.position(tp) + " of " + to);
                }
                for (ConsumerRecord<String, String> r : c.poll(Duration.ofMillis(500)).records(tp)) {
                    if (r.offset() < to) {
                        sink.accept(r);
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /** The order id is the record key; the carrier is in the shipment of the payload. */
    String orderId(ConsumerRecord<String, String> r) {
        if (r.key() != null) {
            return r.key();
        }
        return tree(r).path("shipment").path("orderId").asText(null);
    }

    Carrier carrier(ConsumerRecord<String, String> r) {
        JsonNode n = tree(r);
        String name = n.path("shipment").path("carrier").asText(n.path("carrier").asText(null));
        return name == null ? null : Carrier.valueOf(name);
    }

    private JsonNode tree(ConsumerRecord<String, String> r) {
        try {
            return json.readTree(r.value());
        } catch (Exception e) {
            throw new IllegalStateException("not JSON at " + r.topic() + "-" + r.partition() + "@" + r.offset(), e);
        }
    }
}
