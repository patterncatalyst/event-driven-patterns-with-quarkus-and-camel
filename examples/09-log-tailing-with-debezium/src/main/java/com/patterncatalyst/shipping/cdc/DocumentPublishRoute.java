package com.patterncatalyst.shipping.cdc;

import org.apache.camel.builder.RouteBuilder;

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
                .toD("kafka:" + Topics.INVENTORY_ITEMS + "?key=${header.sku}");
    }
}
