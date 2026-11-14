package com.patterncatalyst.shipping.correlation.order;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.awaitility.Awaitility;
import org.jboss.logging.MDC;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

/** The order id on spans and in the log context, and the sweeper's trace linked to the trace that placed the order. */
@QuarkusTest
class CorrelationTest {

    @Inject
    SagaService saga;

    @Inject
    Correlated correlated;

    @Inject
    InMemorySpanExporter exporter;

    private static void post(String id) {
        given().contentType(ContentType.JSON)
                .body(Map.of("id", id, "customerId", "CU-27", "itemSku", "SKU-27", "quantity", 1, "amount", 25.00))
                .post("/orders").then().statusCode(201);
    }

    private void expireInSweeper(String id) {
        saga.onReserved(UUID.randomUUID(), new InventoryReserved(id, "SKU-27", 1, new BigDecimal("25.00")));
        Fixtures.expire(id);
        assertTrue(saga.expire() >= 1);
    }

    @Test
    void aStepRunsInASpanWithTheOrderIdAndThatIdInTheLogContext() {
        String id = Fixtures.newId();
        AtomicReference<Object> inside = new AtomicReference<>();
        correlated.step(id, () -> inside.set(MDC.get("orderId")));
        assertEquals(id, inside.get());
        assertNull(MDC.get("orderId"));
        assertEquals(id, Spans.await(exporter, "handle event", id).getAttributes()
                .get(AttributeKey.stringKey("order.id")));
    }

    @Test
    void placingAnOrderOverHttpPutsItsOrderIdOnASpanOfTheRequestTrace() {
        String id = Fixtures.newId();
        post(id);
        SpanData handled = Spans.await(exporter, "handle event", id);
        assertTrue(Spans.inTrace(exporter, handled.getTraceId()).stream().anyMatch(s -> s.getName().contains("POST")));
    }

    @Test
    void theSagaStoresTheTraceContextOfTheRequestThatPlacedIt() {
        String id = Fixtures.newId();
        post(id);
        SpanData handled = Spans.await(exporter, "handle event", id);
        String[] parts = Fixtures.saga(id).traceparent.split("-");
        assertEquals(4, parts.length);
        assertEquals(handled.getTraceId(), parts[1]);
    }

    @Test
    void theSweepersTraceIsARootWithTheOrderIdAndALinkToTheOrdersTrace() {
        String id = Fixtures.newId();
        post(id);
        SpanData placed = Spans.await(exporter, "handle event", id);
        expireInSweeper(id);
        SpanData sweep = Spans.await(exporter, "cancel expired saga", id);
        assertFalse(sweep.getParentSpanContext().isValid());
        assertNotEquals(placed.getTraceId(), sweep.getTraceId());
        assertEquals(1, sweep.getLinks().size());
        assertEquals(placed.getTraceId(), sweep.getLinks().get(0).getSpanContext().getTraceId());
    }

    @Test
    void theCancellationEventIsWrittenInsideTheSweepersTrace() {
        String id = Fixtures.newId();
        post(id);
        expireInSweeper(id);
        SpanData sweep = Spans.await(exporter, "cancel expired saga", id);
        SpanData outbox = Awaitility.await().atMost(Duration.ofSeconds(30))
                .until(() -> Spans.inTrace(exporter, sweep.getTraceId()).stream()
                        .filter(s -> s.getName().equals("outbox-write")).findFirst().orElse(null), Objects::nonNull);
        assertEquals(sweep.getSpanId(), outbox.getParentSpanId());
    }
}
