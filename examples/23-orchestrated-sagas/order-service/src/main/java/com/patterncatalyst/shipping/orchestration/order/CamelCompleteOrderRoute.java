package com.patterncatalyst.shipping.orchestration.order;

import org.apache.camel.builder.RouteBuilder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** The coordinator calls this when the saga completes. */
@ApplicationScoped
public class CamelCompleteOrderRoute extends RouteBuilder {

    @Inject
    SagaOutcomes outcomes;

    @Override
    public void configure() {
        from("direct:camel-complete-order").routeId("camel-complete-order")
                .process(e -> outcomes.completed("camel-lra", e.getMessage().getHeader("orderId", String.class)));
    }
}
