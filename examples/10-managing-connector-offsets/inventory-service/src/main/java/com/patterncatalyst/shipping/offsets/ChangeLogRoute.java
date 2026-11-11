package com.patterncatalyst.shipping.offsets;

import org.apache.camel.builder.RouteBuilder;

import com.patterncatalyst.shipping.Topics;

import jakarta.enterprise.context.ApplicationScoped;

/** Reads the connector's change events and writes each one to the change log. */
@ApplicationScoped
public class ChangeLogRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("kafka:" + Topics.INVENTORY_ITEM_CHANGES + "?groupId=inventory-change-log")
                .routeId("change-log")
                .unmarshal().json(ChangeEnvelope.class)
                .bean("itemChangeRecorder", "record");
    }
}
