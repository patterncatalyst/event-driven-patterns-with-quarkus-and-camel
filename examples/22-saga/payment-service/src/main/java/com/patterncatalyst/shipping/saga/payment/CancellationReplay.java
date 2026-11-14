package com.patterncatalyst.shipping.saga.payment;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.Topics;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Reads OrderCancelled from the beginning of the topic to its current end and records each one. Repeating it is harmless. */
@ApplicationScoped
public class CancellationReplay {

    private static final Logger LOG = Logger.getLogger(CancellationReplay.class);

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String brokers;

    @Inject
    PaymentService payments;

    @Inject
    ObjectMapper json;

    public int replay() throws Exception {
        var props = Map.<String, Object>of("bootstrap.servers", brokers, "enable.auto.commit", false);
        int recorded = 0;
        try (var consumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
            List<TopicPartition> partitions = consumer.partitionsFor(Topics.ORDERS_CANCELLED).stream()
                    .map(info -> new TopicPartition(info.topic(), info.partition())).toList();
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            Map<TopicPartition, Long> end = consumer.endOffsets(partitions);
            while (partitions.stream().anyMatch(p -> consumer.position(p) < end.get(p))) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    payments.recordCancelled(json.readValue(record.value(), OrderCancelled.class));
                    recorded++;
                }
            }
        }
        LOG.infof("read %d cancellations before taking payments", recorded);
        return recorded;
    }
}
