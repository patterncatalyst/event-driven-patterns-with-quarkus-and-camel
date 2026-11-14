package com.patterncatalyst.shipping.orchestration.inventory;

/** Command from an orchestrator, and the compensation of ReserveStock: put the order's quantity back. */
public record ReleaseStock(String orderId) {
}
