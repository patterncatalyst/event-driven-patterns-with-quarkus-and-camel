package com.patterncatalyst.shipping.payments;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

import com.patterncatalyst.shipping.Topics;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Comparison, profile camel-transacted. The Camel Kafka producer opens a transaction per exchange and commits it when
 * the exchange finishes, so each output is atomic. The consumer is an ordinary one: it commits its own offsets on a
 * timer, outside that transaction. The record key travels in the Kafka key header, which the consumer sets and the
 * producer reads.
 */
@ApplicationScoped
public class CamelTransactedPaymentRoute extends RouteBuilder {

    @Inject
    CrashSwitch crash;

    @Override
    public void configure() {
        from("kafka:" + Topics.INVENTORY_RESERVED + "?groupId=payment-camel&autoOffsetReset=earliest&sessionTimeoutMs=10000")
                .routeId("camel-transacted-payment")
                .autoStartup("{{camel.route.enabled}}")
                .unmarshal().json(JsonLibrary.Jackson, InventoryReserved.class)
                .process(exchange -> {
                    InventoryReserved reserved = exchange.getMessage().getBody(InventoryReserved.class);
                    if (crash.armedFor(reserved.orderId())) {
                        crash.halt().await().indefinitely();
                    }
                    exchange.getMessage().setBody(PaymentProcessed.of(reserved));
                })
                .marshal().json(JsonLibrary.Jackson)
                .to("kafka:" + Topics.PAYMENTS_PROCESSED + "?transacted=true&transactionalId=camel-payment-tx");
    }
}
