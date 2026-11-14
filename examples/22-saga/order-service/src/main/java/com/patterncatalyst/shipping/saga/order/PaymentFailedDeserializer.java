package com.patterncatalyst.shipping.saga.order;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class PaymentFailedDeserializer extends ObjectMapperDeserializer<PaymentFailed> {

    public PaymentFailedDeserializer() {
        super(PaymentFailed.class);
    }
}
