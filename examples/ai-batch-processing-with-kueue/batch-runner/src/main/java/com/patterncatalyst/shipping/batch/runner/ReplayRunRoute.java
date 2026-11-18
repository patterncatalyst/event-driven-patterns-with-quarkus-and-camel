package com.patterncatalyst.shipping.batch.runner;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ReplayRunRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("direct:replay-run").routeId("replay-run").routeConfigurationId("rest-errors")
                .setBody(header("id"))
                .bean("runService", "replay")
                .marshal().json(JsonLibrary.Jackson)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(202))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"));
    }
}
