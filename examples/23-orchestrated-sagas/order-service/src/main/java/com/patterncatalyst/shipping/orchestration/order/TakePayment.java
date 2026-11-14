package com.patterncatalyst.shipping.orchestration.order;

import java.math.BigDecimal;

/** Command to payment-service: take the amount for the order. */
public record TakePayment(String orderId, BigDecimal amount) {
}
