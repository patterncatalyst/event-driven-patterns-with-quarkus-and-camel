package com.patterncatalyst.shipping.cache;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

import jakarta.enterprise.context.ApplicationScoped;

/** The only writer of the cache: it applies the order events that the connector publishes. */
@ApplicationScoped
public class CacheRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("kafka:{{cache.topics}}?groupId={{cache.group}}&autoOffsetReset=earliest")
                .routeId("order-cache")
                .unmarshal().json(JsonLibrary.Jackson, OrderView.class)
                .bean("orderCache", "apply");
    }
}
