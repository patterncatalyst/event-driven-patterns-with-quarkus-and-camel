package com.patterncatalyst.shipping.join;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.streams.KafkaStreams;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.Topics;

import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

/**
 * A join needs both topics to have the same number of partitions. With three and two, Kafka Streams starts and reports
 * healthy, and the join matches only the orders whose two records happen to land on the same partition number. The
 * Camel route reads both topics in one consumer and matches every order.
 */
@QuarkusTest
@TestProfile(MismatchedPartitionsProfile.class)
class CopartitioningTest {

    private static final int ORDERS = 30;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrap;

    @Inject
    KafkaStreams streams;

    @Test
    void streamsMatchesSomeOrdersAndCamelMatchesAll() throws Exception {
        try (var admin = Admin.create(Map.<String, Object>of("bootstrap.servers", bootstrap))) {
            var topics = admin.describeTopics(List.of(Topics.INVENTORY_RESERVED, Topics.PAYMENTS_PROCESSED))
                    .allTopicNames().get();
            int reserved = topics.get(Topics.INVENTORY_RESERVED).partitions().size();
            int paid = topics.get(Topics.PAYMENTS_PROCESSED).partitions().size();
            Log.info("COPARTITION partitions: " + Topics.INVENTORY_RESERVED + "=" + reserved + " "
                    + Topics.PAYMENTS_PROCESSED + "=" + paid);
            assertEquals(3, reserved);
            assertEquals(2, paid);
        }
        for (int i = 0; i < ORDERS; i++) {
            Kafka.reserve(bootstrap, "O-P%02d".formatted(i), "09:00");
        }
        for (int i = 0; i < ORDERS; i++) {
            Kafka.pay(bootstrap, "O-P%02d".formatted(i), "09:01");
        }
        Kafka.await(() -> unchecked(() -> ready(Topics.SHIPPING_ORDER_READINESS_CAMEL) == ORDERS));
        Kafka.await(() -> unchecked(() -> streamsHasReadEverything()));
        Thread.sleep(3000);
        long camel = ready(Topics.SHIPPING_ORDER_READINESS_CAMEL);
        long streamsReady = ready(Topics.SHIPPING_ORDER_READINESS);
        Log.info("COPARTITION streams state " + streams.state() + ", threads "
                + streams.metadataForLocalThreads().stream()
                        .map(t -> t.threadName() + " active=" + t.activeTasks()).toList()
                + ", matched " + streamsReady + " of " + ORDERS);
        assertEquals(ORDERS, camel);
        assertTrue(streamsReady > 0 && streamsReady < ORDERS, "Streams matched " + streamsReady + " of " + ORDERS);
    }

    /** The Streams group has committed the end of every partition of both input topics. */
    private boolean streamsHasReadEverything() throws Exception {
        try (var admin = Admin.create(Map.<String, Object>of("bootstrap.servers", bootstrap))) {
            var committed = admin.listConsumerGroupOffsets("shipping-readiness")
                    .partitionsToOffsetAndMetadata().get();
            var ends = admin.listOffsets(committed.keySet().stream()
                    .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()))).all().get();
            return committed.size() == 5 && committed.entrySet().stream()
                    .allMatch(e -> e.getValue().offset() >= ends.get(e.getKey()).offset());
        }
    }

    private long ready(String topic) throws Exception {
        return Kafka.readiness(bootstrap, topic).stream()
                .filter(r -> r.orderId().startsWith("O-P") && r.ready()).count();
    }

    private static boolean unchecked(java.util.concurrent.Callable<Boolean> check) {
        try {
            return check.call();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
