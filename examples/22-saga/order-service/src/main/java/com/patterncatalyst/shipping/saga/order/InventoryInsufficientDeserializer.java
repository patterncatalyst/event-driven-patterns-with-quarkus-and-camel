package com.patterncatalyst.shipping.saga.order;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class InventoryInsufficientDeserializer extends ObjectMapperDeserializer<InventoryInsufficient> {

    public InventoryInsufficientDeserializer() {
        super(InventoryInsufficient.class);
    }
}
