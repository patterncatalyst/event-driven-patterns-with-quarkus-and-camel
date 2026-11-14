package com.patterncatalyst.shipping.correlation.inventory;

/** The saga ended without a payment: the reservation, if there is one, has to be released. */
public record OrderCancelled(String orderId, String reason) {
}
