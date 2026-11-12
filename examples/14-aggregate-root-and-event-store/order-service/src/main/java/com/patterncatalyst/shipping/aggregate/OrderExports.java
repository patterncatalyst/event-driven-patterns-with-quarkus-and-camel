package com.patterncatalyst.shipping.aggregate;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.Topics;

import io.debezium.outbox.quarkus.ExportedEvent;

/**
 * The public events: what the outbox extension writes for OrderPlaced and OrderCancelled. The extra column
 * {@code topic} tells the connector's router where the row goes. OrderPaid and OrderShipped have no export; they
 * stay in the stream.
 */
public record OrderExports(String aggregateId, String type, String topic, JsonNode payload, Instant timestamp)
        implements ExportedEvent<String, JsonNode> {

    public static OrderExports placed(String orderId, OrderEvent.OrderPlaced placed, ObjectMapper json) {
        return new OrderExports(orderId, "OrderPlaced", Topics.ORDERS_PLACED, json.valueToTree(placed),
                Instant.now());
    }

    public static OrderExports cancelled(String orderId, OrderEvent.OrderCancelled cancelled, ObjectMapper json) {
        JsonNode payload = json.createObjectNode().put("orderId", orderId).put("reason", cancelled.reason());
        return new OrderExports(orderId, "OrderCancelled", Topics.ORDERS_CANCELLED, payload, Instant.now());
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
