package com.patterncatalyst.shipping.orchestration.order;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.Topics;

import io.debezium.outbox.quarkus.ExportedEvent;

/** What the outbox extension writes when an order is cancelled; the connector routes the row to its topic. */
public record OrderCancelledEvent(String orderId, JsonNode payload, Instant timestamp)
        implements ExportedEvent<String, JsonNode> {

    public record Body(String orderId) {
    }

    static OrderCancelledEvent of(String orderId, ObjectMapper json) {
        return new OrderCancelledEvent(orderId, json.valueToTree(new Body(orderId)), Instant.now());
    }

    @Override
    public String getAggregateId() {
        return orderId;
    }

    @Override
    public String getAggregateType() {
        return "Order";
    }

    @Override
    public String getType() {
        return "OrderCancelled";
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public JsonNode getPayload() {
        return payload;
    }

    @Override
    public Map<String, Object> getAdditionalFieldValues() {
        return Map.of("topic", Topics.ORDERS_CANCELLED);
    }
}
