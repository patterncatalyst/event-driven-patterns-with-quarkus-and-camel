package com.patterncatalyst.shipping.correlation.inventory;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Reserves for OrderPlaced and releases for PaymentFailed and OrderCancelled, each under the order id of its event.
 * The event id is the record's id header, which the publishing outbox sets.
 */
@ApplicationScoped
public class InventoryListener {

    @Inject
    ReservationService reservations;

    @Inject
    Correlated correlated;

    @Incoming("placed")
    @Blocking
    public void onPlaced(ConsumerRecord<String, OrderPlaced> record) {
        correlated.step(record.value().orderId(), () -> reservations.reserve(eventId(record), record.value()));
    }

    @Incoming("failed")
    @Blocking
    public void onPaymentFailed(ConsumerRecord<String, PaymentFailed> record) {
        correlated.step(record.value().orderId(), () -> reservations.release(eventId(record), record.value().orderId()));
    }

    @Incoming("cancelled")
    @Blocking
    public void onCancelled(ConsumerRecord<String, OrderCancelled> record) {
        correlated.step(record.value().orderId(), () -> reservations.release(eventId(record), record.value().orderId()));
    }

    private static UUID eventId(ConsumerRecord<?, ?> record) {
        return UUID.fromString(new String(record.headers().lastHeader("id").value(), StandardCharsets.UTF_8));
    }
}
