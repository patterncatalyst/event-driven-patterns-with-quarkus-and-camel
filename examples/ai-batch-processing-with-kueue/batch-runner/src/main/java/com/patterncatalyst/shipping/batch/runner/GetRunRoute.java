package com.patterncatalyst.shipping.batch.runner;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class GetRunRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("direct:get-run").routeId("get-run").routeConfigurationId("rest-errors")
                .setBody(header("id"))
                .bean("runService", "view")
                .marshal().json(JsonLibrary.Jackson)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"));
    }
}
