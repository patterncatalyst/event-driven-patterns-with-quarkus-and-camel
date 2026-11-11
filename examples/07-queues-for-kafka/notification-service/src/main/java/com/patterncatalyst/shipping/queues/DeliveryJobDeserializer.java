package com.patterncatalyst.shipping.queues;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

/** Reads the record value as a DeliveryJob. A value that does not parse is rejected by the share consumer. */
public class DeliveryJobDeserializer extends ObjectMapperDeserializer<DeliveryJob> {

    public DeliveryJobDeserializer() {
        super(DeliveryJob.class);
    }
}
