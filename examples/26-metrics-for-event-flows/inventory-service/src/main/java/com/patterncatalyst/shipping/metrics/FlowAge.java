package com.patterncatalyst.shipping.metrics;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Two ages for every record that arrives. Event age is now minus the time the order service stamped on the event
 * (header occurredAt, placed there by the connector); record age is now minus the Kafka record timestamp. A record that
 * was written again later, such as one redriven from the dead letter topic, has a new record timestamp and the
 * original event time. The buckets are explicit because the OTLP export carries no percentile histogram.
 */
@ApplicationScoped
public class FlowAge {

    static final String OCCURRED_AT = "occurredAt";
    private static final Duration[] BUCKETS = { Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(30),
            Duration.ofSeconds(120) };

    private final Timer eventAge;
    private final Timer recordAge;

    FlowAge(MeterRegistry registry) {
        this.eventAge = Timer.builder("order.flow.event.age")
                .description("Time from the event being stamped to its arrival").serviceLevelObjectives(BUCKETS)
                .register(registry);
        this.recordAge = Timer.builder("order.flow.record.age")
                .description("Time from the Kafka record timestamp to its arrival").serviceLevelObjectives(BUCKETS)
                .register(registry);
    }

    void record(ConsumerRecord<?, ?> record) {
        record(record, Instant.now());
    }

    void record(ConsumerRecord<?, ?> record, Instant now) {
        recordAge.record(nonNegative(Duration.between(Instant.ofEpochMilli(record.timestamp()), now)));
        Header occurredAt = record.headers().lastHeader(OCCURRED_AT);
        if (occurredAt != null) {
            Instant stamped = Instant.parse(new String(occurredAt.value(), StandardCharsets.UTF_8));
            eventAge.record(nonNegative(Duration.between(stamped, now)));
        }
    }

    private static Duration nonNegative(Duration d) {
        return d.isNegative() ? Duration.ZERO : d;
    }
}
