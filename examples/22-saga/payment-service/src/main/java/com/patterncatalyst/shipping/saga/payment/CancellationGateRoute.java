package com.patterncatalyst.shipping.saga.payment;

import org.apache.camel.builder.RouteBuilder;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Runs once at start. The cancellations are read to the end of the topic first, and only then does the payment route
 * start: a restarted payment-service must know what was cancelled while it was away before it takes any payment.
 */
@ApplicationScoped
public class CancellationGateRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("timer:cancellation-gate?repeatCount=1")
                .routeId("cancellation-gate")
                .bean(CancellationReplay.class, "replay")
                .to("controlbus:route?routeId=take-payment&action=start");
    }
}
