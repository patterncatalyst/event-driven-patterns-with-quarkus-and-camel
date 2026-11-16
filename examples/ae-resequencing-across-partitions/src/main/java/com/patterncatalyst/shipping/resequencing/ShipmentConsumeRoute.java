package com.patterncatalyst.shipping.resequencing;

import org.apache.camel.builder.RouteBuilder;

import com.patterncatalyst.shipping.Topics;

/**
 * Reads the three per-event topics in one group. Keys are whatever the producers chose, so one order's events sit in
 * different partitions of different topics and arrive in no particular order.
 */
public class ShipmentConsumeRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("kafka:" + Topics.SHIPPING_SCHEDULED + "," + Topics.SHIPPING_IN_TRANSIT + "," + Topics.SHIPPING_DELIVERED
                + "?groupId={{resequencer.group}}")
                .routeId("shipment-consume")
                .to("direct:intake");
    }
}
