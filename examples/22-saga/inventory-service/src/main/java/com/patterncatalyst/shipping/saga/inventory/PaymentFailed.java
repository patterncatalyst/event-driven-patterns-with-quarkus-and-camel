package com.patterncatalyst.shipping.saga.inventory;

/** The payment was declined: the reservation has to be released. */
public record PaymentFailed(String orderId, String reason) {
}
