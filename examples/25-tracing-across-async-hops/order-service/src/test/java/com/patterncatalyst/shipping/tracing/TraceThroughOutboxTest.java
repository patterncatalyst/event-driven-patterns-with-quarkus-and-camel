package com.patterncatalyst.shipping.tracing;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@QuarkusTest
class TraceThroughOutboxTest {

    private static final AttributeKey<String> ORDER_ID = AttributeKey.stringKey("order.id");

    @Inject
    InMemorySpanExporter spans;

    @Inject
    EntityManager em;

    @BeforeEach
    void reset() {
        spans.reset();
    }

    private static String place() {
        String id = "O-" + UUID.randomUUID();
        int status = given().contentType(ContentType.JSON)
                .body(Map.of("id", id, "customerId", "CU-25", "itemSku", "SKU-25", "quantity", 1, "amount", 10.00))
                .post("/orders").statusCode();
        assertEquals(201, status);
        return id;
    }

    private SpanData requestSpan(String orderId) {
        await().atMost(10, SECONDS).until(() -> spans.getFinishedSpanItems().stream()
                .anyMatch(s -> s.getName().equals("POST /orders") && orderId.equals(s.getAttributes().get(ORDER_ID))));
        return spans.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("POST /orders") && orderId.equals(s.getAttributes().get(ORDER_ID)))
                .findFirst().orElseThrow();
    }

    private String contextOf(String orderId) {
        return QuarkusTransaction.requiringNew().call(() -> (String) em
                .createNativeQuery("select tracingspancontext from orders.outboxevent where aggregateid = ?1")
                .setParameter(1, orderId).getSingleResult());
    }

    @Test
    void theOrderIdIsOnTheRequestSpan() {
        String id = place();
        assertEquals(id, requestSpan(id).getAttributes().get(ORDER_ID));
    }

    @Test
    void theOutboxWriteIsAChildOfTheRequestSpan() {
        SpanData request = requestSpan(place());
        SpanData write = spans.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("outbox-write") && s.getTraceId().equals(request.getTraceId()))
                .findFirst().orElseThrow();
        assertEquals(request.getSpanId(), write.getParentSpanId());
    }

    @Test
    void theOutboxRowCarriesTheTraceOfTheRequest() {
        String id = place();
        SpanData request = requestSpan(id);
        String context = contextOf(id);
        assertTrue(context.contains("traceparent=00-" + request.getTraceId() + "-"), context);
    }

    @Test
    void twoRequestsCarryTwoTraces() {
        String first = place();
        String second = place();
        assertNotEquals(requestSpan(first).getTraceId(), requestSpan(second).getTraceId());
        assertNotEquals(contextOf(first), contextOf(second));
    }
}
