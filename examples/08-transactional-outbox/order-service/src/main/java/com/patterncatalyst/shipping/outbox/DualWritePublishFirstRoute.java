package com.patterncatalyst.shipping.outbox;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;

import com.patterncatalyst.shipping.Order;
import com.patterncatalyst.shipping.Topics;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Comparison, not the pattern: publish, then commit the order. When the commit fails the event has already
 * left, and it announces an order that does not exist.
 */
@ApplicationScoped
public class DualWritePublishFirstRoute extends RouteBuilder {

    static final String ENDPOINT = "direct:publish-first";

    @Override
    public void configure() {
        from(ENDPOINT)
                .routeId("dual-write-publish-first")
                .setVariable("order", body())
                .setBody(e -> OrderPlaced.of(e.getMessage().getBody(Order.class)))
                .setHeader(KafkaConstants.KEY, simple("${body.orderId}"))
                .marshal().json()
                .toF("kafka:%s", Topics.ORDERS_PLACED)
                .setBody(variable("order"))
                .bean("orderService", "save");
    }
}
