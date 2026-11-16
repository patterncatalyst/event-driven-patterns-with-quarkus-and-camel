package com.patterncatalyst.shipping.deliverycounts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.patterncatalyst.shipping.Carrier;
import com.patterncatalyst.shipping.Topics;
import io.quarkus.kafka.client.serialization.ObjectMapperSerde;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.state.WindowStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The topology on its own, with the Kafka Streams test driver: no broker, no wall clock. Event time comes from the
 * payload, as the three engines read it. The scenario is the one the compose stack runs, second by second.
 */
class DeliveryCountsTopologyTest {

    static final ObjectMapper MAPPER = JsonMapper.builder().findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    static final Instant BASE = Instant.parse("2026-11-16T10:00:00Z");
    static final Duration TEN = Duration.ofSeconds(10);

    TopologyTestDriver driver;
    TestInputTopic<String, ShipmentDelivered> in;
    int n = 3800;

    void start(Duration grace, Duration retention) {
        start(DeliveryCountsTopology.build(MAPPER, grace, retention));
    }

    void start(Topology topology) {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "delivery-counts-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "unused:9092");
        props.put(StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, 0);
        driver = new TopologyTestDriver(topology, props);
        in = driver.createInputTopic(Topics.SHIPPING_DELIVERED, Serdes.String().serializer(),
                new ObjectMapperSerde<>(ShipmentDelivered.class, MAPPER).serializer());
    }

    @AfterEach
    void close() {
        if (driver != null) {
            driver.close();
        }
    }

    /** A delivery {@code seconds} after the base; the record timestamp is the wall clock so only the payload counts. */
    void deliver(Carrier carrier, int seconds) {
        n++;
        in.pipeInput(null, new ShipmentDelivered("O-" + n, carrier, BASE.plusSeconds(seconds)), Instant.now());
    }

    /** The scenario of the stack: three one-minute windows, an out-of-order record, and a fourth window that opens. */
    void scenario() {
        deliver(Carrier.FASTLINE, 5);
        deliver(Carrier.FASTLINE, 20);
        deliver(Carrier.AIRCO, 30);
        deliver(Carrier.FASTLINE, 40);
        deliver(Carrier.AIRCO, 50);
        deliver(Carrier.AIRCO, 62);
        deliver(Carrier.FASTLINE, 55); // 7 s behind the newest event time
        deliver(Carrier.FASTLINE, 70);
        deliver(Carrier.AIRCO, 80);
        deliver(Carrier.AIRCO, 100);
        deliver(Carrier.FASTLINE, 110);
        deliver(Carrier.FASTLINE, 125);
        deliver(Carrier.AIRCO, 140);
        deliver(Carrier.FASTLINE, 195);
    }

    long count(Carrier carrier, int windowStartSeconds) {
        WindowStore<String, Long> store = driver.getWindowStore(DeliveryCountsTopology.STORE);
        Instant start = BASE.plusSeconds(windowStartSeconds);
        try (var windows = store.fetch(carrier.name(), start, start)) {
            return windows.hasNext() ? windows.next().value : 0;
        }
    }

    double droppedRecords() {
        return driver.metrics().entrySet().stream()
                .filter(e -> e.getKey().name().equals("dropped-records-total"))
                .mapToDouble(e -> ((Number) e.getValue().metricValue()).doubleValue()).sum();
    }

    @Test
    void eachWindowCountsTheDeliveriesOfItsCarrierByEventTime() {
        start(TEN, DeliveryCountsTopology.RETENTION);
        scenario();
        assertEquals(4, count(Carrier.FASTLINE, 0));
        assertEquals(2, count(Carrier.AIRCO, 0));
        assertEquals(2, count(Carrier.FASTLINE, 60));
        assertEquals(3, count(Carrier.AIRCO, 60));
        assertEquals(1, count(Carrier.FASTLINE, 120));
        assertEquals(1, count(Carrier.AIRCO, 120));
        assertEquals(1, count(Carrier.FASTLINE, 180), "the fourth window is open and already answers");
    }

    @Test
    void aRecordBehindTheNewestEventTimeButInsideTheGraceIsCounted() {
        start(TEN, DeliveryCountsTopology.RETENTION);
        scenario();
        assertEquals(4, count(Carrier.FASTLINE, 0), "the 55 s record arrived after a 62 s record and is in window 1");
        assertEquals(0, droppedRecords(), 0.001);
    }

    @Test
    void aRecordAfterItsWindowClosedIsDroppedAndCounted() {
        start(TEN, DeliveryCountsTopology.RETENTION);
        scenario();
        deliver(Carrier.FASTLINE, 58); // window 1 closed when stream time passed 70 s
        assertEquals(4, count(Carrier.FASTLINE, 0));
        assertEquals(1, droppedRecords(), 0.001);
    }

    @Test
    void withNoGraceTheOutOfOrderRecordIsLate() {
        start(Duration.ZERO, DeliveryCountsTopology.RETENTION);
        scenario();
        assertEquals(3, count(Carrier.FASTLINE, 0), "comparison: a smaller lateness than the other engines counts one less");
        assertEquals(1, droppedRecords(), 0.001);
    }

    @Test
    void aClosedWindowStaysQueryableForTheRetention() {
        start(TEN, DeliveryCountsTopology.RETENTION);
        scenario();
        deliver(Carrier.AIRCO, 600);
        assertEquals(4, count(Carrier.FASTLINE, 0));
    }

    /** Comparison code: the default retention is the window size plus the grace, so closed windows vanish. */
    @Test
    void withoutAnExplicitRetentionAClosedWindowDisappears() {
        start(TEN, Duration.ofMinutes(1).plus(TEN));
        scenario();
        deliver(Carrier.AIRCO, 600);
        assertEquals(0, count(Carrier.FASTLINE, 0));
    }

    @Test
    void theSlotFollowsThePayloadNotTheRecordTimestamp() {
        start(TEN, DeliveryCountsTopology.RETENTION);
        deliver(Carrier.AIRCO, 5);
        assertEquals(1, count(Carrier.AIRCO, 0));
        assertTrue(driver.getAllStateStores().containsKey(DeliveryCountsTopology.STORE));
    }
}
