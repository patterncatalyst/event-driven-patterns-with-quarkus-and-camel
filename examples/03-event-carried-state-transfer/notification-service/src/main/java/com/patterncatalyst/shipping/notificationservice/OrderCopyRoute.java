package com.patterncatalyst.shipping.notificationservice;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;

/** One consumer route per topic. An event that cannot be applied is logged and skipped. */
abstract class OrderCopyRoute extends RouteBuilder {

    private final String topic;
    private final String routeId;

    OrderCopyRoute(String topic, String routeId) {
        this.topic = topic;
        this.routeId = routeId;
    }

    @Override
    public void configure() {
        onException(IllegalArgumentException.class, JsonProcessingException.class)
                .handled(true)
                .log(LoggingLevel.WARN, "skipped record on " + topic + ": ${exception.message}");

        from("kafka:" + topic + "?groupId=notification-order-copy")
                .routeId(routeId)
                .unmarshal().json(OrderStateEvent.class)
                .bean(OrderCopies.class, "apply");
    }
}
