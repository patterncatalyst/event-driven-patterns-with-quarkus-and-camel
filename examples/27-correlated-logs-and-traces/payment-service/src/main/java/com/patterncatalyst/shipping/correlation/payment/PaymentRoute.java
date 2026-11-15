package com.patterncatalyst.shipping.correlation.payment;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

import com.patterncatalyst.shipping.Topics;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Takes a payment for every reserved order. The route starts only after the cancellations have been read (see
 * CancellationGateRoute), and breakOnFirstError makes a failed payment be read again instead of skipped.
 */
@ApplicationScoped
public class PaymentRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("kafka:" + Topics.INVENTORY_RESERVED
                + "?groupId=payment-reserved&autoOffsetReset=earliest&sessionTimeoutMs=10000&heartbeatIntervalMs=3000&breakOnFirstError=true")
                .routeId("take-payment")
                .autoStartup(false)
                .unmarshal().json(JsonLibrary.Jackson, InventoryReserved.class)
                .log("taking the payment for ${body.orderId}")
                .bean("paymentService", "take");
    }
}
