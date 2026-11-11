package com.patterncatalyst.shipping.tuning;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;

import com.patterncatalyst.shipping.Topics;

import io.quarkus.narayana.jta.QuarkusTransaction;

/** Test helpers: seed records into explicit partitions, and read the stored rows back. */
final class Fixtures {

    static final List<String> TOPICS = List.of(Topics.ORDERS_PLACED, Topics.PAYMENTS_PROCESSED, Topics.SHIPPING_SCHEDULED);

    private Fixtures() {
    }

    /** Sends {@code count} records, spread over every partition of the three topics, with the given record timestamp. */
    static void seed(String bootstrapServers, int count, long timestamp) throws ExecutionException, InterruptedException {
        try (var producer = new KafkaProducer<>(Map.<String, Object>of("bootstrap.servers", bootstrapServers),
                new StringSerializer(), new StringSerializer())) {
            for (int i = 0; i < count; i++) {
                String topic = TOPICS.get(i % 3);
                int partition = (i / 3) % 3;
                String json = """
                        {"orderId":"O-%d","customerId":"C-%d","eventType":"Test"}""".formatted(i, i % 7);
                producer.send(new ProducerRecord<>(topic, partition, timestamp, null, json)).get();
            }
        }
    }

    static long rows(String group) {
        return QuarkusTransaction.requiringNew().call(() -> NotificationRow.count("groupId", group));
    }

    static long rowsWithAttempts(String group, int attempts) {
        return QuarkusTransaction.requiringNew().call(() ->
                NotificationRow.count("groupId = ?1 and attempts = ?2", group, attempts));
    }

    static long distinctConsumers(String group) {
        return QuarkusTransaction.requiringNew().call(() ->
                NotificationRow.<NotificationRow>list("groupId", group).stream().map(r -> r.consumer).distinct().count());
    }

    /** Waits until the group has stored {@code expected} rows, then keeps waiting to show no more arrive. */
    static void awaitExactly(String group, long expected) {
        Awaitility.await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(200))
                .until(() -> rows(group) >= expected);
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
