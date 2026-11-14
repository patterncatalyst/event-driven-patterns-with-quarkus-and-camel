package com.patterncatalyst.shipping.correlation.order;

import java.math.BigDecimal;

/** Stock is held for the order: the first step of the saga is done. */
public record InventoryReserved(String orderId, String sku, int quantity, BigDecimal amount) {
}
