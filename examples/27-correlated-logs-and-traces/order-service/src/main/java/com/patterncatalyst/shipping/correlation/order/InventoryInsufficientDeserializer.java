package com.patterncatalyst.shipping.correlation.order;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class InventoryInsufficientDeserializer extends ObjectMapperDeserializer<InventoryInsufficient> {

    public InventoryInsufficientDeserializer() {
        super(InventoryInsufficient.class);
    }
}
