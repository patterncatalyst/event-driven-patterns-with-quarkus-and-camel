package com.patterncatalyst.shipping.inbox;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class OrderPlacedDeserializer extends ObjectMapperDeserializer<OrderPlaced> {

    public OrderPlacedDeserializer() {
        super(OrderPlaced.class);
    }
}
