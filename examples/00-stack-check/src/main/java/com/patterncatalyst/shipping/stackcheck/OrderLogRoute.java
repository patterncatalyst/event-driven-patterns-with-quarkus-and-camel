package com.patterncatalyst.shipping.stackcheck;

import com.patterncatalyst.shipping.Topics;
import org.apache.camel.builder.RouteBuilder;

/** Consumes the order-placed topic and logs each record. */
public class OrderLogRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("kafka:" + Topics.ORDERS_PLACED + "?groupId=stack-check")
                .routeId("order-log")
                .log("order received key=${header.kafka.KEY} body=${body}");
    }
}
