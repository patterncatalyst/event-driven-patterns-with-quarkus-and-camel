package com.patterncatalyst.shipping.resequencing;

import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;

/** Parses an event, sets the headers the resequencer reads, and hands it to the selected form. */
@ApplicationScoped
public class ShipmentIntakeRoute extends RouteBuilder {

    @ConfigProperty(name = "resequencer.mode")
    String mode;
    @ConfigProperty(name = "resequencer.sequence-by")
    String sequenceBy;

    @Override
    public void configure() {
        var intake = from("direct:intake")
                .routeId("shipment-intake")
                .unmarshal().json(ShipmentEvent.class)
                .setHeader("orderId", simple("${body.orderId}"))
                .setHeader("seq", simple("order".equals(sequenceBy) ? "${body.sequence}" : "${body.globalSequence}", Long.class))
                .setHeader("sortKey", simple("${body.sortKey()}"))
                .log("arrived g=${body.globalSequence} order=${body.orderId} seq=${body.sequence}");
        if ("none".equals(mode)) {
            intake.to("direct:apply");
        } else {
            intake.to("direct:" + mode);
        }
    }
}
