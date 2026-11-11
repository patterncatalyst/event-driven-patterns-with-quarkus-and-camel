package com.patterncatalyst.shipping.ordering;

import static io.restassured.RestAssured.given;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.utils.Utils;

import com.patterncatalyst.shipping.Topics;

/** Test helpers: publish an order, read the topic back, compute the partition Kafka's own hash gives a key. */
final class Placement {

    static final int PARTITIONS = 3;

    private Placement() {
    }

    /** The partition the default producer partitioner picks for a key: murmur2, made positive, modulo. */
    static int expected(String key) {
        return Utils.toPositive(Utils.murmur2(key.getBytes(StandardCharsets.UTF_8))) % PARTITIONS;
    }

    static List<Receipt> publish(String orderId, String carrier) {
        var request = given();
        if (carrier != null) {
            request = request.queryParam("carrier", carrier);
        }
        return List.of(request.post("/shipments/" + orderId).then().statusCode(200)
                .extract().as(Receipt[].class));
    }

    static List<ConsumerRecord<String, String>> readTopic(String bootstrapServers, String orderIdPrefix) {
        var props = Map.<String, Object>of(
                "bootstrap.servers", bootstrapServers,
                "group.id", "test-" + UUID.randomUUID(),
                "auto.offset.reset", "earliest");
        var records = new ArrayList<ConsumerRecord<String, String>>();
        try (var consumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(Topics.SHIPPING_SHIPMENTS));
            long end = System.nanoTime() + Duration.ofSeconds(6).toNanos();
            while (System.nanoTime() < end) {
                consumer.poll(Duration.ofMillis(500)).forEach(r -> {
                    if (r.value().contains("\"orderId\":\"" + orderIdPrefix)) {
                        records.add(r);
                    }
                });
            }
        }
        return records;
    }
}
