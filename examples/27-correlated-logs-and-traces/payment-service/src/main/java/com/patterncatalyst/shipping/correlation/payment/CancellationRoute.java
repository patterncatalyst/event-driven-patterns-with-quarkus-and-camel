package com.patterncatalyst.shipping.correlation.payment;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

import com.patterncatalyst.shipping.Topics;

import jakarta.enterprise.context.ApplicationScoped;

/** Records every cancelled order, so that a payment for it can be refused. */
@ApplicationScoped
public class CancellationRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("kafka:" + Topics.ORDERS_CANCELLED
                + "?groupId=payment-cancelled&autoOffsetReset=earliest&sessionTimeoutMs=10000&heartbeatIntervalMs=3000&autoCommitIntervalMs=500&breakOnFirstError=true")
                .routeId("record-cancellation")
                .unmarshal().json(JsonLibrary.Jackson, OrderCancelled.class)
                .bean("paymentService", "recordCancelled");
    }
}
