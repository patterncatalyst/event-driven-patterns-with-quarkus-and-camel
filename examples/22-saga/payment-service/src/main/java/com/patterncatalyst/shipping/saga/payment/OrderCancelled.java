package com.patterncatalyst.shipping.saga.payment;

/** The saga ended without a payment, in the shape order-service publishes. */
public record OrderCancelled(String orderId, String reason) {
}
