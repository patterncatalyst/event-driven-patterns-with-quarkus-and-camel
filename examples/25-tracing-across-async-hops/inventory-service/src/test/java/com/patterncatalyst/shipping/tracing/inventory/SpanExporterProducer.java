package com.patterncatalyst.shipping.tracing.inventory;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/** Quarkus exports spans to every SpanExporter bean when {@code quarkus.otel.traces.exporter=cdi}. */
@ApplicationScoped
class SpanExporterProducer {

    @Produces
    @Singleton
    InMemorySpanExporter spans() {
        return InMemorySpanExporter.create();
    }
}
