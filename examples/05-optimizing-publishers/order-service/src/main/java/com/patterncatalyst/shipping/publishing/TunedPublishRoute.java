package com.patterncatalyst.shipping.publishing;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;

import com.patterncatalyst.shipping.Topics;

/** Waits up to publisher.tuned.linger-ms for a batch of up to publisher.tuned.batch-size bytes, compressed. */
public class TunedPublishRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("direct:publish-tuned")
                .routeId("publish-tuned")
                .setHeader(KafkaConstants.KEY, simple("${body.orderId}"))
                .marshal().json()
                .to("kafka:" + Topics.ORDERS_PLACED + "?clientId=publisher-tuned"
                        + "&lingerMs={{publisher.tuned.linger-ms}}"
                        + "&producerBatchSize={{publisher.tuned.batch-size}}"
                        + "&compressionCodec={{publisher.tuned.compression}}");
    }
}
