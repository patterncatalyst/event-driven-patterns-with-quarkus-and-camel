package com.patterncatalyst.shipping.correlation.order;

/** The payment was declined: the reservation has to be released. */
public record PaymentFailed(String orderId, String reason) {
}
