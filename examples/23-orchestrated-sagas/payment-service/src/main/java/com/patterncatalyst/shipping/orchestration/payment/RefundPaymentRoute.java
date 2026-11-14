package com.patterncatalyst.shipping.orchestration.payment;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** The workflow's explicit undo: the same refund the coordinator's compensation calls. */
@ApplicationScoped
public class RefundPaymentRoute extends RouteBuilder {

    @Inject
    PaymentService payments;

    @Override
    public void configure() {
        from("direct:refund-payment").routeId("refund-payment")
                .unmarshal().json(JsonLibrary.Jackson, RefundPayment.class)
                .process(e -> payments.refund(e.getMessage().getBody(RefundPayment.class).orderId()));
    }
}
