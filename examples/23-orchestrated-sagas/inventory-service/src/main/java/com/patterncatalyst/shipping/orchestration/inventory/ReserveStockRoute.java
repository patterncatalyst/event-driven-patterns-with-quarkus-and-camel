package com.patterncatalyst.shipping.orchestration.inventory;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.SagaPropagation;
import org.apache.camel.model.dataformat.JsonLibrary;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * ReserveStock joins the saga the command carries (the Long-Running-Action header) and registers its compensation
 * with the coordinator; without the header it runs plain, which is how the workflow form uses it.
 */
@ApplicationScoped
public class ReserveStockRoute extends RouteBuilder {

    @Inject
    ReservationService reservations;

    @Override
    public void configure() {
        from("direct:reserve-stock").routeId("reserve-stock")
                .unmarshal().json(JsonLibrary.Jackson, ReserveStock.class)
                .process(e -> e.getMessage().setHeader("orderId", e.getMessage().getBody(ReserveStock.class).orderId()))
                .saga().propagation(SagaPropagation.SUPPORTS)
                    .compensation("direct:release-compensation")
                    .option("orderId", simple("${header.orderId}"))
                .process(e -> reservations.reserve(e.getMessage().getBody(ReserveStock.class))
                        .ifPresentOrElse(e.getMessage()::setBody, () -> e.getMessage().setBody(null)))
                .filter(body().isNotNull())
                .to("direct:reply");
    }
}
