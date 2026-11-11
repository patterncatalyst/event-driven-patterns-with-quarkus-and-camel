package com.patterncatalyst.shipping.publishing;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;

import com.patterncatalyst.shipping.Topics;

/** Control: no waiting and no batch buffer, so every record is its own batch and its own request. */
public class UnbatchedPublishRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("direct:publish-unbatched")
                .routeId("publish-unbatched")
                .setHeader(KafkaConstants.KEY, simple("${body.orderId}"))
                .marshal().json()
                .to("kafka:" + Topics.ORDERS_PLACED + "?clientId=publisher-unbatched&lingerMs=0&producerBatchSize=0");
    }
}
