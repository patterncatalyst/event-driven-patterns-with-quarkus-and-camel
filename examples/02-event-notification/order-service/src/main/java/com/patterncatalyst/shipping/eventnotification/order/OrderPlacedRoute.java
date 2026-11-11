package com.patterncatalyst.shipping.eventnotification.order;

import org.apache.camel.builder.RouteBuilder;

import com.patterncatalyst.shipping.Topics;

/** Publishes OrderPlaced as JSON to the order-placed topic, keyed by order id. */
public class OrderPlacedRoute extends RouteBuilder {

    static final String ENDPOINT = "direct:order-placed";

    @Override
    public void configure() {
        from(ENDPOINT)
                .routeId("order-placed-publisher")
                .marshal().json()
                .to("kafka:" + Topics.ORDERS_PLACED);
    }
}
