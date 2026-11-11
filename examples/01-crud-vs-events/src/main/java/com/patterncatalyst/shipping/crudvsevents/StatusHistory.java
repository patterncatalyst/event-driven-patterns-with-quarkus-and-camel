package com.patterncatalyst.shipping.crudvsevents;

import java.time.Instant;
import java.util.List;

import com.patterncatalyst.shipping.OrderStatus;

/** Every status an Order held and when it entered it. Only the event stream can answer this. */
public record StatusHistory(String orderId, List<Entry> entries) {

    public record Entry(int version, OrderStatus status, Instant occurredAt) {
    }
}
