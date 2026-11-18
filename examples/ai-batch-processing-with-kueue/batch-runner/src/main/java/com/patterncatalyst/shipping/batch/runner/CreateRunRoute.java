package com.patterncatalyst.shipping.batch.runner;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CreateRunRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("direct:create-run").routeId("create-run").routeConfigurationId("rest-errors")
                .bean("runService", "create")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(202));
    }
}
