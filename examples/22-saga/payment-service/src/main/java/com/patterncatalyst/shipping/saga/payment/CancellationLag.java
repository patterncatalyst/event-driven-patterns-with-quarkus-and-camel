package com.patterncatalyst.shipping.saga.payment;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.common.TopicPartition;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.patterncatalyst.shipping.Topics;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

/**
 * Whether the cancellation consumer still has records to read: the group's committed offset against the end of the
 * topic. The cost is one metadata call, not a read of the topic. A group that has committed nothing starts at the
 * earliest offset, as its consumer does.
 */
@ApplicationScoped
@Named("cancellationLag")
public class CancellationLag {

    static final String GROUP = "payment-cancelled";

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String brokers;

    private Admin admin;

    @PostConstruct
    void open() {
        admin = Admin.create(Map.<String, Object>of("bootstrap.servers", brokers));
    }

    @PreDestroy
    void close() {
        admin.close();
    }

    public boolean behind() throws Exception {
        List<TopicPartition> partitions = admin.describeTopics(List.of(Topics.ORDERS_CANCELLED)).allTopicNames().get()
                .get(Topics.ORDERS_CANCELLED).partitions().stream()
                .map(info -> new TopicPartition(Topics.ORDERS_CANCELLED, info.partition())).toList();
        var committed = admin.listConsumerGroupOffsets(GROUP).partitionsToOffsetAndMetadata().get();
        var end = admin.listOffsets(spec(partitions, OffsetSpec.latest())).all().get();
        var start = admin.listOffsets(spec(partitions, OffsetSpec.earliest())).all().get();
        return partitions.stream().anyMatch(p -> {
            long position = committed.containsKey(p) ? committed.get(p).offset() : start.get(p).offset();
            return position < end.get(p).offset();
        });
    }

    private static Map<TopicPartition, OffsetSpec> spec(List<TopicPartition> partitions, OffsetSpec spec) {
        return partitions.stream().collect(Collectors.toMap(p -> p, p -> spec));
    }
}
