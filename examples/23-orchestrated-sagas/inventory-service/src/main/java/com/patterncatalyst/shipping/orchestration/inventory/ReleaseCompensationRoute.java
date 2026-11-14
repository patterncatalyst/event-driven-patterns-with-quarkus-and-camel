package com.patterncatalyst.shipping.orchestration.inventory;

import org.apache.camel.builder.RouteBuilder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** The coordinator calls this over HTTP with the option set at the join. */
@ApplicationScoped
public class ReleaseCompensationRoute extends RouteBuilder {

    @Inject
    ReservationService reservations;

    @Override
    public void configure() {
        from("direct:release-compensation").routeId("release-compensation")
                .log("compensation: release stock for ${header.orderId}")
                .process(e -> reservations.release(e.getMessage().getHeader("orderId", String.class)));
    }
}
