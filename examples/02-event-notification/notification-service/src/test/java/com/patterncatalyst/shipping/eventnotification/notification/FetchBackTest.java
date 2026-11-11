package com.patterncatalyst.shipping.eventnotification.notification;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.OrderStatus;
import com.patterncatalyst.shipping.Topics;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class FetchBackTest {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @AfterEach
    void sourceBackUp() {
        StubOrderService.DOWN.set(false);
    }

    /** Publishes an OrderPlaced record that carries only the id, and returns its offset. */
    private long publish(String orderId) throws Exception {
        var props = Map.<String, Object>of("bootstrap.servers", bootstrapServers);
        try (var producer = new KafkaProducer<>(props, new StringSerializer(), new StringSerializer())) {
            String value = """
                    {"eventId":"%s","occurredAt":"2026-01-01T00:00:00Z","orderId":"%s"}
                    """.formatted(UUID.randomUUID(), orderId);
            return producer.send(new ProducerRecord<>(Topics.ORDERS_PLACED, orderId, value)).get().offset();
        }
    }

    private long committed() throws Exception {
        try (var admin = Admin.create(Map.<String, Object>of("bootstrap.servers", bootstrapServers))) {
            var offsets = admin.listConsumerGroupOffsets("notification-service")
                    .partitionsToOffsetAndMetadata().get();
            var meta = offsets.get(new TopicPartition(Topics.ORDERS_PLACED, 0));
            return meta == null ? 0 : meta.offset();
        }
    }

    private static String orderId() {
        return "O-" + UUID.randomUUID();
    }

    @Test
    void notificationHoldsTheFetchedCustomerAndAmount() throws Exception {
        String id = orderId();
        StubOrderService.put(id, "CU-9", "59.90", OrderStatus.PLACED);
        publish(id);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                given().queryParam("orderId", id).get("/notifications").then()
                        .body("$", hasSize(1))
                        .body("[0].customerId", org.hamcrest.Matchers.equalTo("CU-9"))
                        .body("[0].amount", org.hamcrest.Matchers.equalTo(59.90f))
                        .body("[0].orderStatus", org.hamcrest.Matchers.equalTo("PLACED"))
                        .body("[0].eventType", org.hamcrest.Matchers.equalTo("OrderPlaced")));
    }

    @Test
    void notificationShowsTheStatusAtFetchTimeNotAtEventTime() throws Exception {
        String id = orderId();
        StubOrderService.put(id, "CU-9", "10.00", OrderStatus.CANCELLED);
        publish(id);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                given().queryParam("orderId", id).get("/notifications").then()
                        .body("[0].orderStatus", org.hamcrest.Matchers.equalTo("CANCELLED")));
    }

    @Test
    void failedFetchIsRetriedAndTheOffsetStaysUntilItSucceeds() throws Exception {
        String id = orderId();
        StubOrderService.put(id, "CU-9", "25.00", OrderStatus.PLACED);
        StubOrderService.DOWN.set(true);
        long offset = publish(id);

        Thread.sleep(8000);
        assertTrue(committed() <= offset, "offset " + offset + " must not be committed past while the fetch fails");
        given().queryParam("orderId", id).get("/notifications").then().body("$", hasSize(0));

        StubOrderService.DOWN.set(false);
        await().atMost(Duration.ofSeconds(40)).untilAsserted(() ->
                given().queryParam("orderId", id).get("/notifications").then().body("$", hasSize(1)));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertTrue(committed() > offset, "offset is committed once the fetch succeeds"));
    }

    @Test
    void onlyOneNotificationPerRecord() throws Exception {
        String id = orderId();
        StubOrderService.put(id, "CU-9", "5.00", OrderStatus.PLACED);
        publish(id);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                given().queryParam("orderId", id).get("/notifications").then().body("$", hasSize(1)));
        Thread.sleep(3000);
        assertEquals(1, given().queryParam("orderId", id).get("/notifications").jsonPath().getList("$").size());
    }
}
