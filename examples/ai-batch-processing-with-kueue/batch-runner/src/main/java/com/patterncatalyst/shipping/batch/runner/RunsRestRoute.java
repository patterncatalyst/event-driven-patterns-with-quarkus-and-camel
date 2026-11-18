package com.patterncatalyst.shipping.batch.runner;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;

import jakarta.enterprise.context.ApplicationScoped;

/** The REST surface: POST /runs, POST /runs/{id}/replay and GET /runs/{id}. */
@ApplicationScoped
public class RunsRestRoute extends RouteBuilder {

    @Override
    public void configure() {
        restConfiguration().bindingMode(RestBindingMode.off);

        rest("/runs")
                .post().id("rest-create-run").consumes("application/json").produces("application/json")
                .to("direct:create-run")
                .post("/{id}/replay").id("rest-replay-run").produces("application/json")
                .to("direct:replay-run")
                .get("/{id}").id("rest-get-run").produces("application/json")
                .to("direct:get-run");
    }
}
