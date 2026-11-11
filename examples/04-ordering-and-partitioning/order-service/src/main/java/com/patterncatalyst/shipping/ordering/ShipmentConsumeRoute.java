package com.patterncatalyst.shipping.ordering;

import org.apache.camel.builder.RouteBuilder;

import com.patterncatalyst.shipping.Topics;

/**
 * Reads this replica's partitions of the shipment stream. Without RESEQUENCE the event is applied
 * as it arrives; with it, the event goes through the per-order buffer.
 */
public class ShipmentConsumeRoute extends RouteBuilder {

    @Override
    public void configure() {
        // A concurrent first insert for the same order loses the race once; a retry sees the row.
        onException(Exception.class).maximumRedeliveries(3).redeliveryDelay(200);

        from("kafka:" + Topics.SHIPPING_SHIPMENTS + "?groupId={{order.group}}&clientId={{order.replica}}")
                .routeId("shipment-consume")
                .choice()
                    .when(simple("{{order.resequence}}"))
                        .bean("shipmentBuffer", "accept")
                    .otherwise()
                        .unmarshal().json(ShipmentEvent.class)
                        .bean("shipmentApplier", "apply")
                .end();
    }
}
