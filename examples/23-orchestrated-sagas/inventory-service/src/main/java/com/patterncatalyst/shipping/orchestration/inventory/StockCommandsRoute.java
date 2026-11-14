package com.patterncatalyst.shipping.orchestration.inventory;

import org.apache.camel.builder.RouteBuilder;

import com.patterncatalyst.shipping.Topics;

import jakarta.enterprise.context.ApplicationScoped;

/** Reads the commands of the inventory topic and hands each to the route of its type. */
@ApplicationScoped
public class StockCommandsRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("kafka:" + Topics.INVENTORY_COMMANDS + "?groupId=inventory-service").routeId("stock-commands")
                .choice()
                    .when(header("type").isEqualTo("ReserveStock")).to("direct:reserve-stock")
                    .when(header("type").isEqualTo("ReleaseStock")).to("direct:release-stock")
                    .otherwise().log("ignored command ${header.type}")
                .end();
    }
}
