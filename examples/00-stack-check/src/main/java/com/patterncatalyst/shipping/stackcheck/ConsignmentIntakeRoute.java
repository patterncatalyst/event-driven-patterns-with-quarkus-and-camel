package com.patterncatalyst.shipping.stackcheck;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.patterncatalyst.shipping.Consignment;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;

/** Accepts a Consignment over HTTP, validates it and publishes it keyed by its id. */
public class ConsignmentIntakeRoute extends RouteBuilder {

    @Override
    public void configure() {
        // A body that is not valid JSON, or fails the Consignment constructor checks, never reaches Kafka.
        onException(JsonProcessingException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                .setBody(simple("rejected: ${exception.originalMessage}"));

        from("platform-http:/consignments?httpMethodRestrict=POST")
                .routeId("consignment-intake")
                .unmarshal().json(Consignment.class)
                .setHeader(KafkaConstants.KEY, simple("${body.id}"))
                .marshal().json()
                .to("kafka:consignments")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(202))
                .setBody(constant(""));
    }
}
