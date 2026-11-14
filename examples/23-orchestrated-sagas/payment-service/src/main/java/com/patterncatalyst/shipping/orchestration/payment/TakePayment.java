package com.patterncatalyst.shipping.orchestration.payment;

import java.math.BigDecimal;

/** Command from an orchestrator: take the amount for the order. */
public record TakePayment(String orderId, BigDecimal amount) {
}
