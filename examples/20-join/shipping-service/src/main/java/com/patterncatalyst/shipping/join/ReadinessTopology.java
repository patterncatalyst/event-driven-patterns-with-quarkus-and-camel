package com.patterncatalyst.shipping.join;

import java.time.Duration;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.JoinWindows;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.StreamJoined;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.Topics;

import io.quarkus.kafka.client.serialization.ObjectMapperSerde;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * The Kafka Streams form: a windowed left join of reservations and payments by order id, in event time. A payment
 * within 30 minutes of the reservation matches. A reservation that gets no match leaves as PaymentOverdue once stream
 * time passes the end of its window plus the grace period.
 */
@ApplicationScoped
public class ReadinessTopology {

    static final Duration WINDOW = Duration.ofMinutes(30);
    static final Duration GRACE = Duration.ofMinutes(5);

    private final ObjectMapper mapper;
    private final ReadinessMetrics metrics;

    ReadinessTopology(ObjectMapper mapper, ReadinessMetrics metrics) {
        this.mapper = mapper;
        this.metrics = metrics;
    }

    @Produces
    public Topology topology() {
        var reservedSerde = new ObjectMapperSerde<>(InventoryReserved.class, mapper);
        var paidSerde = new ObjectMapperSerde<>(PaymentProcessed.class, mapper);
        var readinessSerde = new ObjectMapperSerde<>(Readiness.class, mapper);

        var builder = new StreamsBuilder();
        var reserved = builder.stream(Topics.INVENTORY_RESERVED,
                Consumed.with(Serdes.String(), reservedSerde).withTimestampExtractor(new PayloadTimestampExtractor()));
        var paid = builder.stream(Topics.PAYMENTS_PROCESSED,
                Consumed.with(Serdes.String(), paidSerde).withTimestampExtractor(new PayloadTimestampExtractor()));

        reserved.leftJoin(paid, Readiness::of,
                        JoinWindows.ofTimeDifferenceAndGrace(WINDOW, GRACE),
                        StreamJoined.with(Serdes.String(), reservedSerde, paidSerde).withStoreName("readiness"))
                .peek((orderId, readiness) -> metrics.count("streams", readiness))
                .to(Topics.SHIPPING_ORDER_READINESS, Produced.with(Serdes.String(), readinessSerde));
        return builder.build();
    }
}
