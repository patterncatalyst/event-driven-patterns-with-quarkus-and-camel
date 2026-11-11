package com.patterncatalyst.shipping.ordering;

import org.apache.camel.builder.RouteBuilder;

/** Puts the funnelled events back in stream sequence before they are applied. */
public class ShipmentResequenceRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("{{order.resequence.from}}")
                .routeId("shipment-resequence")
                .unmarshal().json(ShipmentEvent.class)
                .resequence(simple("${body.streamSequence}"))
                    .stream().timeout("{{order.resequence.timeout}}").capacity(1000)
                .to("direct:apply-shipment");
    }
}
