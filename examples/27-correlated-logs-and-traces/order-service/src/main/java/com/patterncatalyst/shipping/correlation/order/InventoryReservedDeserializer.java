package com.patterncatalyst.shipping.correlation.order;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class InventoryReservedDeserializer extends ObjectMapperDeserializer<InventoryReserved> {

    public InventoryReservedDeserializer() {
        super(InventoryReserved.class);
    }
}
