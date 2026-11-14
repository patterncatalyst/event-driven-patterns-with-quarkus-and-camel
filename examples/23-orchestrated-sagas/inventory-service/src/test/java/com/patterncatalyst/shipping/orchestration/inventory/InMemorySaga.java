package com.patterncatalyst.shipping.orchestration.inventory;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.saga.InMemorySagaService;

import jakarta.enterprise.context.ApplicationScoped;

/** Tests run without a coordinator: Camel's in-memory saga service stands in for it. */
@ApplicationScoped
public class InMemorySaga extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        getContext().addService(new InMemorySagaService());
    }
}
