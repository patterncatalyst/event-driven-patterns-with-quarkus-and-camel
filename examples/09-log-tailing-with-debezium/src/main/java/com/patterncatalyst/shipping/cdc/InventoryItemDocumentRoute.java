package com.patterncatalyst.shipping.cdc;

import org.apache.camel.builder.RouteBuilder;

import com.patterncatalyst.shipping.Topics;

/**
 * Reads the change envelopes Kafka Connect writes for the legacy table and republishes each row as an
 * InventoryItem document keyed by SKU.
 */
public class InventoryItemDocumentRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("kafka:" + Topics.INVENTORY_ITEM_CHANGES + "?groupId=inventory-documents")
                .routeId("item-changes-to-documents")
                .autoStartup("{{cdc.connect.enabled}}")
                // Debezium follows every delete event with a record that has a key and no value.
                .filter(body().isNotNull())
                .unmarshal().json(ChangeEnvelope.class)
                .setHeader("sku", simple("${body.sku}"))
                .setHeader("cdcMode", constant("connect"))
                .bean("itemDocuments", "fromEnvelope")
                .to("direct:publish-document");
    }
}
