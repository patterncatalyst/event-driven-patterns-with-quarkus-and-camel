package com.patterncatalyst.shipping.eventnotification.order;

import java.time.Instant;
import java.util.UUID;

/** The event: an order was placed. It names the order and carries nothing else about it. The time is ISO-8601. */
public record OrderPlaced(String eventId, String occurredAt, String orderId) {

    static OrderPlaced of(String orderId) {
        return new OrderPlaced(UUID.randomUUID().toString(), Instant.now().toString(), orderId);
    }
}
