package com.patterncatalyst.shipping.stackcheck;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
class ConsignmentIntakeTest {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    private static String consignment(String id, String weight) {
        return """
                {"id":"%s","origin":"Rotterdam","destination":"Singapore","weightKg":%s,"carrier":"FASTLINE"}
                """.formatted(id, weight);
    }

    private List<ConsumerRecord<String, String>> drain(Duration wait) {
        var props = Map.<String, Object>of(
                "bootstrap.servers", bootstrapServers,
                "group.id", "test-" + UUID.randomUUID(),
                "auto.offset.reset", "earliest");
        var records = new ArrayList<ConsumerRecord<String, String>>();
        try (var consumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of("consignments"));
            long end = System.nanoTime() + wait.toNanos();
            while (System.nanoTime() < end) {
                consumer.poll(Duration.ofMillis(500)).forEach(records::add);
            }
        }
        return records;
    }

    @Test
    void validConsignmentIsAcceptedAndPublishedKeyedById() {
        given().contentType("application/json").body(consignment("C-1001", "1200"))
                .post("/consignments").then().statusCode(202);

        var records = drain(Duration.ofSeconds(10));
        var published = records.stream().filter(r -> "C-1001".equals(r.key())).findFirst();
        assertTrue(published.isPresent(), "record keyed C-1001 on the consignments topic");
        assertTrue(published.get().value().contains("\"id\":\"C-1001\""));
    }

    @Test
    void invalidConsignmentIsRejectedAndNothingIsPublished() {
        given().contentType("application/json").body(consignment("C-BAD", "-5"))
                .post("/consignments").then().statusCode(400);
        given().contentType("application/json").body("{not json")
                .post("/consignments").then().statusCode(400);

        assertFalse(drain(Duration.ofSeconds(5)).stream().anyMatch(r -> "C-BAD".equals(r.key())),
                "an invalid consignment must not reach the topic");
    }

    @Test
    void healthReportsUp() {
        given().get("/q/health/ready").then().statusCode(200);
    }
}
