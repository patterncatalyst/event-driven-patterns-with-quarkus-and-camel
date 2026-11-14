package com.patterncatalyst.shipping.pickups;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.patterncatalyst.shipping.Carrier;
import com.patterncatalyst.shipping.Shipment;
import com.patterncatalyst.shipping.ShipmentStatus;
import com.patterncatalyst.shipping.Topics;
import io.quarkus.kafka.client.serialization.ObjectMapperSerde;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The topology on its own, with the Kafka Streams test driver: no broker, no wall clock. Event time is the only clock,
 * so every test sets it from the payload. The day is yesterday, as everywhere in the example.
 */
class PickupTopologyTest {

    static final ObjectMapper MAPPER = JsonMapper.builder().findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    static final LocalDate DAY = LocalDate.now(ZoneOffset.UTC).minusDays(1);

    TopologyTestDriver driver;
    TestInputTopic<String, ShipmentScheduled> in;
    TestOutputTopic<String, PickupList> out;

    void start(boolean updates) {
        start(PickupTopology.build(MAPPER, updates, true));
    }

    void start(Topology topology) {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "pickup-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "unused:9092");
        props.put(StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, 0);
        driver = new TopologyTestDriver(topology, props);
        in = driver.createInputTopic(Topics.SHIPPING_SCHEDULED, Serdes.String().serializer(),
                new ObjectMapperSerde<>(ShipmentScheduled.class, MAPPER).serializer());
        out = driver.createOutputTopic(Topics.SHIPPING_PICKUP_LISTS, Serdes.String().deserializer(),
                new ObjectMapperSerde<>(PickupList.class, MAPPER).deserializer());
    }

    @AfterEach
    void close() {
        if (driver != null) {
            driver.close();
        }
    }

    static Instant at(String hhmm) {
        return Instant.parse(DAY + "T" + hhmm + ":00Z");
    }

    void schedule(String id, Carrier carrier, String hhmm) {
        var shipment = new Shipment(id, "O-" + id, carrier, "TRK-" + id, ShipmentStatus.SCHEDULED);
        // The record timestamp is the wall clock on purpose: the window must follow the payload, not this.
        in.pipeInput("O-" + id, new ShipmentScheduled(shipment, at(hhmm)), Instant.now());
    }

    @Test
    void oneFinalListPerCarrierPerSlotOnceTheWindowCloses() {
        start(false);
        for (int i = 1; i <= 7; i++) {
            schedule("F" + i, Carrier.FASTLINE, "09:0" + i);
        }
        for (int i = 1; i <= 3; i++) {
            schedule("A" + i, Carrier.AIRCO, "09:0" + i);
        }
        assertTrue(out.isEmpty(), "nothing leaves while the slot can still change");

        schedule("TICK", Carrier.AIRCO, "09:21");

        var lists = out.readKeyValuesToList();
        assertEquals(2, lists.size());
        var fastline = lists.stream().filter(kv -> kv.value.carrier() == Carrier.FASTLINE).findFirst().orElseThrow().value;
        assertEquals(7, fastline.count());
        assertEquals(List.of("F1", "F2", "F3", "F4", "F5", "F6", "F7"), fastline.shipmentIds());
        assertEquals(at("09:00"), fastline.from());
        assertEquals(at("09:15"), fastline.to());
        assertEquals("window", fastline.closedBy());
        var airco = lists.stream().filter(kv -> kv.value.carrier() == Carrier.AIRCO).findFirst().orElseThrow().value;
        assertEquals(3, airco.count());
    }

    @Test
    void theWindowStaysOpenForTheGracePeriodAndThenCloses() {
        start(false);
        schedule("F1", Carrier.FASTLINE, "09:01");
        schedule("T1", Carrier.AIRCO, "09:19");
        assertTrue(out.isEmpty(), "09:19 is inside the grace of the 09:00 slot");
        schedule("T2", Carrier.AIRCO, "09:21");
        assertEquals(1, out.getQueueSize(), "one list for the FASTLINE slot once stream time passes the grace");
    }

    @Test
    void anOutOfOrderRecordInsideGraceLandsInItsOwnSlot() {
        start(false);
        schedule("F1", Carrier.FASTLINE, "09:01");
        schedule("F8", Carrier.FASTLINE, "09:16");
        schedule("F9", Carrier.FASTLINE, "09:10");
        schedule("TICK", Carrier.AIRCO, "09:21");

        var lists = out.readKeyValuesToList();
        assertEquals(1, lists.size(), "only the 09:00 slot is closed; the 09:15 slot is still open");
        assertEquals(List.of("F1", "F9"), lists.get(0).value.shipmentIds());
        assertTrue(lists.get(0).value.shipmentIds().contains("F9"));
        assertFalse(lists.get(0).value.shipmentIds().contains("F8"));
    }

