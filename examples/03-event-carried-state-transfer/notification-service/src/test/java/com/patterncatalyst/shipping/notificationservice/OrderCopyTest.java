package com.patterncatalyst.shipping.notificationservice;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.Topics;

import io.quarkus.test.junit.QuarkusTest;

/** Every test runs with no order-service: the events alone have to answer. */
@QuarkusTest
class OrderCopyTest {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrap;

    private void publish(String topic, String id, String json) {
        var props = Map.<String, Object>of("bootstrap.servers", bootstrap);
        try (var producer = new KafkaProducer<>(props, new StringSerializer(), new StringSerializer())) {
            producer.send(new ProducerRecord<>(topic, id, json));
            producer.flush();
        }
    }

    private static String state(String id, String status, String claim) {
        return """
                {"orderId":"%s","order":{"id":"%s","customerId":"CU-77","itemSku":"SKU-42","quantity":3,"amount":59.90},"status":"%s"%s}
                """.formatted(id, id, status, claim == null ? "" : ",\"claim\":" + claim);
    }

    private static void awaitStatus(String id, String status) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(
                () -> given().get("/order-copies/" + id).then().statusCode(200).body("status", equalTo(status)));
    }

    @Test
    void placedEventBecomesACopyThatAnswersWithEveryField() {
        String id = "O-" + UUID.randomUUID();
        int calls = StubOrderService.ORDER_CALLS.get();
        publish(Topics.ORDERS_PLACED, id, state(id, "PLACED", null));
        awaitStatus(id, "PLACED");
        given().get("/order-copies/" + id).then().statusCode(200)
                .body("id", equalTo(id), "customerId", equalTo("CU-77"), "itemSku", equalTo("SKU-42"),
                        "quantity", equalTo(3), "amount", equalTo(59.9f), "attachment", nullValue());
        assertEquals(calls, StubOrderService.ORDER_CALLS.get(), "the copy answered without calling the order-service");
    }

    @Test
    void cancelledEventMovesTheCopyToCancelled() {
        String id = "O-" + UUID.randomUUID();
        publish(Topics.ORDERS_PLACED, id, state(id, "PLACED", null));
        awaitStatus(id, "PLACED");
        publish(Topics.ORDERS_CANCELLED, id, state(id, "CANCELLED", null));
        awaitStatus(id, "CANCELLED");
    }

    @Test
    void aLatePlacedEventDoesNotUndoACancellation() {
        String id = "O-" + UUID.randomUUID();
        publish(Topics.ORDERS_CANCELLED, id, state(id, "CANCELLED", null));
        awaitStatus(id, "CANCELLED");
        publish(Topics.ORDERS_PLACED, id, state(id, "PLACED", null));
        await().during(Duration.ofSeconds(4)).atMost(Duration.ofSeconds(10)).untilAsserted(
                () -> given().get("/order-copies/" + id).then().body("status", equalTo("CANCELLED")));
    }

    @Test
    void thinEventIsSkippedAndTheRouteKeepsConsuming() {
        String thin = "O-" + UUID.randomUUID();
        publish(Topics.ORDERS_PLACED, thin, "{\"orderId\":\"" + thin + "\"}");
        String next = "O-" + UUID.randomUUID();
        publish(Topics.ORDERS_PLACED, next, state(next, "PLACED", null));
        awaitStatus(next, "PLACED");
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(
                () -> given().get("/order-copies/" + thin).then().statusCode(404));
    }

    @Test
    void claimOnTheEventIsKeptAndFetchedOnlyOnDemand() {
        String id = "O-" + UUID.randomUUID();
        String claim = UUID.randomUUID().toString();
        int before = StubOrderService.CLAIM_CALLS.get();
        publish(Topics.ORDERS_PLACED, id, state(id, "PLACED", "{\"id\":\"" + claim + "\",\"size\":102400}"));
        awaitStatus(id, "PLACED");
        given().get("/order-copies/" + id).then().body("attachment.id", equalTo(claim), "attachment.size", equalTo(102400));
        assertEquals(before, StubOrderService.CLAIM_CALLS.get(), "no fetch until someone asks");
        given().get("/order-copies/" + id + "/attachment").then().statusCode(200).body(equalTo("attachment-for-" + claim));
        assertEquals(before + 1, StubOrderService.CLAIM_CALLS.get());
    }

    @Test
    void orderWithoutAttachmentHasNothingToFetch() {
        String id = "O-" + UUID.randomUUID();
        publish(Topics.ORDERS_PLACED, id, state(id, "PLACED", null));
        awaitStatus(id, "PLACED");
        given().get("/order-copies/" + id + "/attachment").then().statusCode(404);
    }

    @Test
    void unknownOrderIs404() {
        given().get("/order-copies/O-NONE").then().statusCode(404);
    }
}
