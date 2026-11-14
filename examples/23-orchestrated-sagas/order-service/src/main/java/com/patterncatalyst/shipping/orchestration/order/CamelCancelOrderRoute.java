package com.patterncatalyst.shipping.orchestration.order;

import org.apache.camel.builder.RouteBuilder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** The coordinator calls this when it cancels the saga, including when the time limit passes. */
@ApplicationScoped
public class CamelCancelOrderRoute extends RouteBuilder {

    @Inject
    SagaOutcomes outcomes;

    @Override
    public void configure() {
        from("direct:camel-cancel-order").routeId("camel-cancel-order")
                .process(e -> outcomes.compensated("camel-lra", e.getMessage().getHeader("orderId", String.class)));
    }
}
