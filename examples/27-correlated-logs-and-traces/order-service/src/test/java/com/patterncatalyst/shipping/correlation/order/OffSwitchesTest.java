package com.patterncatalyst.shipping.correlation.order;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.logging.MDC;
import org.junit.jupiter.api.Test;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

/** Demo switches SWEEPER_LINK=off and ORDER_ID_IN_LOGS=off: the sweeper's trace has no link and the log context has no order id. */
@QuarkusTest
@TestProfile(OffSwitchesProfile.class)
class OffSwitchesTest {

    @Inject
    SagaService saga;

    @Inject
    Correlated correlated;

    @Inject
    InMemorySpanExporter exporter;

    @Test
    void withTheLinkOffTheSweepersTraceStillCarriesTheOrderIdButLinksToNothing() {
        String id = Fixtures.newId();
        given().contentType(ContentType.JSON)
                .body(Map.of("id", id, "customerId", "CU-27", "itemSku", "SKU-27", "quantity", 1, "amount", 25.00))
                .post("/orders").then().statusCode(201);
        saga.onReserved(UUID.randomUUID(), new InventoryReserved(id, "SKU-27", 1, new BigDecimal("25.00")));
        Fixtures.expire(id);
        assertTrue(saga.expire() >= 1);
        SpanData sweep = Spans.await(exporter, "cancel expired saga", id);
        assertEquals(0, sweep.getLinks().size());
    }

    @Test
    void withTheSwitchOffTheLogContextHasNoOrderIdButTheSpanStillDoes() {
        String id = Fixtures.newId();
        AtomicReference<Object> inside = new AtomicReference<>("unset");
        correlated.step(id, () -> inside.set(MDC.get("orderId")));
        assertNull(inside.get());
        Spans.await(exporter, "handle event", id);
    }
}
