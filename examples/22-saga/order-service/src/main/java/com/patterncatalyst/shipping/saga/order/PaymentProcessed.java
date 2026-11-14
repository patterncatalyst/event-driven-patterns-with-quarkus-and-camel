package com.patterncatalyst.shipping.saga.order;

import com.patterncatalyst.shipping.Payment;

/** The payment was taken, in the shape payment-service publishes. */
public record PaymentProcessed(Payment payment) {
}
