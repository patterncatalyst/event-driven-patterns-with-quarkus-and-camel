package com.patterncatalyst.shipping.inbox;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Reads OrderPlaced and reserves stock once per event id. The id is the record's id header, which the
 * outbox of the order service sets. The record is acknowledged when this method returns.
 */
@ApplicationScoped
public class OrderPlacedInbox {

    private static final Logger LOG = Logger.getLogger(OrderPlacedInbox.class);

    @Inject
    ReservationService reservations;

    @Inject
    CrashSwitch crash;

    @ConfigProperty(name = "demo.inbox.enabled", defaultValue = "true")
    boolean inboxEnabled;

    private final Counter duplicates;

    OrderPlacedInbox(MeterRegistry registry) {
        this.duplicates = registry.counter("inventory.inbox.duplicates");
    }

    @Incoming("orders-placed")
    @Blocking
    public void on(ConsumerRecord<String, OrderPlaced> record) {
        OrderPlaced event = record.value();
        if (inboxEnabled) {
            UUID eventId = UUID.fromString(new String(record.headers().lastHeader("id").value(), StandardCharsets.UTF_8));
            if (!reservations.reserveOnce(eventId, event)) {
                duplicates.increment();
                LOG.infof("duplicate event %s skipped", eventId);
                return;
            }
        } else {
            reservations.reserve(event);
        }
        LOG.infof("reserved %d of %s for %s", event.quantity(), event.itemSku(), event.orderId());
        crash.afterCommit(event.orderId());
    }
}
