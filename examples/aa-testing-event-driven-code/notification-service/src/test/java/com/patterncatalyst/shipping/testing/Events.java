package com.patterncatalyst.shipping.testing;

import org.apache.kafka.clients.producer.ProducerRecord;

import com.patterncatalyst.shipping.Topics;

final class Events {

    private Events() {
    }

    static String orderPlaced(String eventId, String orderId) {
        return "{\"eventId\":\"" + eventId + "\",\"occurredAt\":\"2026-11-16T10:00:00Z\",\"orderId\":\"" + orderId + "\"}";
    }

    static ProducerRecord<String, String> record(String eventId, String orderId) {
        return new ProducerRecord<>(Topics.ORDERS_PLACED, orderId, orderPlaced(eventId, orderId));
    }
}
