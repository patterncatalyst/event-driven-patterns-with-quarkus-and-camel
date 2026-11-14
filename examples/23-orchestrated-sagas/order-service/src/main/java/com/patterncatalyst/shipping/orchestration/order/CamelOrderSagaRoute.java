package com.patterncatalyst.shipping.orchestration.order;

import java.time.Duration;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.model.SagaCompletionMode;
import org.apache.camel.model.SagaPropagation;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.patterncatalyst.shipping.Topics;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The start of the orchestrator written with the Camel Saga EIP. It opens the saga at the coordinator and sends
 * ReserveStock; the saga stays open (MANUAL) until the replies route completes or compensates it, and the
 * coordinator cancels it when the time limit passes.
 */
@ApplicationScoped
public class CamelOrderSagaRoute extends RouteBuilder {

    @Inject
    OrderService orders;

    @ConfigProperty(name = "saga.timeout")
    String timeout;

    @Override
    public void configure() {
        from("direct:camel-saga").routeId("camel-saga-start")
                .saga().propagation(SagaPropagation.REQUIRES_NEW).completionMode(SagaCompletionMode.MANUAL)
                    .timeout(Duration.parse(timeout))
                    .compensation("direct:camel-cancel-order")
                    .completion("direct:camel-complete-order")
                    .option("orderId", simple("${header.orderId}"))
                .process(e -> e.getMessage().setBody(orders.reserveCommand(e.getMessage().getHeader("orderId", String.class))))
                .setHeader("type", constant("ReserveStock"))
                .setHeader(KafkaConstants.KEY, header("orderId"))
                .marshal().json(JsonLibrary.Jackson)
                .to("kafka:" + Topics.INVENTORY_COMMANDS);
    }
}
