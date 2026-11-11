package com.patterncatalyst.shipping.cdc;

import org.apache.camel.builder.RouteBuilder;

/**
 * The same capture without Kafka Connect: Debezium runs inside the service as a library, with its own
 * replication slot and a file for its offsets. It starts only when the embedded profile is on.
 */
public class EmbeddedCdcRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("debezium-postgres:inventory-embedded"
                + "?databaseHostname={{cdc.pg.host}}&databasePort=5432"
                + "&databaseUser={{cdc.pg.user}}&databasePassword={{cdc.pg.password}}&databaseDbname=edp"
                + "&topicPrefix=embedded&pluginName=pgoutput"
                + "&slotName=edp_inventory_embedded&publicationName=edp_inventory_embedded"
                + "&publicationAutocreateMode=filtered&tableIncludeList=inventory.inventory_item"
                + "&offsetStorageFileName={{cdc.embedded.offsets}}&offsetFlushIntervalMs=1000")
                .routeId("embedded-cdc")
                .autoStartup("{{cdc.embedded.enabled}}")
                .to("direct:embedded-row");
    }
}
