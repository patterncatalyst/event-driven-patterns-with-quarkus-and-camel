package com.patterncatalyst.shipping.saga.order;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** The four replies of the saga. The event id is the record's id header, which the publishing outbox sets. */
@ApplicationScoped
public class SagaListener {

    @Inject
    SagaService saga;

    @Incoming("reserved")
    @Blocking
    public void onReserved(ConsumerRecord<String, InventoryReserved> record) {
        saga.onReserved(eventId(record), record.value());
    }

    @Incoming("insufficient")
    @Blocking
    public void onInsufficient(ConsumerRecord<String, InventoryInsufficient> record) {
        saga.onInsufficient(eventId(record), record.value());
    }

    @Incoming("processed")
    @Blocking
    public void onProcessed(ConsumerRecord<String, PaymentProcessed> record) {
        saga.onPaymentProcessed(eventId(record), record.value());
    }

    @Incoming("failed")
    @Blocking
    public void onFailed(ConsumerRecord<String, PaymentFailed> record) {
        saga.onPaymentFailed(eventId(record), record.value());
    }

    private static UUID eventId(ConsumerRecord<?, ?> record) {
        return UUID.fromString(new String(record.headers().lastHeader("id").value(), StandardCharsets.UTF_8));
    }
}
