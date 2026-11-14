package com.patterncatalyst.shipping.orchestration.inventory;

/** Command from an orchestrator: take the quantity out of stock for the order. */
public record ReserveStock(String orderId, String itemSku, int quantity) {
}
