package com.patterncatalyst.shipping.queues;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;

import com.patterncatalyst.shipping.Topics;

import io.quarkus.narayana.jta.QuarkusTransaction;

/** Test helpers: publish jobs, tune the share group, read the delivery log. */
final class Jobs {

    private Jobs() {
    }

    static String json(String orderId, String customerId) {
        return """
                {"orderId":"%s","order":{"id":"%s","customerId":"%s","itemSku":"SKU-1","quantity":1,"amount":10.00}}
                """.formatted(orderId, orderId, customerId).strip();
    }

    /** Sets the share group's settings on the group resource, before any job is published. */
    static void configureShareGroup(String bootstrapServers, String group, long lockMs) throws Exception {
        try (Admin admin = Admin.create(Map.<String, Object>of("bootstrap.servers", bootstrapServers))) {
            var resource = new ConfigResource(ConfigResource.Type.GROUP, group);
            admin.incrementalAlterConfigs(Map.of(resource, List.of(
                    new AlterConfigOp(new ConfigEntry("share.auto.offset.reset", "earliest"), AlterConfigOp.OpType.SET),
                    new AlterConfigOp(new ConfigEntry("share.record.lock.duration.ms", Long.toString(lockMs)),
                            AlterConfigOp.OpType.SET)))).all().get();
        }
    }

    /** Waits until the share group has the expected number of members, so no job is published to an empty group. */
    static void awaitShareMembers(String bootstrapServers, String group, int members) {
        try (Admin admin = Admin.create(Map.<String, Object>of("bootstrap.servers", bootstrapServers))) {
            Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(250)).until(() ->
                    admin.describeShareGroups(List.of(group)).all().get().get(group).members().size() == members);
        }
    }

    static void send(String bootstrapServers, String orderId, String customerId) {
        try (var producer = new KafkaProducer<>(Map.<String, Object>of("bootstrap.servers", bootstrapServers),
                new StringSerializer(), new StringSerializer())) {
            producer.send(new ProducerRecord<>(Topics.ORDERS_PLACED, orderId, json(orderId, customerId)));
        }
    }

    static List<DeliveryRow> rows(String mode, String orderId) {
        return QuarkusTransaction.requiringNew().call(() ->
                DeliveryRow.<DeliveryRow>list("mode = ?1 and orderId = ?2 order by id", mode, orderId));
    }

    static List<String> outcomes(String mode, String orderId) {
        return rows(mode, orderId).stream().map(r -> r.outcome).toList();
    }

    static List<Integer> deliveryCounts(String mode, String orderId) {
        return rows(mode, orderId).stream().map(r -> r.deliveryCount).toList();
    }

    static void awaitRows(String mode, String orderId, int count) {
        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
                .until(() -> rows(mode, orderId).size() >= count);
    }

    /** Waits the full period, so a row that arrives late is in the log before the test asserts on it. */
    static void settle(Duration period) {
        Awaitility.await().pollDelay(period).atMost(period.plus(Duration.ofSeconds(5))).until(() -> true);
    }
}
