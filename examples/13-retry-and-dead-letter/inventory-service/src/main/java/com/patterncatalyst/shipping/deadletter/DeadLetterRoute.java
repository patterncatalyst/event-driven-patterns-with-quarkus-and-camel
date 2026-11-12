package com.patterncatalyst.shipping.deadletter;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.patterncatalyst.shipping.Topics;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * The same consumer written with Camel (comparison, profile camel): redelivery and the dead letter channel are
 * declared on the route, and the original bytes go to the dead letter topic.
 */
@ApplicationScoped
public class DeadLetterRoute extends RouteBuilder {

    @ConfigProperty(name = "retry.max")
    int retryMax;

    @ConfigProperty(name = "dead-letter.camel.dead-letter-channel")
    boolean deadLetterChannel;

    @Override
    public void configure() {
        if (deadLetterChannel) {
            errorHandler(deadLetterChannel("kafka:" + Topics.ORDERS_PLACED_DLQ
                    + "?valueSerializer=" + ByteArraySerializer.class.getName())
                    .useOriginalBody().maximumRedeliveries(retryMax).redeliveryDelay(1000));
            // Without useOriginalBody here the dead letter would carry the parsed OrderPlaced and fail to serialize.
            onException(JsonProcessingException.class, IllegalArgumentException.class)
                    .maximumRedeliveries(0).useOriginalBody();
        }

        from("kafka:" + Topics.ORDERS_PLACED + "?groupId=inventory-camel&autoOffsetReset=earliest"
                + "&sessionTimeoutMs=10000&maxPollIntervalMs={{max-poll-interval-ms}}"
                + "&breakOnFirstError={{dead-letter.camel.break-on-first-error}}"
                + "&valueDeserializer=" + ByteArrayDeserializer.class.getName())
                .routeId("dead-letter")
                .autoStartup("{{dead-letter.camel.enabled}}")
                .unmarshal().json(JsonLibrary.Jackson, OrderPlaced.class)
                .bean(ReservationService.class, "reserve");
    }
}
