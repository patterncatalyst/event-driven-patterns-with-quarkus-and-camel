package com.patterncatalyst.shipping.saga.inventory;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class OrderCancelledDeserializer extends ObjectMapperDeserializer<OrderCancelled> {

    public OrderCancelledDeserializer() {
        super(OrderCancelled.class);
    }
}
