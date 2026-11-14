package com.patterncatalyst.shipping.orchestration.order;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** The two ways a saga ends, for both orchestrators: the order changes once, the metric counts once. */
@ApplicationScoped
public class SagaOutcomes {

    @Inject
    OrderService orders;

    @Inject
    MeterRegistry registry;

    public void completed(String orchestrator, String orderId) {
        if (orders.markPaid(orderId)) {
            registry.counter("saga.outcomes", "orchestrator", orchestrator, "outcome", "completed").increment();
        }
    }

    public void compensated(String orchestrator, String orderId) {
        if (orders.cancel(orderId)) {
            registry.counter("saga.outcomes", "orchestrator", orchestrator, "outcome", "compensated").increment();
        }
    }
}
