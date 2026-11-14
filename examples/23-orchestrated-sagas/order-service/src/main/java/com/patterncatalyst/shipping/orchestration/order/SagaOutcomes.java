package com.patterncatalyst.shipping.orchestration.order;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.kafka.KafkaConstants;

import com.patterncatalyst.shipping.Topics;

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

    @Inject
    ProducerTemplate camel;

    public void completed(String orchestrator, String orderId) {
        if (orders.markPaid(orderId)) {
            registry.counter("saga.outcomes", "orchestrator", orchestrator, "outcome", "completed").increment();
        }
    }

    public void compensated(String orchestrator, String orderId) {
        if (orders.cancel(orderId)) {
            camel.sendBodyAndHeader("kafka:" + Topics.ORDERS_CANCELLED, "{\"orderId\":\"" + orderId + "\"}",
                    KafkaConstants.KEY, orderId);
            registry.counter("saga.outcomes", "orchestrator", orchestrator, "outcome", "compensated").increment();
        }
    }
}
