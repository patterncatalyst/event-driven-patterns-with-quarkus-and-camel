package com.patterncatalyst.shipping.cdc;

import java.util.Map;

import org.apache.camel.builder.RouteBuilder;

/**
 * One row from the embedded engine: the body is the row after the change, not an envelope, and the operation
 * and the key are headers.
 */
public class EmbeddedRowRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("direct:embedded-row")
                .routeId("embedded-row")
                .setHeader("sku").simple("${header.CamelDebeziumKey.getString('item_sku')}")
                .setHeader("cdcMode").constant("embedded")
                .choice()
                    // A delete has no body; the old row is in the CamelDebeziumBefore header.
                    .when(header("CamelDebeziumOperation").isEqualTo("d"))
                        .setBody(simple("${null}"))
                    .otherwise()
                        .convertBodyTo(Map.class)
                        .bean("itemDocuments", "fromMap")
                .end()
                .to("direct:publish-document");
    }
}
