package com.patterncatalyst.shipping.batch.runner;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CreateRunRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("direct:create-run").routeId("create-run").routeConfigurationId("rest-errors")
                .unmarshal().json(JsonLibrary.Jackson, RunRequest.class)
                .bean("runService", "create")
                .marshal().json(JsonLibrary.Jackson)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(202))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"));
    }
}
