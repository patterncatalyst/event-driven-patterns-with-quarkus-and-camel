package com.patterncatalyst.shipping.batch.worker;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

import org.jboss.logging.Logger;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Runs a step as a span that is a child of the run's span. The parent arrives in the TRACEPARENT environment variable, the
 * way the OpenTelemetry specification carries a context into a process that has no request.
 */
@ApplicationScoped
public class StepSpan {

    private static final Logger LOG = Logger.getLogger(StepSpan.class);

    /** Environment names are the upper-case form of the header name, with underscores for hyphens. */
    private static final TextMapGetter<Map<String, String>> ENV = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> env) {
            return env.keySet();
        }

        @Override
        public String get(Map<String, String> env, String key) {
            return env == null ? null : env.get(key.toUpperCase().replace('-', '_'));
        }
    };

    private final Tracer tracer;
    private final OpenTelemetry telemetry;
    private final WorkerEnv env;

    StepSpan(Tracer tracer, OpenTelemetry telemetry, WorkerEnv env) {
        this.tracer = tracer;
        this.telemetry = telemetry;
        this.env = env;
    }

    /** Runs the body inside the span and returns its exit code. An exception is exit code 1. */
    public int run(String name, Map<String, String> attributes, IntSupplier body) {
        Context parent = W3CTraceContextPropagator.getInstance().extract(Context.root(), env.all(), ENV);
        var builder = tracer.spanBuilder(name).setParent(parent);
        attributes.forEach(builder::setAttribute);
        Span span = builder.startSpan();
        try (Scope ignored = span.makeCurrent()) {
            int code = body.getAsInt();
            span.setAttribute("exit.code", code);
            if (code != 0) {
                span.setStatus(StatusCode.ERROR);
            }
            return code;
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            LOG.errorf(e, "%s failed", name);
            return 1;
        } finally {
            span.end();
        }
    }

    /** Pushes the spans and log records out before the process exits; the pod would otherwise end with them still queued. */
    public void flush() {
        try {
            if (telemetry instanceof io.opentelemetry.sdk.OpenTelemetrySdk sdk) {
                sdk.getSdkTracerProvider().forceFlush().join(10, TimeUnit.SECONDS);
                sdk.getSdkLoggerProvider().forceFlush().join(10, TimeUnit.SECONDS);
            }
        } catch (RuntimeException e) {
            LOG.warn("telemetry flush failed", e);
        }
    }
}
