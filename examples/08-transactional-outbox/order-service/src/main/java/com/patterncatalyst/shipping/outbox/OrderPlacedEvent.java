package com.patterncatalyst.shipping.outbox;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.Topics;

import io.debezium.outbox.quarkus.ExportedEvent;

/**
 * What the outbox extension writes to the outbox table. The extra column {@code topic} tells the connector's
 * router which topic the row belongs to.
 */
public record OrderPlacedEvent(String aggregateId, JsonNode payload, Instant timestamp)
        implements ExportedEvent<String, JsonNode> {

    static OrderPlacedEvent of(OrderPlaced placed, ObjectMapper json) {
        return new OrderPlacedEvent(placed.orderId(), json.valueToTree(placed), Instant.now());
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
        return "OrderPlaced";
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
        return Map.of("topic", Topics.ORDERS_PLACED);
    }
}
