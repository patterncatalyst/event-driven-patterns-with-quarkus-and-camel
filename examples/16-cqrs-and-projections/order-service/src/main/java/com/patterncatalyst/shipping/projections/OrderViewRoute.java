package com.patterncatalyst.shipping.projections;

import java.time.Instant;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

import com.patterncatalyst.shipping.Topics;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The comparison: one Camel consumer, one group and one poll loop over all six topics, started with the
 * camel-projector profile. The route only routes by topic. The projector is the same bean, so it keeps the same guard.
 */
@ApplicationScoped
public class OrderViewRoute extends RouteBuilder {

    @Inject
    OrderViewProjector projector;

    @Override
    public void configure() {
        from("kafka:" + String.join(",", Topics.ORDERS_PLACED, Topics.ORDERS_CANCELLED, Topics.PAYMENTS_PROCESSED,
                Topics.SHIPPING_SCHEDULED, Topics.SHIPPING_IN_TRANSIT, Topics.SHIPPING_DELIVERED)
                + "?groupId=order-view-camel&autoOffsetReset=earliest&sessionTimeoutMs=10000")
                .routeId("order-view-camel")
                .autoStartup("{{projection.camel.enabled}}")
                .process(exchange -> exchange.setProperty("at",
                        Instant.ofEpochMilli(exchange.getIn().getHeader("CamelKafkaTimestamp", Long.class))))
                .choice()
                    .when(header("CamelKafkaTopic").isEqualTo(Topics.ORDERS_PLACED))
                        .unmarshal().json(JsonLibrary.Jackson, Events.OrderPlaced.class)
                        .bean(projector, "placed(${body}, ${exchangeProperty.at})")
                    .when(header("CamelKafkaTopic").isEqualTo(Topics.ORDERS_CANCELLED))
                        .unmarshal().json(JsonLibrary.Jackson, Events.OrderCancelled.class)
                        .bean(projector, "cancelled(${body}, ${exchangeProperty.at})")
                    .when(header("CamelKafkaTopic").isEqualTo(Topics.PAYMENTS_PROCESSED))
                        .unmarshal().json(JsonLibrary.Jackson, Events.PaymentProcessed.class)
                        .bean(projector, "paid(${body}, ${exchangeProperty.at})")
                    .when(header("CamelKafkaTopic").isEqualTo(Topics.SHIPPING_SCHEDULED))
                        .unmarshal().json(JsonLibrary.Jackson, Events.ShipmentScheduled.class)
                        .bean(projector, "scheduled(${body}, ${exchangeProperty.at})")
                    .when(header("CamelKafkaTopic").isEqualTo(Topics.SHIPPING_IN_TRANSIT))
                        .unmarshal().json(JsonLibrary.Jackson, Events.ShipmentInTransit.class)
                        .bean(projector, "inTransit(${body}, ${exchangeProperty.at})")
                    .otherwise()
                        .unmarshal().json(JsonLibrary.Jackson, Events.ShipmentDelivered.class)
                        .bean(projector, "delivered(${body}, ${exchangeProperty.at})")
                .end();
    }
}