    @Test
    void aRecordAfterTheWindowClosedIsDroppedAndCounted() {
        start(false);
        schedule("F1", Carrier.FASTLINE, "09:01");
        schedule("TICK", Carrier.AIRCO, "09:21");
        assertEquals(1, out.readKeyValuesToList().size());

        double before = droppedRecords();
        schedule("LATE", Carrier.FASTLINE, "09:05");

        assertTrue(out.isEmpty(), "the late record reopens nothing and produces nothing");
        assertEquals(before + 1, droppedRecords(), 0.001);
    }

    @Test
    void withoutSuppressionEveryUpdateLeavesAsAPartialList() {
        start(true);
        schedule("F1", Carrier.FASTLINE, "09:01");
        schedule("F2", Carrier.FASTLINE, "09:02");
        schedule("F3", Carrier.FASTLINE, "09:03");

        var counts = out.readValuesToList().stream().map(PickupList::count).toList();
        assertEquals(List.of(1, 2, 3), counts);
    }

    @Test
    void nothingLeavesFromAnIdleStreamHoweverLongTheClockRuns() {
        start(false);
        schedule("F1", Carrier.FASTLINE, "09:01");
        driver.advanceWallClockTime(Duration.ofHours(6));
        assertTrue(out.isEmpty(), "wall-clock time does not close an event-time window");
    }

    @Test
    void theSlotFollowsTheEventTimeInThePayloadNotTheRecordTimestamp() {
        start(false);
        schedule("F1", Carrier.FASTLINE, "09:01");
        schedule("TICK", Carrier.AIRCO, "09:21");
        assertEquals(at("09:00"), out.readValue().from());
    }

    @Test
    void aListHoldsOneCarrierAndTheSameSlotGivesEachCarrierItsOwn() {
        start(false);
        schedule("F1", Carrier.FASTLINE, "09:05");
        schedule("A1", Carrier.AIRCO, "09:05");
        schedule("TICK", Carrier.AIRCO, "09:21");
        var lists = out.readKeyValuesToList();
        assertEquals(2, lists.size());
        assertTrue(lists.stream().anyMatch(kv -> kv.key.startsWith("FASTLINE@")));
        assertTrue(lists.stream().anyMatch(kv -> kv.key.startsWith("AIRCO@")));
    }

    @Test
    void theStateStoreHasAChangelogUnlessItIsSwitchedOff() {
        String described = PickupTopology.build(MAPPER, false, true).describe().toString();
        assertTrue(described.contains("pickup-lists"), described);
        assertTrue(described.contains("by-carrier-repartition"), described);
    }

    /** Comparison code: hopping windows of 15 minutes advancing by 5 count each shipment three times. */
    @Test
    void hoppingWindowsWouldPutEachShipmentInThreeLists() {
        var builder = new org.apache.kafka.streams.StreamsBuilder();
        var scheduled = new ObjectMapperSerde<>(ShipmentScheduled.class, MAPPER);
        builder.stream(Topics.SHIPPING_SCHEDULED, org.apache.kafka.streams.kstream.Consumed.with(Serdes.String(), scheduled)
                        .withTimestampExtractor(new PayloadTimestampExtractor()))
                .groupBy((k, v) -> v.shipment().carrier().name(),
                        org.apache.kafka.streams.kstream.Grouped.with(Serdes.String(), scheduled))
                .windowedBy(org.apache.kafka.streams.kstream.TimeWindows
                        .ofSizeAndGrace(Duration.ofMinutes(15), Duration.ofMinutes(5)).advanceBy(Duration.ofMinutes(5)))
                .count()
                .toStream()
                .map((w, n) -> org.apache.kafka.streams.KeyValue.pair(w.key() + "@" + w.window().startTime(), n.intValue()))
                .to("hopping", org.apache.kafka.streams.kstream.Produced.with(Serdes.String(), Serdes.Integer()));
        start(builder.build());
        schedule("F1", Carrier.FASTLINE, "09:07");
        var hopping = driver.createOutputTopic("hopping", Serdes.String().deserializer(), Serdes.Integer().deserializer());
        assertEquals(3, hopping.readKeyValuesToList().size(), "one record is counted in three overlapping windows");
    }

    private double droppedRecords() {
        return driver.metrics().entrySet().stream()
                .filter(e -> e.getKey().name().equals("dropped-records-total"))
                .mapToDouble(e -> ((Number) e.getValue().metricValue()).doubleValue()).sum();
    }
}
