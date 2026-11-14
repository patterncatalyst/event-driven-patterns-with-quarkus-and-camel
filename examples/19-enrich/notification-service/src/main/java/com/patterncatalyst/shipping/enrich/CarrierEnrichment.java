package com.patterncatalyst.shipping.enrich;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Merges the HTTP answer of shipping-service into the shipment, with the same merge the topology uses. */
@ApplicationScoped
public class CarrierEnrichment implements AggregationStrategy {

    @Inject
    Enrichment enrichment;

    @Inject
    ObjectMapper mapper;

    @Override
    public Exchange aggregate(Exchange original, Exchange resource) {
        ShipmentScheduled scheduled = original.getIn().getBody(ShipmentScheduled.class);
        try {
            CarrierReference reference = resource == null ? null
                    : mapper.readValue(resource.getIn().getBody(String.class), CarrierReference.class);
            original.getIn().setBody(enrichment.merge(Enrichment.CAMEL, scheduled, reference));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return original;
    }
}
