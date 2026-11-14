package com.patterncatalyst.shipping.orchestration.inventory;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** The workflow's explicit undo: the same release the coordinator's compensation calls. */
@ApplicationScoped
public class ReleaseStockRoute extends RouteBuilder {

    @Inject
    ReservationService reservations;

    @Override
    public void configure() {
        from("direct:release-stock").routeId("release-stock")
                .unmarshal().json(JsonLibrary.Jackson, ReleaseStock.class)
                .process(e -> reservations.release(e.getMessage().getBody(ReleaseStock.class).orderId()));
    }
}
