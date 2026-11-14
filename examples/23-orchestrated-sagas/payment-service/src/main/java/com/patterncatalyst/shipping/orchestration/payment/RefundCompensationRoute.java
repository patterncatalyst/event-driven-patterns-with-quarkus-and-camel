package com.patterncatalyst.shipping.orchestration.payment;

import org.apache.camel.builder.RouteBuilder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** The coordinator calls this over HTTP with the option set at the join. */
@ApplicationScoped
public class RefundCompensationRoute extends RouteBuilder {

    @Inject
    PaymentService payments;

    @Override
    public void configure() {
        from("direct:refund-compensation").routeId("refund-compensation")
                .log("compensation: refund payment for ${header.orderId}")
                .process(e -> payments.refund(e.getMessage().getHeader("orderId", String.class)));
    }
}
