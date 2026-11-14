package com.patterncatalyst.shipping.join;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;

/** One counter, order.readiness, by path (streams or camel) and outcome (ready or overdue). */
@ApplicationScoped
public class ReadinessMetrics {

    private final MeterRegistry registry;

    ReadinessMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void count(String path, Readiness readiness) {
        registry.counter("order.readiness", "path", path, "outcome", readiness.ready() ? "ready" : "overdue").increment();
    }
}
