package com.patterncatalyst.shipping.saga.payment;

import org.apache.camel.builder.RouteBuilder;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Runs once at start. The payment route starts only when the cancellation consumer has no lag: a restarted
 * payment-service must know what was cancelled while it was away before it takes any payment.
 */
@ApplicationScoped
public class CancellationGateRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("timer:cancellation-gate?repeatCount=1")
                .routeId("cancellation-gate")
                .loopDoWhile(simple("${bean:cancellationLag?method=behind}"))
                    .delay(500)
                    .end() // the delay
                .end() // the loop
                .log("cancellations are caught up, starting the payment route")
                .to("controlbus:route?routeId=take-payment&action=start");
    }
}
