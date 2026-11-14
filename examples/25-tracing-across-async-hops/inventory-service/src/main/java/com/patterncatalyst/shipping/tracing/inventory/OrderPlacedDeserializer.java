package com.patterncatalyst.shipping.tracing.inventory;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class OrderPlacedDeserializer extends ObjectMapperDeserializer<OrderPlaced> {

    public OrderPlacedDeserializer() {
        super(OrderPlaced.class);
    }
}
