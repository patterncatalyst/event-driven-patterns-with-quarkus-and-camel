package com.patterncatalyst.shipping.saga.order;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class InventoryReservedDeserializer extends ObjectMapperDeserializer<InventoryReserved> {

    public InventoryReservedDeserializer() {
        super(InventoryReserved.class);
    }
}
