package com.patterncatalyst.shipping.saga.order;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class PaymentProcessedDeserializer extends ObjectMapperDeserializer<PaymentProcessed> {

    public PaymentProcessedDeserializer() {
        super(PaymentProcessed.class);
    }
}
