package com.patterncatalyst.shipping.aggregate;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class ShipmentScheduledDeserializer extends ObjectMapperDeserializer<ShipmentScheduled> {

    public ShipmentScheduledDeserializer() {
        super(ShipmentScheduled.class);
    }
}
