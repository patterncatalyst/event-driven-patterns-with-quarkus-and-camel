package com.patterncatalyst.shipping.orchestration.order;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.model.SagaPropagation;
import org.apache.camel.model.dataformat.JsonLibrary;

import com.patterncatalyst.shipping.Topics;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** The second step joins the saga it was started in, and fails if there is none. */
@ApplicationScoped
public class CamelTakePaymentRoute extends RouteBuilder {

    @Inject
    OrderService orders;

    @Override
    public void configure() {
        from("direct:camel-take-payment").routeId("camel-take-payment")
                .saga().propagation(SagaPropagation.MANDATORY)
                .process(e -> e.getMessage().setBody(orders.takeCommand(e.getMessage().getHeader("orderId", String.class))))
                .setHeader("type", constant("TakePayment"))
                .setHeader(KafkaConstants.KEY, header("orderId"))
                .marshal().json(JsonLibrary.Jackson)
                .to("kafka:" + Topics.PAYMENTS_COMMANDS);
    }
}
