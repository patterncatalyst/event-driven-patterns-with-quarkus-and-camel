package com.patterncatalyst.shipping.payments;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

/** Reads the record value as an InventoryReserved. */
public class InventoryReservedDeserializer extends ObjectMapperDeserializer<InventoryReserved> {

    public InventoryReservedDeserializer() {
        super(InventoryReserved.class);
    }
}
