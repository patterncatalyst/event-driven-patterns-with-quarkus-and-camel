package com.patterncatalyst.shipping.correlation.payment;

import org.apache.camel.builder.RouteBuilder;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Runs once at start. The payment route starts only when the cancellation consumer has no lag: a restarted
 * payment-service must know what was cancelled while it was away before it takes any payment. A failing lag check is
 * retried, never treated as caught up.
 */
@ApplicationScoped
public class CancellationGateRoute extends RouteBuilder {

    @Override
    public void configure() {
        // A lag check that fails (an Admin call that times out at start) is tried again until it answers, so the
        // gate cannot end without starting the payment route.
        onException(Exception.class).maximumRedeliveries(-1).redeliveryDelay(1000).logRetryAttempted(true);

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
