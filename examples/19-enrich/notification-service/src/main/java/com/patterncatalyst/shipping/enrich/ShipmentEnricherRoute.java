package com.patterncatalyst.shipping.enrich;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.model.dataformat.JsonLibrary;

import com.patterncatalyst.shipping.Topics;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The Content Enricher: for every ShipmentScheduled, ask shipping-service for the carrier and merge the answer into
 * the message. A failed call is retried three times, then the consumer seeks back and reads the record again
 * ({@code breakOnFirstError}). The switch {@code enricher.break-on-first-error=false} is demo code: it commits the
 * failed record, which is then never enriched.
 */
@ApplicationScoped
public class ShipmentEnricherRoute extends RouteBuilder {

    @Inject
    CarrierEnrichment carrierEnrichment;

    @Override
    public void configure() {
        errorHandler(defaultErrorHandler().maximumRedeliveries(3).redeliveryDelay(1000));

        from("kafka:" + Topics.SHIPPING_SCHEDULED + "?groupId=notification-enricher-camel&sessionTimeoutMs=10000"
                + "&breakOnFirstError={{enricher.break-on-first-error}}")
                .routeId("shipment-enricher")
                .unmarshal().json(JsonLibrary.Jackson, ShipmentScheduled.class)
                .setHeader(Exchange.HTTP_METHOD, constant("GET"))
                .enrich().simple("{{shipping-service.url}}/carriers/${body.shipment.carrier}")
                        .aggregationStrategy(carrierEnrichment)
                .setHeader(KafkaConstants.KEY, simple("${body.shipment.orderId}"))
                .marshal().json(JsonLibrary.Jackson)
                .to("kafka:" + Topics.SHIPPING_SCHEDULED_ENRICHED_CAMEL);
    }
}
