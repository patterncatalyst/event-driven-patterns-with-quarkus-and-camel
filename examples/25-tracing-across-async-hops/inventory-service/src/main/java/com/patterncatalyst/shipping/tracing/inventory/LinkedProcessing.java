package com.patterncatalyst.shipping.tracing.inventory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Demo code: the processing starts a new trace and links it to the context in the record, the form the OpenTelemetry
 * messaging conventions recommend when one span covers many records or later work.
 */
@ApplicationScoped
public class LinkedProcessing {

    private static final TextMapGetter<Headers> HEADERS = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Headers headers) {
            List<String> keys = new ArrayList<>();
            headers.forEach(h -> keys.add(h.key()));
            return keys;
        }

        @Override
        public String get(Headers headers, String key) {
            Header header = headers == null ? null : headers.lastHeader(key);
            return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
        }
    };

    private final Tracer tracer;

    LinkedProcessing(Tracer tracer) {
        this.tracer = tracer;
    }

    public void run(Headers headers, Runnable work) {
        Context creation = W3CTraceContextPropagator.getInstance().extract(Context.root(), headers, HEADERS);
        var span = tracer.spanBuilder("process edp.orders.placed").setSpanKind(SpanKind.CONSUMER).setNoParent();
        if (Span.fromContext(creation).getSpanContext().isValid()) {
            span.addLink(Span.fromContext(creation).getSpanContext());
        }
        Span started = span.startSpan();
        try (Scope scope = started.makeCurrent()) {
            work.run();
        } finally {
            started.end();
        }
    }
}
