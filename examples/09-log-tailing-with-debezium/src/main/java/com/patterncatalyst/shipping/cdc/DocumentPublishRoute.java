package com.patterncatalyst.shipping.cdc;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;

import com.patterncatalyst.shipping.Topics;

/** Publishes a document keyed by SKU; a missing body is published as a tombstone, which deletes the key on compaction. */
public class DocumentPublishRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("direct:publish-document")
                .routeId("publish-document")
                .choice()
                    .when(body().isNotNull()).marshal().json()
                .end()
                .setHeader(KafkaConstants.KEY, header("sku"))
                .to("kafka:" + Topics.INVENTORY_ITEMS);
    }
}
