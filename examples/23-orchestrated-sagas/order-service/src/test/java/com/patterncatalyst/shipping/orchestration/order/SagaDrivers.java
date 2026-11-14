package com.patterncatalyst.shipping.orchestration.order;

import org.apache.camel.builder.RouteBuilder;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Camel's in-memory saga service keeps a saga on the exchange that started it and does not carry it in a header, so a
 * reply read from Kafka cannot be joined to it. These routes finish the saga on the exchange that started it, the way
 * the replies route does with the header and the coordinator.
 */
@ApplicationScoped
public class SagaDrivers extends RouteBuilder {

    @Override
    public void configure() {
        from("direct:test-complete").to("direct:camel-saga").to("saga:complete");
        from("direct:test-compensate").to("direct:camel-saga").to("saga:compensate");
        from("direct:test-pay").to("direct:camel-saga").to("direct:camel-take-payment").to("saga:complete");
    }
}
