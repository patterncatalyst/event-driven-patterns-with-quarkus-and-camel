package com.patterncatalyst.shipping.eventnotification.order;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.apache.camel.CamelContext;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.Order;
import com.patterncatalyst.shipping.Topics;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

@QuarkusTest
class OrderEventTest {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Inject
    CamelContext camel;

    @Inject
    ObjectMapper json;

    @Inject
    OrderService service;

    private static String place() {
        String id = "O-" + UUID.randomUUID();
        given().contentType(ContentType.JSON)
                .body(Map.of("id", id, "customerId", "CU-77", "itemSku", "SKU-42", "quantity", 3, "amount", 59.90))
                .post("/orders").then().statusCode(201);
        return id;
    }

    /** Reads for the whole wait, so a record published late still counts. */
    private List<ConsumerRecord<String, String>> drainAll(String orderId, Duration wait) {
        return drain(orderId, wait, false);
    }

    private List<ConsumerRecord<String, String>> drain(String orderId, Duration wait) {
        return drain(orderId, wait, true);
    }

    private List<ConsumerRecord<String, String>> drain(String orderId, Duration wait, boolean stopAtFirst) {
        var props = Map.<String, Object>of(
                "bootstrap.servers", bootstrapServers,
                "group.id", "test-" + UUID.randomUUID(),
                "auto.offset.reset", "earliest");
        var found = new ArrayList<ConsumerRecord<String, String>>();
        try (var consumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(Topics.ORDERS_PLACED));
            long end = System.nanoTime() + wait.toNanos();
            while (System.nanoTime() < end && !(stopAtFirst && !found.isEmpty())) {
                consumer.poll(Duration.ofMillis(500)).forEach(r -> {
                    if (orderId.equals(r.key())) {
                        found.add(r);
                    }
                });
            }
        }
        return found;
    }

    @Test
    void recordCarriesOnlyTheOrderIdKeyedByOrderId() throws Exception {
        String id = place();
        var records = drain(id, Duration.ofSeconds(20));
        assertEquals(1, records.size());
        var tree = json.readTree(records.get(0).value());
        Set<String> fields = new TreeSet<>();
        tree.fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("eventId", "occurredAt", "orderId"), fields);
        assertEquals(id, tree.get("orderId").asText());
        assertEquals(id, records.get(0).key());
    }

    @Test
    void orderIsReadableWhenTheRecordIsPublished() {
        String id = place();
        assertEquals(1, drain(id, Duration.ofSeconds(20)).size());
        given().get("/orders/" + id).then().statusCode(200)
                .body("customerId", equalTo("CU-77"))
                .body("amount", equalTo(59.90f))
                .body("status", equalTo("PLACED"));
    }

    @Test
    void rolledBackOrderPublishesNothing() {
        String id = "O-" + UUID.randomUUID();
        QuarkusTransaction.begin();
        service.place(new Order(id, "CU-77", "SKU-42", 3, new BigDecimal("59.90")));
        QuarkusTransaction.rollback();
        assertTrue(drain(id, Duration.ofSeconds(5)).isEmpty());
        given().get("/orders/" + id).then().statusCode(404);
    }

    @Test
    void cancelledOrderReadsCancelled() {
        String id = place();
        given().post("/orders/" + id + "/cancel").then().statusCode(204);
        given().get("/orders/" + id).then().body("status", equalTo("CANCELLED"));
        given().post("/orders/" + id + "/cancel").then().statusCode(409);
    }

    @Test
    void duplicateAndInvalidOrdersPublishNothing() {
        String id = place();
        given().contentType(ContentType.JSON)
                .body(Map.of("id", id, "customerId", "CU-77", "itemSku", "SKU-42", "quantity", 1, "amount", 5))
                .post("/orders").then().statusCode(409);
        given().contentType(ContentType.JSON)
                .body(Map.of("id", "O-BAD", "customerId", "CU-77", "itemSku", "SKU-42", "quantity", 0, "amount", 5))
                .post("/orders").then().statusCode(400);
        assertEquals(1, drainAll(id, Duration.ofSeconds(10)).size());
        assertTrue(drain("O-BAD", Duration.ofSeconds(3)).isEmpty());
    }

    @Test
    void unknownOrderIs404() {
        given().get("/orders/O-NOPE").then().statusCode(404);
    }

    @Test
    void failedPublishLeavesTheOrderWithoutAnEvent() throws Exception {
        camel.getRouteController().stopRoute("order-placed-publisher");
        try {
            String id = place();
            given().get("/orders/" + id).then().statusCode(200);
            assertTrue(drain(id, Duration.ofSeconds(5)).isEmpty());
        } finally {
            camel.getRouteController().startRoute("order-placed-publisher");
        }
    }
}
