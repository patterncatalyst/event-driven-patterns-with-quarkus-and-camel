package com.patterncatalyst.shipping.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** The two ages, on a registry of their own and a fixed clock. */
class FlowAgeTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    private SimpleMeterRegistry registry;
    private FlowAge age;

    @BeforeEach
    void create() {
        registry = new SimpleMeterRegistry();
        age = new FlowAge(registry);
    }

    private static ConsumerRecord<String, byte[]> record(Instant recordTime, Instant occurredAt) {
        var record = new ConsumerRecord<>("edp.orders.placed", 0, 0, recordTime.toEpochMilli(), TimestampType.CREATE_TIME,
                0, 0, "O-1", new byte[0], new org.apache.kafka.common.header.internals.RecordHeaders(), java.util.Optional.empty());
        if (occurredAt != null) {
            record.headers().add(new RecordHeader(FlowAge.OCCURRED_AT, occurredAt.toString().getBytes(StandardCharsets.UTF_8)));
        }
        return record;
    }

    private double seconds(String name) {
        Timer timer = registry.find(name).timer();
        return timer.totalTime(TimeUnit.MILLISECONDS) / 1000;
    }

    @Test
    void bothAgesAgreeWhenTheRecordIsWrittenWhenTheEventHappens() {
        Instant happened = NOW.minusSeconds(40);
        age.record(record(happened, happened), NOW);
        assertEquals(40, seconds("order.flow.event.age"), 0.001);
        assertEquals(40, seconds("order.flow.record.age"), 0.001);
    }

    @Test
    void aRedrivenRecordKeepsItsEventAgeAndHasANewRecordAge() {
        age.record(record(NOW.minusMillis(300), NOW.minusSeconds(40)), NOW);
        assertEquals(40, seconds("order.flow.event.age"), 0.001);
        assertEquals(0.3, seconds("order.flow.record.age"), 0.001);
    }

    @Test
    void aRecordWithoutTheHeaderHasNoEventAgeSample() {
        age.record(record(NOW.minusSeconds(2), null), NOW);
        assertEquals(0, registry.find("order.flow.event.age").timer().count());
        assertEquals(1, registry.find("order.flow.record.age").timer().count());
    }

    @Test
    void aClockAheadOfTheRecordIsCountedAsZero() {
        age.record(record(NOW.plusSeconds(5), NOW.plusSeconds(5)), NOW);
        assertEquals(0, seconds("order.flow.event.age"), 0.001);
    }

    @Test
    void bucketsAreExplicitSoPercentilesCanBeComputedFromTheExport() {
        age.record(record(NOW, NOW), NOW);
        var buckets = registry.find("order.flow.event.age").timer().takeSnapshot().histogramCounts();
        var bounds = java.util.Arrays.stream(buckets).map(b -> (long) b.bucket(TimeUnit.SECONDS)).toList();
        assertTrue(bounds.containsAll(java.util.List.of(1L, 5L, 30L, 120L)), bounds.toString());
    }
}
