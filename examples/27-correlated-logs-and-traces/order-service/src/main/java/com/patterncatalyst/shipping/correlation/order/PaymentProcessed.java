package com.patterncatalyst.shipping.correlation.order;

import com.patterncatalyst.shipping.Payment;

/** The payment was taken, in the shape payment-service publishes. */
public record PaymentProcessed(Payment payment) {
}
