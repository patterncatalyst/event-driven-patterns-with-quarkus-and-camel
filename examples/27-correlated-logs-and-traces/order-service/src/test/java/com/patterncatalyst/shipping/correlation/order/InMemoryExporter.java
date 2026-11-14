package com.patterncatalyst.shipping.correlation.order;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/** Keeps the finished spans in memory: the OpenTelemetry extension uses any SpanExporter bean in place of OTLP. */
@ApplicationScoped
class InMemoryExporter {

    @Produces
    @Singleton
    InMemorySpanExporter exporter() {
        return InMemorySpanExporter.create();
    }
}
