package com.patterncatalyst.shipping.correlation.order;

import java.time.Duration;
import java.util.List;

import org.awaitility.Awaitility;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;

/** Finds finished spans by name and order id. */
final class Spans {

    private static final AttributeKey<String> ORDER_ID = AttributeKey.stringKey("order.id");

    private Spans() {
    }

    /** The span with this name and order id, once the batch processor has exported it. */
    static SpanData await(InMemorySpanExporter exporter, String name, String orderId) {
        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200))
                .until(() -> find(exporter, name, orderId) != null);
        return find(exporter, name, orderId);
    }

    static SpanData find(InMemorySpanExporter exporter, String name, String orderId) {
        return exporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals(name) && orderId.equals(s.getAttributes().get(ORDER_ID)))
                .findFirst().orElse(null);
    }

    static List<SpanData> inTrace(InMemorySpanExporter exporter, String traceId) {
        return exporter.getFinishedSpanItems().stream().filter(s -> s.getTraceId().equals(traceId)).toList();
    }
}
