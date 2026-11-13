package com.patterncatalyst.shipping.join;

import java.time.Instant;

import com.patterncatalyst.shipping.Payment;

/** payment-service took the payment for an order, as the event carries it on edp.payments.processed. */
public record PaymentProcessed(Payment payment, Instant occurredAt) implements HasEventTime {
}
