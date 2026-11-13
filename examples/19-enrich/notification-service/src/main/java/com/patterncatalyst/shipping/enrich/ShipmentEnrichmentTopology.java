package com.patterncatalyst.shipping.enrich;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.GlobalKTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;

import com.patterncatalyst.shipping.Topics;

import io.quarkus.kafka.client.serialization.ObjectMapperSerde;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/**
 * The stream-table form: every instance holds a full copy of the compacted carrier topic in a local store and joins
 * each shipment against it. A left join keeps a shipment whose carrier has no reference.
 */
@ApplicationScoped
public class ShipmentEnrichmentTopology {

    static final String CARRIER_STORE = "carriers";

    private final Enrichment enrichment;

    @Inject
    public ShipmentEnrichmentTopology(Enrichment enrichment) {
        this.enrichment = enrichment;
    }

    @Produces
    public Topology topology() {
        var references = new ObjectMapperSerde<>(CarrierReference.class);
        var scheduled = new ObjectMapperSerde<>(ShipmentScheduled.class);
        var enriched = new ObjectMapperSerde<>(EnrichedShipmentScheduled.class);

        StreamsBuilder builder = new StreamsBuilder();
        GlobalKTable<String, CarrierReference> carriers = builder.globalTable(Topics.SHIPPING_CARRIERS,
                Consumed.with(Serdes.String(), references),
                Materialized.<String, CarrierReference, KeyValueStore<Bytes, byte[]>>as(CARRIER_STORE)
                        .withKeySerde(Serdes.String()).withValueSerde(references));
        builder.stream(Topics.SHIPPING_SCHEDULED, Consumed.with(Serdes.String(), scheduled))
                .leftJoin(carriers,
                        (orderId, event) -> event.shipment().carrier().name(),
                        (event, reference) -> enrichment.merge(Enrichment.KAFKA_STREAMS, event, reference))
                .to(Topics.SHIPPING_SCHEDULED_ENRICHED, Produced.with(Serdes.String(), enriched));
        return builder.build();
    }
}
