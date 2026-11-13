package com.patterncatalyst.shipping.enrich;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.Carrier;
import com.patterncatalyst.shipping.Topics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkus.kafka.client.serialization.ObjectMapperSerde;

/** The topology without a broker: records go in, the join result comes out, and no clock is involved. */
class EnrichmentTopologyTest {

    private static final ObjectMapperSerde<CarrierReference> REFERENCES = new ObjectMapperSerde<>(CarrierReference.class);
    private static final ObjectMapperSerde<ShipmentScheduled> SCHEDULED = new ObjectMapperSerde<>(ShipmentScheduled.class);
    private static final ObjectMapperSerde<EnrichedShipmentScheduled> ENRICHED = new ObjectMapperSerde<>(EnrichedShipmentScheduled.class);

    private SimpleMeterRegistry meters;
    private Enrichment enrichment;
    private TopologyTestDriver driver;
    private TestInputTopic<String, CarrierReference> references;
    private TestInputTopic<String, ShipmentScheduled> scheduled;
    private TestOutputTopic<String, EnrichedShipmentScheduled> enriched;

    @BeforeEach
    void start() {
        meters = new SimpleMeterRegistry();
        enrichment = new Enrichment(meters);
        open(new ShipmentEnrichmentTopology(enrichment).topology(), Topics.SHIPPING_SCHEDULED_ENRICHED);
    }

    @AfterEach
    void stop() {
        driver.close();
    }

    private void open(Topology topology, String output) {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "enrichment-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "unused:9092");
        props.put(StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, 0);
        driver = new TopologyTestDriver(topology, props);
        references = driver.createInputTopic(Topics.SHIPPING_CARRIERS, Serdes.String().serializer(), REFERENCES.serializer());
        scheduled = driver.createInputTopic(Topics.SHIPPING_SCHEDULED, Serdes.String().serializer(), SCHEDULED.serializer());
        enriched = driver.createOutputTopic(output, Serdes.String().deserializer(), ENRICHED.deserializer());
    }

    @Test
    void theLeftJoinEnrichesEachShipmentFromItsCarrier() {
        references.pipeInput("FASTLINE", Samples.fastline(Samples.FASTLINE_TEMPLATE));
        references.pipeInput("AIRCO", Samples.airco());
        scheduled.pipeInput("O-1", Samples.scheduled("O-1", Carrier.FASTLINE));
        scheduled.pipeInput("O-2", Samples.scheduled("O-2", Carrier.AIRCO));

        var out = enriched.readKeyValuesToMap();
        assertEquals("ROAD", out.get("O-1").carrierMode());
        assertEquals("https://track.fastline.example/TRK-O-1", out.get("O-1").trackingUrl());
        assertEquals("AIR", out.get("O-2").carrierMode());
        assertEquals("https://track.airco.example/TRK-O-2", out.get("O-2").trackingUrl());
    }

    @Test
    void aReferenceChangeAppliesOnlyToShipmentsProcessedAfterIt() {
        references.pipeInput("FASTLINE", Samples.fastline(Samples.FASTLINE_TEMPLATE));
        scheduled.pipeInput("O-1", Samples.scheduled("O-1", Carrier.FASTLINE));
        references.pipeInput("FASTLINE", Samples.fastline("https://track.fastline.example/v2/{trackingNumber}"));
        scheduled.pipeInput("O-2", Samples.scheduled("O-2", Carrier.FASTLINE));

        var first = enriched.readKeyValue();
        var second = enriched.readKeyValue();
        assertEquals("https://track.fastline.example/TRK-O-1", first.value.trackingUrl());
        assertEquals("https://track.fastline.example/v2/TRK-O-2", second.value.trackingUrl());
        assertTrue(enriched.isEmpty(), "the first output is not revised when the reference changes");
    }

    @Test
    void aCarrierWithoutAReferenceKeepsTheRecordAndCountsItApart() {
        references.pipeInput("FASTLINE", Samples.fastline(Samples.FASTLINE_TEMPLATE));
        scheduled.pipeInput("O-1", Samples.scheduled("O-1", Carrier.AIRCO));
        references.pipeInput("FASTLINE", (CarrierReference) null);
        scheduled.pipeInput("O-2", Samples.scheduled("O-2", Carrier.FASTLINE));

        var out = enriched.readKeyValuesToMap();
        assertEquals(2, out.size());
        assertNull(out.get("O-1").carrierMode());
        assertNull(out.get("O-1").trackingUrl());
        assertNull(out.get("O-2").carrierMode());
        assertEquals(2.0, meters.counter("shipment.enrichments", "path", "kafka-streams", "outcome", "no-reference").count());
    }

    @Test
    void anInnerJoinDropsTheShipmentWithoutAReference() {
        driver.close();
        StreamsBuilder builder = new StreamsBuilder();
        var table = builder.globalTable(Topics.SHIPPING_CARRIERS, Consumed.with(Serdes.String(), REFERENCES));
        builder.stream(Topics.SHIPPING_SCHEDULED, Consumed.with(Serdes.String(), SCHEDULED))
                .join(table, (orderId, event) -> event.shipment().carrier().name(),
                        (event, reference) -> enrichment.merge(Enrichment.KAFKA_STREAMS, event, reference))
                .to("inner", Produced.with(Serdes.String(), ENRICHED));
        open(builder.build(), "inner");

        references.pipeInput("FASTLINE", Samples.fastline(Samples.FASTLINE_TEMPLATE));
        scheduled.pipeInput("O-1", Samples.scheduled("O-1", Carrier.FASTLINE));
        scheduled.pipeInput("O-2", Samples.scheduled("O-2", Carrier.AIRCO));

        var out = enriched.readKeyValuesToMap();
        assertEquals(1, out.size());
        assertTrue(out.containsKey("O-1"));
    }

    @Test
    void aTableJoinOnTheRecordKeyMatchesNothingBecauseTheKeyIsTheOrderId() {
        driver.close();
        StreamsBuilder builder = new StreamsBuilder();
        var table = builder.table(Topics.SHIPPING_CARRIERS, Consumed.with(Serdes.String(), REFERENCES));
        builder.stream(Topics.SHIPPING_SCHEDULED, Consumed.with(Serdes.String(), SCHEDULED))
                .join(table, (event, reference) -> enrichment.merge(Enrichment.KAFKA_STREAMS, event, reference))
                .to("keyed", Produced.with(Serdes.String(), ENRICHED));
        open(builder.build(), "keyed");

        references.pipeInput("FASTLINE", Samples.fastline(Samples.FASTLINE_TEMPLATE));
        scheduled.pipeInput("O-1", Samples.scheduled("O-1", Carrier.FASTLINE));
        scheduled.pipeInput("O-2", Samples.scheduled("O-2", Carrier.AIRCO));

        assertTrue(enriched.isEmpty(), "a table join compares the record key with the table key, not a field of the value");
    }
}
