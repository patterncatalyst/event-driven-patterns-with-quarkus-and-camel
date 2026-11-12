package com.patterncatalyst.shipping.aggregate;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.patterncatalyst.shipping.Topics;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * The comparison: PaymentProcessed as a command through a Camel route, started with the camel-commands profile.
 * The route is an adapter. It decides nothing: the aggregate and the event store are the same beans. Redelivery on a
 * conflict calls the transactional bean again, which reloads and decides again.
 */
@ApplicationScoped
public class PaymentCommandRoute extends RouteBuilder {

    @ConfigProperty(name = "commands.camel.redeliveries", defaultValue = "3")
    int redeliveries;

    @Override
    public void configure() {
        onException(ConcurrencyConflict.class)
                .maximumRedeliveries(redeliveries).redeliveryDelay(50)
                .logRetryAttempted(true).retryAttemptedLogLevel(LoggingLevel.INFO);
        onException(CommandRefused.class)
                .handled(true)
                .log(LoggingLevel.INFO, "camel acknowledged a refused command: ${exception.message}");

        from("kafka:" + Topics.PAYMENTS_PROCESSED + "?groupId=order-payments-camel&autoOffsetReset=earliest&sessionTimeoutMs=10000")
                .routeId("payment-commands")
                .autoStartup("{{commands.camel.enabled}}")
                .unmarshal().json(JsonLibrary.Jackson, PaymentProcessed.class)
                .bean("orderTransaction", "markPaid");
    }
}
