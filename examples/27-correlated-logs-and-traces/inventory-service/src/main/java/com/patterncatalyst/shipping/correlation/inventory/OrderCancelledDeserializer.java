package com.patterncatalyst.shipping.correlation.inventory;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class OrderCancelledDeserializer extends ObjectMapperDeserializer<OrderCancelled> {

    public OrderCancelledDeserializer() {
        super(OrderCancelled.class);
    }
}
