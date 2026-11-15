package com.patterncatalyst.shipping.tracing.inventory;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Reserves stock for each OrderPlaced. SmallRye Reactive Messaging reads the {@code traceparent} header and makes
 * its consumer span a child of the span that wrote it. Demo code: {@code demo.consumer-trace=link} turns that
 * instrumentation off for the channel and starts a new trace linked to the record's context instead.
 */
@ApplicationScoped
public class PlacedListener {

    private final ReservationService reservations;
    private final LinkedProcessing linked;
    private final boolean link;

    PlacedListener(ReservationService reservations, LinkedProcessing linked,
            @ConfigProperty(name = "demo.consumer-trace", defaultValue = "parent") String mode) {
        this.reservations = reservations;
        this.linked = linked;
        this.link = "link".equals(mode);
    }

    @Incoming("placed")
    @Blocking
    public void onPlaced(ConsumerRecord<String, OrderPlaced> record) {
        OrderPlaced placed = record.value();
        if (link) {
            linked.run(record.headers(), () -> reserve(placed));
        } else {
            reserve(placed);
        }
    }

    private void reserve(OrderPlaced placed) {
        reservations.reserve(placed.orderId(), placed.itemSku(), placed.quantity());
    }
}
