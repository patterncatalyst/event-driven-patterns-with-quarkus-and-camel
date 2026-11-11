package com.patterncatalyst.shipping.orderservice;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;

import com.patterncatalyst.shipping.Topics;

public class OrderPlacedRoute extends RouteBuilder {

    static final String DIRECT = "direct:order-placed";

    @Override
    public void configure() {
        from(DIRECT)
                .routeId("publish-order-placed")
                .setHeader(KafkaConstants.KEY, simple("${body.orderId}"))
                .marshal().json()
                .to("kafka:" + Topics.ORDERS_PLACED);
    }
}
