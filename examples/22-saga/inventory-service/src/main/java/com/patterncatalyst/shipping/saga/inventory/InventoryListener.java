package com.patterncatalyst.shipping.saga.inventory;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Reserves for OrderPlaced and releases for PaymentFailed and OrderCancelled. The event id is the record's id header,
 * which the publishing outbox sets.
 */
@ApplicationScoped
public class InventoryListener {

    @Inject
    ReservationService reservations;

    @Incoming("placed")
    @Blocking
    public void onPlaced(ConsumerRecord<String, OrderPlaced> record) {
        reservations.reserve(eventId(record), record.value());
    }

    @Incoming("failed")
    @Blocking
    public void onPaymentFailed(ConsumerRecord<String, PaymentFailed> record) {
        reservations.release(eventId(record), record.value().orderId());
    }

    @Incoming("cancelled")
    @Blocking
    public void onCancelled(ConsumerRecord<String, OrderCancelled> record) {
        reservations.release(eventId(record), record.value().orderId());
    }

    private static UUID eventId(ConsumerRecord<?, ?> record) {
        return UUID.fromString(new String(record.headers().lastHeader("id").value(), StandardCharsets.UTF_8));
    }
}
