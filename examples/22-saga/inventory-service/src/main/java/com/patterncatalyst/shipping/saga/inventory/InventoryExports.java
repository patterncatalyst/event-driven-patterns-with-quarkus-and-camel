package com.patterncatalyst.shipping.saga.inventory;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.Topics;

import io.debezium.outbox.quarkus.ExportedEvent;

/** What the outbox extension writes for the two answers of inventory. The extra column names the topic. */
public record InventoryExports(String aggregateId, String type, String topic, JsonNode payload, Instant timestamp)
        implements ExportedEvent<String, JsonNode> {

    public static InventoryExports reserved(OrderPlaced order, ObjectMapper json) {
        JsonNode payload = json.createObjectNode()
                .put("orderId", order.orderId())
                .put("sku", order.itemSku())
                .put("quantity", order.quantity())
                .put("amount", order.amount());
        return new InventoryExports(order.orderId(), "InventoryReserved", Topics.INVENTORY_RESERVED, payload, Instant.now());
    }

    public static InventoryExports insufficient(OrderPlaced order, ObjectMapper json) {
        JsonNode payload = json.createObjectNode()
                .put("orderId", order.orderId())
                .put("sku", order.itemSku())
                .put("quantity", order.quantity());
        return new InventoryExports(order.orderId(), "InventoryInsufficient", Topics.INVENTORY_INSUFFICIENT, payload, Instant.now());
    }

    @Override
    public String getAggregateId() {
        return aggregateId;
    }

    @Override
    public String getAggregateType() {
        return "Reservation";
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
