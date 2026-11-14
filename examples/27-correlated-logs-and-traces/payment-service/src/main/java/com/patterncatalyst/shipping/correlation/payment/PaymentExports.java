package com.patterncatalyst.shipping.correlation.payment;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.Payment;
import com.patterncatalyst.shipping.Topics;

import io.debezium.outbox.quarkus.ExportedEvent;

/** What the outbox extension writes for the two answers of payment-service. The extra column names the topic. */
public record PaymentExports(String aggregateId, String type, String topic, JsonNode payload, Instant timestamp)
        implements ExportedEvent<String, JsonNode> {

    public static PaymentExports processed(Payment payment, ObjectMapper json) {
        JsonNode payload = json.createObjectNode().set("payment", json.valueToTree(payment));
        return new PaymentExports(payment.orderId(), "PaymentProcessed", Topics.PAYMENTS_PROCESSED, payload, Instant.now());
    }

    public static PaymentExports failed(String orderId, String reason, ObjectMapper json) {
        JsonNode payload = json.createObjectNode().put("orderId", orderId).put("reason", reason);
        return new PaymentExports(orderId, "PaymentFailed", Topics.PAYMENTS_FAILED, payload, Instant.now());
    }

    @Override
    public String getAggregateId() {
        return aggregateId;
    }

    @Override
    public String getAggregateType() {
        return "Payment";
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
