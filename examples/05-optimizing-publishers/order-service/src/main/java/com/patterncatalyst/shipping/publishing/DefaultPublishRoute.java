package com.patterncatalyst.shipping.publishing;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;

import com.patterncatalyst.shipping.Topics;

/** Kafka's own defaults for batching and compression; only the client id is set. */
public class DefaultPublishRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("direct:publish-default")
                .routeId("publish-default")
                .setHeader(KafkaConstants.KEY, simple("${body.orderId}"))
                .marshal().json()
                .to("kafka:" + Topics.ORDERS_PLACED + "?clientId=publisher-default");
    }
}
