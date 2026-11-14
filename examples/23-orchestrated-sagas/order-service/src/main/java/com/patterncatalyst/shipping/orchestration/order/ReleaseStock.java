package com.patterncatalyst.shipping.orchestration.order;

/** Command to inventory-service: put the order's quantity back. */
public record ReleaseStock(String orderId) {
}
