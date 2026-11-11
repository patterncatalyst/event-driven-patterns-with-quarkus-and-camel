package com.patterncatalyst.shipping.outbox;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.Order;
import com.patterncatalyst.shipping.Topics;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@QuarkusTest
class OutboxTest {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Inject
    EntityManager em;

    @Inject
    OrderService service;

    @Inject
    ObjectMapper json;

    private static Map<String, Object> body(String id) {
        return Map.of("id", id, "customerId", "CU-77", "itemSku", "SKU-42", "quantity", 3, "amount", 59.90);
    }

    private static int post(String path, String id) {
        return given().contentType(ContentType.JSON).body(body(id)).post(path).statusCode();
    }

    private long count(String table, String column, String id) {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) em
                .createNativeQuery("select count(*) from orders." + table + " where " + column + " = ?1")
                .setParameter(1, id).getSingleResult()).longValue());
    }

    private List<ConsumerRecord<String, String>> drain(String orderId, Duration wait) {
        var props = Map.<String, Object>of("bootstrap.servers", bootstrapServers,
                "group.id", "test-" + UUID.randomUUID(), "auto.offset.reset", "earliest");
        var found = new ArrayList<ConsumerRecord<String, String>>();
        try (var consumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(Topics.ORDERS_PLACED));
            long end = System.nanoTime() + wait.toNanos();
            while (System.nanoTime() < end) {
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
    void orderAndOutboxRowAreWrittenTogether() throws Exception {
        String id = "O-" + UUID.randomUUID();
        assertEquals(201, post("/orders", id));
        assertEquals(1, count("orders", "id", id));
        assertEquals(1, count("outboxevent", "aggregateid", id));

        Object[] row = QuarkusTransaction.requiringNew().call(() -> (Object[]) em.createNativeQuery(
                "select aggregatetype, type, topic, payload from orders.outboxevent where aggregateid = ?1")
                .setParameter(1, id).getSingleResult());
        assertEquals("Order", row[0]);
        assertEquals("OrderPlaced", row[1]);
        assertEquals(Topics.ORDERS_PLACED, row[2]);
        JsonNode payload = json.readTree((String) row[3]);
        assertEquals(id, payload.get("orderId").asText());
        assertEquals("CU-77", payload.get("customerId").asText());
        assertEquals("PLACED", payload.get("status").asText());
    }

    @Test
    void rolledBackOrderLeavesNoOutboxRow() {
        String id = "O-" + UUID.randomUUID();
        QuarkusTransaction.begin();
        service.place(new Order(id, "CU-77", "SKU-42", 3, new BigDecimal("59.90")));
        QuarkusTransaction.rollback();
        assertEquals(0, count("orders", "id", id));
        assertEquals(0, count("outboxevent", "aggregateid", id));
    }

    @Test
    void duplicateOrderIsRefusedAndAddsNoOutboxRow() {
        String id = "O-" + UUID.randomUUID();
        assertEquals(201, post("/orders", id));
        assertEquals(409, post("/orders", id));
        assertEquals(1, count("orders", "id", id));
        assertEquals(1, count("outboxevent", "aggregateid", id));
    }

    @Test
    void crashAfterCommitKeepsTheOrderAndItsOutboxRow() {
        assertEquals(500, post("/orders", "O-CRASH-1"));
        assertEquals(1, count("orders", "id", "O-CRASH-1"));
        assertEquals(1, count("outboxevent", "aggregateid", "O-CRASH-1"));
    }

    @Test
    void commitFirstPublishesAfterTheCommit() {
        String id = "O-" + UUID.randomUUID();
        assertEquals(201, post("/orders/dual-write?mode=commit-first", id));
        assertEquals(1, count("orders", "id", id));
        var records = drain(id, Duration.ofSeconds(5));
        assertEquals(1, records.size());
        assertEquals(id, records.get(0).key());
    }

    @Test
    void commitFirstCrashLosesTheEvent() {
        assertEquals(500, post("/orders/dual-write?mode=commit-first", "O-CRASH-2"));
        assertEquals(1, count("orders", "id", "O-CRASH-2"));
        assertTrue(drain("O-CRASH-2", Duration.ofSeconds(5)).isEmpty());
    }

    @Test
    void publishFirstDuplicateLeavesAGhostEvent() {
        String id = "O-" + UUID.randomUUID();
        assertEquals(201, post("/orders/dual-write?mode=publish-first", id));
        assertTrue(post("/orders/dual-write?mode=publish-first", id) >= 500);
        assertEquals(1, count("orders", "id", id));
        assertEquals(2, drain(id, Duration.ofSeconds(5)).size());
    }
}
