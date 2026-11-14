package com.patterncatalyst.shipping.orchestration.order;

/** Command to payment-service: give the order's payment back. */
public record RefundPayment(String orderId) {
}
