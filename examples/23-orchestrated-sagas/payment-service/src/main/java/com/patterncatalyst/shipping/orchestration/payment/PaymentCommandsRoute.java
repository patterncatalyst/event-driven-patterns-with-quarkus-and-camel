package com.patterncatalyst.shipping.orchestration.payment;

import org.apache.camel.builder.RouteBuilder;

import com.patterncatalyst.shipping.Topics;

import jakarta.enterprise.context.ApplicationScoped;

/** Reads the commands of the payment topic and hands each to the route of its type. */
@ApplicationScoped
public class PaymentCommandsRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("kafka:" + Topics.PAYMENTS_COMMANDS + "?groupId=payment-service").routeId("payment-commands")
                .choice()
                    .when(header("type").isEqualTo("TakePayment")).to("direct:take-payment")
                    .when(header("type").isEqualTo("RefundPayment")).to("direct:refund-payment")
                    .otherwise().log("ignored command ${header.type}")
                .end();
    }
}
