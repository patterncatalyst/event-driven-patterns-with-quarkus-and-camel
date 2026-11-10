package com.patterncatalyst.shipping.stackcheck;

import org.apache.camel.builder.RouteBuilder;

/** Consumes the consignments topic and logs each record. */
public class ConsignmentLogRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("kafka:consignments?groupId=stack-check")
                .routeId("consignment-log")
                .log("consignment received key=${header.kafka.KEY} body=${body}");
    }
}
