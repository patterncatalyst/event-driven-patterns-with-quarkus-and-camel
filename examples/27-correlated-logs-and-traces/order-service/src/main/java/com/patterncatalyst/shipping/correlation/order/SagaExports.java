package com.patterncatalyst.shipping.correlation.order;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.Order;
import com.patterncatalyst.shipping.Topics;

import io.debezium.outbox.quarkus.ExportedEvent;

/** What the outbox extension writes for the two events order-service publishes. The extra column names the topic. */
public record SagaExports(String aggregateId, String type, String topic, JsonNode payload, Instant timestamp)
        implements ExportedEvent<String, JsonNode> {

    public static SagaExports placed(Order order, ObjectMapper json) {
        JsonNode payload = json.createObjectNode()
                .put("orderId", order.id())
                .put("customerId", order.customerId())
                .put("itemSku", order.itemSku())
                .put("quantity", order.quantity())
                .put("amount", order.amount());
        return new SagaExports(order.id(), "OrderPlaced", Topics.ORDERS_PLACED, payload, Instant.now());
    }

    public static SagaExports cancelled(String orderId, CancelReason reason, ObjectMapper json) {
        JsonNode payload = json.createObjectNode().put("orderId", orderId).put("reason", reason.name());
        return new SagaExports(orderId, "OrderCancelled", Topics.ORDERS_CANCELLED, payload, Instant.now());
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
