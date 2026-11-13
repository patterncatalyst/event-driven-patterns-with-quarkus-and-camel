package com.patterncatalyst.shipping.projections;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.reactive.messaging.kafka.api.IncomingKafkaRecordMetadata;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * One channel per topic, each in its own consumer group. A record is acknowledged after its transaction commits, and
 * the time from the record's timestamp to that moment is the lag of the view for that event.
 */
@ApplicationScoped
public class ProjectionChannels {

    @Inject
    OrderViewProjector projector;

    @Inject
    MeterRegistry registry;

    Timer lag;

    @PostConstruct
    void init() {
        lag = Timer.builder("order.view.lag").publishPercentiles(0.5, 0.95, 0.99).publishPercentileHistogram()
                .register(registry);
    }

    @Incoming("placed")
    @Blocking
    public CompletionStage<Void> placed(Message<Events.OrderPlaced> message) {
        return apply(message, projector::placed);
    }

    @Incoming("cancelled")
    @Blocking
    public CompletionStage<Void> cancelled(Message<Events.OrderCancelled> message) {
        return apply(message, projector::cancelled);
    }

    @Incoming("paid")
    @Blocking
    public CompletionStage<Void> paid(Message<Events.PaymentProcessed> message) {
        return apply(message, projector::paid);
    }

    @Incoming("scheduled")
    @Blocking
    public CompletionStage<Void> scheduled(Message<Events.ShipmentScheduled> message) {
        return apply(message, projector::scheduled);
    }

    @Incoming("in-transit")
    @Blocking
    public CompletionStage<Void> inTransit(Message<Events.ShipmentInTransit> message) {
        return apply(message, projector::inTransit);
    }

    @Incoming("delivered")
    @Blocking
    public CompletionStage<Void> delivered(Message<Events.ShipmentDelivered> message) {
        return apply(message, projector::delivered);
    }

    private <T> CompletionStage<Void> apply(Message<T> message, BiConsumer<T, Instant> projection) {
        Instant produced = message.getMetadata(IncomingKafkaRecordMetadata.class).orElseThrow().getTimestamp();
        projection.accept(message.getPayload(), produced);
        lag.record(Duration.between(produced, Instant.now()));
        return message.ack();
    }
}
