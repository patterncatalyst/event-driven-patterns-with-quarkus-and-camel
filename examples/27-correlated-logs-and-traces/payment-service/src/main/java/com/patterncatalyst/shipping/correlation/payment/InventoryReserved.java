package com.patterncatalyst.shipping.correlation.payment;

import java.math.BigDecimal;

/** Stock is held for the order and the payment may be taken, in the shape inventory-service publishes. */
public record InventoryReserved(String orderId, String sku, int quantity, BigDecimal amount) {
}
