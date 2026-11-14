package com.patterncatalyst.shipping.orchestration.payment;

/** Command from an orchestrator, and the compensation of TakePayment: give the order's payment back. */
public record RefundPayment(String orderId) {
}
