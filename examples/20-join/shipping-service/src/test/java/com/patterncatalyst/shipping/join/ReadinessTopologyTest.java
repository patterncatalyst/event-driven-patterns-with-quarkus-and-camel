package com.patterncatalyst.shipping.join;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.Payment;
import com.patterncatalyst.shipping.PaymentStatus;
import com.patterncatalyst.shipping.Topics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkus.kafka.client.serialization.ObjectMapperSerde;

/** The join in event time, driven by the topology test driver: stream time moves only with the records. */
class ReadinessTopologyTest {

    private TopologyTestDriver driver;
    private TestInputTopic<String, InventoryReserved> reserved;
    private TestInputTopic<String, PaymentProcessed> paid;
    private TestOutputTopic<String, Readiness> out;

    @BeforeEach
    void start() {
        var mapper = Times.mapper();
        var topology = new ReadinessTopology(mapper, new ReadinessMetrics(new SimpleMeterRegistry())).topology();
        Properties config = new Properties();
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, "readiness-test");
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "unused:9092");
        config.put(StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, 0);
        driver = new TopologyTestDriver(topology, config);
        reserved = driver.createInputTopic(Topics.INVENTORY_RESERVED, Serdes.String().serializer(),
                new ObjectMapperSerde<>(InventoryReserved.class, mapper).serializer());
        paid = driver.createInputTopic(Topics.PAYMENTS_PROCESSED, Serdes.String().serializer(),
                new ObjectMapperSerde<>(PaymentProcessed.class, mapper).serializer());
        out = driver.createOutputTopic(Topics.SHIPPING_ORDER_READINESS, Serdes.String().deserializer(),
                new ObjectMapperSerde<>(Readiness.class, mapper).deserializer());
    }

    @AfterEach
    void stop() {
        driver.close();
    }

    private void reserve(String orderId, String time) {
        reserved.pipeInput(orderId, new InventoryReserved(orderId, "SKU-20", 1, Times.at(time)));
    }

    private void pay(String orderId, String time) {
        paid.pipeInput(orderId, new PaymentProcessed(
                new Payment("P-" + orderId, orderId, new BigDecimal("25.00"), PaymentStatus.COMPLETED), Times.at(time)));
    }

    private Map<String, Readiness> results() {
        return out.readKeyValuesToMap();
    }

    @Test
    void aPaymentInsideTheWindowMatchesInEitherOrder() {
        reserve("A", "09:00");
        pay("A", "09:01");
        pay("B", "09:00");
        reserve("B", "09:01");
        var results = results();
        assertEquals(Readiness.READY_TO_SHIP, results.get("A").type());
        assertEquals(Readiness.READY_TO_SHIP, results.get("B").type());
        assertEquals(Times.at("09:00"), results.get("A").reservedAt());
        assertEquals(Times.at("09:01"), results.get("A").paidAt());
    }

    @Test
    void aPaymentThirtyOneMinutesLaterDoesNotMatch() {
        reserve("C", "09:00");
        pay("C", "09:31");
        assertTrue(out.isEmpty(), "nothing leaves while the window and its grace are still open");
        reserve("tick", "09:36");
        var results = results();
        assertEquals(Readiness.PAYMENT_OVERDUE, results.get("C").type());
        assertNull(results.get("C").paidAt());
    }

    @Test
    void anUnmatchedReservationLeavesOnlyAfterTheWindowAndTheGrace() {
        reserve("D", "09:00");
        reserve("tick-1", "09:34");
        assertTrue(out.isEmpty(), "09:34 is inside the 30 minute window plus 5 minute grace");
        reserve("tick-2", "09:35");
        assertTrue(out.isEmpty(), "stream time equal to the end of the grace does not close it");
        reserve("tick-3", "09:36");
        assertEquals(Readiness.PAYMENT_OVERDUE, results().get("D").type());
    }

    @Test
    void theWallClockDoesNotCloseTheWindow() {
        reserve("E", "09:00");
        driver.advanceWallClockTime(java.time.Duration.ofHours(5));
        assertTrue(out.isEmpty(), "an idle stream never closes its windows");
    }

    @Test
    void aPaymentOlderThanStreamTimeButInsideTheGraceStillMatches() {
        reserve("F", "09:00");
        reserve("tick", "09:33");
        pay("F", "09:02");
        assertEquals(Readiness.READY_TO_SHIP, results().get("F").type());
    }

    @Test
    void theCraftedPairsGiveTheEventTimeAnswer() {
        reserve("O-2003", "09:10");
        pay("O-2003", "09:12");
        reserve("O-2004", "09:20");
        pay("O-2004", "10:50");
        var results = results();
        assertEquals(Readiness.READY_TO_SHIP, results.get("O-2003").type());
        assertEquals(Readiness.PAYMENT_OVERDUE, results.get("O-2004").type());
    }

    @Test
    void aLatePaymentStillMatchesWhileTheReservationIsRetained() {
        reserve("G", "09:00");
        reserve("tick", "09:36");
        assertEquals(Readiness.PAYMENT_OVERDUE, results().get("G").type());
        pay("G", "09:01");
        assertEquals(Readiness.READY_TO_SHIP, results().get("G").type(),
                "a second, contradictory answer for the same order");
        assertEquals(0, droppedRecords(), "a join does not count it as a dropped record");
    }

    @Test
    void aLatePaymentFindsNothingOnceTheReservationStoreHasMovedOn() {
        reserve("H", "09:00");
        reserve("tick", "10:06");
        assertEquals(Readiness.PAYMENT_OVERDUE, results().get("H").type());
        pay("H", "09:01");
        assertTrue(out.isEmpty());
    }

    private double droppedRecords() {
        return driver.metrics().entrySet().stream()
                .filter(entry -> entry.getKey().name().equals("dropped-records-total"))
                .mapToDouble(entry -> ((Number) entry.getValue().metricValue()).doubleValue()).sum();
    }
}
