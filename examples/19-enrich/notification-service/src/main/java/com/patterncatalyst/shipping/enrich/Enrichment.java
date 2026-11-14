package com.patterncatalyst.shipping.enrich;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The merge both forms share: the same function turns a shipment and a carrier reference into the enriched record, so
 * the only difference between the Camel route and the topology is where the reference comes from.
 */
@ApplicationScoped
public class Enrichment {

    public static final String KAFKA_STREAMS = "kafka-streams";
    public static final String CAMEL = "camel";

    private final MeterRegistry meters;

    @Inject
    public Enrichment(MeterRegistry meters) {
        this.meters = meters;
    }

    /** A null reference means the owner has none for this carrier; the record is kept and carries no mode and no link. */
    public EnrichedShipmentScheduled merge(String path, ShipmentScheduled scheduled, CarrierReference reference) {
        boolean found = reference != null;
        meters.counter("shipment.enrichments", "path", path, "outcome", found ? "enriched" : "no-reference").increment();
        if (!found) {
            return new EnrichedShipmentScheduled(scheduled.shipment(), null, null);
        }
        String url = reference.trackingUrlTemplate().replace("{trackingNumber}", scheduled.shipment().trackingNumber());
        return new EnrichedShipmentScheduled(scheduled.shipment(), reference.mode(), url);
    }
}
