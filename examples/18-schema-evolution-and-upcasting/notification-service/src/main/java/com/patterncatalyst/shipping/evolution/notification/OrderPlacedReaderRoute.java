package com.patterncatalyst.shipping.evolution.notification;

import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.patterncatalyst.shipping.Topics;
import com.patterncatalyst.shipping.evolution.avro.OrderPlaced;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The comparison: the same reader as a Camel route, started with the camel-reader profile. Quarkus configures the
 * Apicurio deserializer only for SmallRye channels, so the route names the deserializer and the registry itself.
 */
@ApplicationScoped
public class OrderPlacedReaderRoute extends RouteBuilder {

    @ConfigProperty(name = "evolution.registry-url")
    String registryUrl;

    @Inject
    Recorder recorder;

    @Override
    public void configure() {
        from("kafka:" + Topics.ORDERS_PLACED + "?groupId=notification-camel&autoOffsetReset=earliest&sessionTimeoutMs=10000"
                + "&valueDeserializer=io.apicurio.registry.serde.avro.AvroKafkaDeserializer"
                + "&additionalProperties.apicurio.registry.url=" + registryUrl
                + "&additionalProperties.apicurio.registry.use-specific-avro-reader=true")
                .routeId("order-placed-reader")
                .autoStartup("{{reader.camel.enabled}}")
                .process(exchange -> recorder.record(exchange.getIn().getBody(OrderPlaced.class), "camel"));
    }
}
