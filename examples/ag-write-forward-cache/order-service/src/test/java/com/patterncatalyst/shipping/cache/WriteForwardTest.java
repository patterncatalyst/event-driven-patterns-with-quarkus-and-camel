package com.patterncatalyst.shipping.cache;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.kafka.KafkaConstants;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.OrderStatus;
import com.patterncatalyst.shipping.Topics;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@QuarkusTest
class WriteForwardTest {

    @Inject
    EntityManager em;

    @Inject
    OrderCache cache;

    @Inject
    ProducerTemplate producer;

    @Inject
    ObjectMapper json;

    private static String newId() {
        return "O-" + UUID.randomUUID();
    }

    private static int post(String path, String id) {
        var body = Map.of("id", id, "customerId", "CU-77", "itemSku", "SKU-42", "quantity", 3, "amount", 59.90);
        return given().contentType(ContentType.JSON).body(body).post(path).statusCode();
    }

    private long count(String table, String column, String id) {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) em
                .createNativeQuery("select count(*) from orders." + table + " where " + column + " = ?1")
                .setParameter(1, id).getSingleResult()).longValue());
    }

    private static OrderView view(String id, OrderStatus status, long version) {
        return new OrderView(id, "CU-77", "SKU-42", 3, new BigDecimal("59.90"), status, version);
    }

    @Test
    void orderAndOutboxRowAreWrittenTogether() throws Exception {
        String id = newId();
        assertEquals(201, post("/orders", id));
        assertEquals(1, count("orders", "id", id));
        assertEquals(1, count("outboxevent", "aggregateid", id));

        Object[] row = QuarkusTransaction.requiringNew().call(() -> (Object[]) em.createNativeQuery(
                "select type, topic, payload from orders.outboxevent where aggregateid = ?1")
                .setParameter(1, id).getSingleResult());
        assertEquals("OrderPlaced", row[0]);
        assertEquals(Topics.ORDERS_PLACED, row[1]);
        JsonNode payload = json.readTree((String) row[2]);
        assertEquals("PLACED", payload.get("status").asText());
        assertEquals(1, payload.get("version").asLong());
        assertEquals("CU-77", payload.get("customerId").asText());
    }

    @Test
    void cancelIncrementsTheVersionAndWritesAnEventWithTheFullState() throws Exception {
        String id = newId();
        assertEquals(201, post("/orders", id));
        assertEquals(200, given().post("/orders/" + id + "/cancel").statusCode());
        Object[] row = QuarkusTransaction.requiringNew().call(() -> (Object[]) em.createNativeQuery(
                "select topic, payload from orders.outboxevent where aggregateid = ?1 and type = 'OrderCancelled'")
                .setParameter(1, id).getSingleResult());
        assertEquals(Topics.ORDERS_CANCELLED, row[0]);
        JsonNode payload = json.readTree((String) row[1]);
        assertEquals("CANCELLED", payload.get("status").asText());
        assertEquals(2, payload.get("version").asLong());
        assertEquals("SKU-42", payload.get("itemSku").asText());
    }

    @Test
    void aFailedTransactionLeavesNoRowNoEventAndNoCacheEntry() throws Exception {
        assertEquals(500, post("/orders", "O-FAIL"));
        assertEquals(0, count("orders", "id", "O-FAIL"));
        assertEquals(0, count("outboxevent", "aggregateid", "O-FAIL"));
        assertTrue(cache.find("O-FAIL").isEmpty());
    }

    @Test
    void theRequestDoesNotWriteTheCache() {
        String id = newId();
        assertEquals(201, post("/orders", id));
        assertEquals(200, given().get("/orders/" + id + "/db").statusCode());
        assertEquals(404, given().get("/orders/" + id).statusCode());
    }

    @Test
    void theVersionGuardIgnoresAnOlderEvent() throws Exception {
        String id = newId();
        cache.apply(view(id, OrderStatus.CANCELLED, 2));
        cache.apply(view(id, OrderStatus.PLACED, 1));
        assertEquals(OrderStatus.CANCELLED, cache.find(id).orElseThrow().status());
        cache.apply(view(id, OrderStatus.CANCELLED, 2));
        assertEquals(2, cache.find(id).orElseThrow().version());
    }

    @Test
    void theRouteAppliesAnEventFromTheTopicToRedis() throws Exception {
        String id = newId();
        producer.sendBodyAndHeader("kafka:" + Topics.ORDERS_PLACED, json.writeValueAsString(view(id, OrderStatus.PLACED, 1)),
                KafkaConstants.KEY, id);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertEquals(200, given().get("/orders/" + id).statusCode());
        });
        assertEquals(OrderStatus.PLACED, cache.find(id).orElseThrow().status());
    }
}
