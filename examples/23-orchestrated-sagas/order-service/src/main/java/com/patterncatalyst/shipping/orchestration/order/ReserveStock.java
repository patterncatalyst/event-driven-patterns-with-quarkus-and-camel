package com.patterncatalyst.shipping.orchestration.order;

/** Command to inventory-service: take the quantity out of stock for the order. */
public record ReserveStock(String orderId, String itemSku, int quantity) {
}
