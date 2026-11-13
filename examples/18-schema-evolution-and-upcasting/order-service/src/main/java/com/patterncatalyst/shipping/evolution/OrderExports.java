package com.patterncatalyst.shipping.evolution;

import java.time.Instant;
import java.util.Map;

import com.patterncatalyst.shipping.Topics;

import io.debezium.outbox.quarkus.ExportedEvent;

/** The OrderPlaced outbox row: the Avro bytes as they go on the topic, and the topic the Event Router sends them to. */
public record OrderExports(String aggregateId, byte[] payload, Instant timestamp) implements ExportedEvent<String, byte[]> {

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
    public byte[] getPayload() {
        return payload;
    }

    @Override
    public Map<String, Object> getAdditionalFieldValues() {
        return Map.of("topic", Topics.ORDERS_PLACED);
    }
}
