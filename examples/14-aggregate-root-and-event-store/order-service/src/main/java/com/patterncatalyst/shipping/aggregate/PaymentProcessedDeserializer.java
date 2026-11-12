package com.patterncatalyst.shipping.aggregate;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class PaymentProcessedDeserializer extends ObjectMapperDeserializer<PaymentProcessed> {

    public PaymentProcessedDeserializer() {
        super(PaymentProcessed.class);
    }
}
