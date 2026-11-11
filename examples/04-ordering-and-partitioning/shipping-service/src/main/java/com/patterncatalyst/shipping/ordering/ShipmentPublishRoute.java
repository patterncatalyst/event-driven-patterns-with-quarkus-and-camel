package com.patterncatalyst.shipping.ordering;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.processor.aggregate.GroupedBodyAggregationStrategy;

import com.patterncatalyst.shipping.Topics;

/** Publishes the three shipment events of an order, in lifecycle order, keyed by the configured strategy. */
public class ShipmentPublishRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("platform-http:/shipments/{orderId}?httpMethodRestrict=POST")
                .routeId("shipment-publish")
                .bean("shipmentPlanner", "plan")
                .split(body()).aggregationStrategy(new GroupedBodyAggregationStrategy())
                    .setProperty("event", body())
                    .setHeader(KafkaConstants.KEY, method("partitionKeyChooser", "keyFor"))
                    .marshal().json()
                    .to("kafka:" + Topics.SHIPPING_SHIPMENTS + "?synchronous=true&recordMetadata=true")
                    .bean("receipts", "of")
                .end()
                .marshal().json()
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"));
    }
}
