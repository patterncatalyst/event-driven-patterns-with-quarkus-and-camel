package com.patterncatalyst.shipping.stackcheck;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.patterncatalyst.shipping.Topics;
import io.quarkus.test.junit.QuarkusTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OrderIntakeTest {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    private static String order(String id, int quantity, String amount) {
        return """
                {"id":"%s","customerId":"CUST-7","itemSku":"SKU-1","quantity":%d,"amount":%s}
                """.formatted(id, quantity, amount);
    }

    private List<ConsumerRecord<String, String>> drain(Duration wait) {
        var props = Map.<String, Object>of(
                "bootstrap.servers", bootstrapServers,
                "group.id", "test-" + UUID.randomUUID(),
                "auto.offset.reset", "earliest");
        var records = new ArrayList<ConsumerRecord<String, String>>();
        try (var consumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(Topics.ORDERS_PLACED));
            long end = System.nanoTime() + wait.toNanos();
            while (System.nanoTime() < end) {
                consumer.poll(Duration.ofMillis(500)).forEach(records::add);
            }
        }
        return records;
    }

    @Test
    void validOrderIsAcceptedAndPublishedKeyedById() {
        given().contentType("application/json").body(order("O-1001", 2, "49.90"))
                .post("/orders").then().statusCode(202);

        var records = drain(Duration.ofSeconds(10));
        var published = records.stream().filter(r -> "O-1001".equals(r.key())).findFirst();
        assertTrue(published.isPresent(), "record keyed O-1001 on the order-placed topic");
        assertTrue(published.get().value().contains("\"id\":\"O-1001\""));
    }

    @Test
    void invalidOrderIsRejectedAndNothingIsPublished() {
        given().contentType("application/json").body(order("O-BAD", 0, "49.90"))
                .post("/orders").then().statusCode(400);
        given().contentType("application/json").body(order("O-NEG", 1, "-5"))
                .post("/orders").then().statusCode(400);
        given().contentType("application/json").body("{not json")
                .post("/orders").then().statusCode(400);

        var ids = drain(Duration.ofSeconds(5)).stream().map(ConsumerRecord::key).toList();
        assertFalse(ids.contains("O-BAD") || ids.contains("O-NEG"), "an invalid order must not reach the topic");
    }

    @Test
    void blankCustomerIsRejectedWith400() {
        given().contentType("application/json")
                .body("""
                        {"id":"O-BLANK","customerId":" ","itemSku":"SKU-1","quantity":1,"amount":10.00}
                        """)
                .post("/orders").then().statusCode(400);
    }

    @Test
    void healthReportsUp() {
        given().get("/q/health/ready").then().statusCode(200);
    }
}
