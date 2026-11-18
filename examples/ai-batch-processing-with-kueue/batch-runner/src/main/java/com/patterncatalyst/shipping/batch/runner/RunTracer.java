package com.patterncatalyst.shipping.batch.runner;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * One span per run, started when the run is accepted and ended when it finishes. Its W3C traceparent is stored with the
 * run and handed to every Job, so the pods' spans join the run's trace.
 */
@ApplicationScoped
public class RunTracer {

    private static final TextMapGetter<Map<String, String>> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier == null ? null : carrier.get(key);
        }
    };
    private static final TextMapSetter<Map<String, String>> SETTER = Map::put;

    private final Tracer tracer;
    private final Map<String, Span> open = new ConcurrentHashMap<>();

    RunTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    /** Starts the span of a run, or of a replay as a child of the run's earlier span; returns the new traceparent. */
    String start(String runId, String name, String parentTraceparent) {
        var builder = tracer.spanBuilder(name).setAttribute("edp.batch.run", runId);
        if (parentTraceparent != null) {
            builder.setParent(W3CTraceContextPropagator.getInstance().extract(Context.root(),
                    Map.of("traceparent", parentTraceparent), GETTER));
        }
        Span span = builder.startSpan();
        open.put(runId, span);
        SpanContext sc = span.getSpanContext();
        if (!sc.isValid()) {
            return parentTraceparent;
        }
        Map<String, String> carrier = new HashMap<>();
        W3CTraceContextPropagator.getInstance().inject(Context.root().with(span), carrier, SETTER);
        return carrier.get("traceparent");
    }

    void end(String runId, boolean failed) {
        Span span = open.remove(runId);
        if (span != null) {
            if (failed) {
                span.setStatus(StatusCode.ERROR);
            }
            span.end();
        }
    }
}
