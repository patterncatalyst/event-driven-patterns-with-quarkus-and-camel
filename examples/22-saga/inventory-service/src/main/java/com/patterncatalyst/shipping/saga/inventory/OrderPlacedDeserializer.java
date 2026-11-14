package com.patterncatalyst.shipping.saga.inventory;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class OrderPlacedDeserializer extends ObjectMapperDeserializer<OrderPlaced> {

    public OrderPlacedDeserializer() {
        super(OrderPlaced.class);
    }
}
