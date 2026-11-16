package com.patterncatalyst.shipping.cache;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.Topics;

import io.debezium.outbox.quarkus.ExportedEvent;

/**
 * What the outbox extension writes to the outbox table: the full state of the order, so a reader needs nothing else.
 * The extra column {@code topic} tells the connector's router which topic the row belongs to.
 */
public record OrderEvent(String aggregateId, String type, String topic, JsonNode payload, Instant timestamp)
        implements ExportedEvent<String, JsonNode> {

    static OrderEvent placed(OrderView view, ObjectMapper json) {
        return new OrderEvent(view.orderId(), "OrderPlaced", Topics.ORDERS_PLACED, json.valueToTree(view), Instant.now());
    }

    static OrderEvent cancelled(OrderView view, ObjectMapper json) {
        return new OrderEvent(view.orderId(), "OrderCancelled", Topics.ORDERS_CANCELLED, json.valueToTree(view), Instant.now());
    }

    @Override
    public String getAggregateId() {
        return aggregateId;
    }

    @Override
    public String getAggregateType() {
        return "Order";
    }

    @Override
    public String getType() {
        return type;
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
        return Map.of("topic", topic);
    }
}
