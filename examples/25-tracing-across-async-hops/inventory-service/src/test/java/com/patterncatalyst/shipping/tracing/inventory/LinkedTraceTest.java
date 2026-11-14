package com.patterncatalyst.shipping.tracing.inventory;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Map;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

/** Demo code: the processing is a new trace that links to the context in the record. */
@QuarkusTest
@TestProfile(LinkedTraceTest.Link.class)
class LinkedTraceTest {

    public static class Link implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("demo.consumer-trace", "link", "mp.messaging.incoming.placed.tracing-enabled", "false");
        }
    }

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrap;

    @Inject
    InMemorySpanExporter spans;

    private SpanData reserveSpan(String orderId) {
        AttributeKey<String> key = AttributeKey.stringKey("order.id");
        await().atMost(30, SECONDS).until(() -> spans.getFinishedSpanItems().stream()
                .anyMatch(s -> s.getName().equals("reserve stock") && orderId.equals(s.getAttributes().get(key))));
        return spans.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("reserve stock") && orderId.equals(s.getAttributes().get(key)))
                .findFirst().orElseThrow();
    }

    @Test
    void theProcessingIsANewTraceThatLinksToTheRecord() {
        String id = "O-" + UUID.randomUUID();
        String trace = Records.hex(16);
        String span = Records.hex(8);
        Records.stock("SKU-T-" + id, 5);
        Records.placed(bootstrap, id, "SKU-T-" + id, 1, "00-" + trace + "-" + span + "-01");
        SpanData reserve = reserveSpan(id);
        SpanData process = spans.getFinishedSpanItems().stream()
                .filter(s -> s.getSpanId().equals(reserve.getParentSpanId())).findFirst().orElseThrow();
        assertEquals("process edp.orders.placed", process.getName());
        assertNotEquals(trace, process.getTraceId());
        assertFalse(process.getParentSpanContext().isValid());
        assertEquals(1, process.getLinks().size());
        assertEquals(trace, process.getLinks().get(0).getSpanContext().getTraceId());
        assertEquals(span, process.getLinks().get(0).getSpanContext().getSpanId());
    }

    @Test
    void theRecordsTraceHoldsNoConsumerSpan() {
        String id = "O-" + UUID.randomUUID();
        String trace = Records.hex(16);
        Records.stock("SKU-T-" + id, 5);
        Records.placed(bootstrap, id, "SKU-T-" + id, 1, "00-" + trace + "-" + Records.hex(8) + "-01");
        reserveSpan(id);
        assertFalse(spans.getFinishedSpanItems().stream().anyMatch(s -> s.getTraceId().equals(trace)));
    }
}
