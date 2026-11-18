package com.patterncatalyst.shipping.batch.runner;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class GetRunRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("direct:get-run").routeId("get-run").routeConfigurationId("rest-errors")
                .setBody(header("id"))
                .bean("runService", "view")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200));
    }
}
