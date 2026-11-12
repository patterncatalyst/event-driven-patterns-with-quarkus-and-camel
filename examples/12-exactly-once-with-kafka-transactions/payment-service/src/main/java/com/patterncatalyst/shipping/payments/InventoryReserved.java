package com.patterncatalyst.shipping.payments;

import java.math.BigDecimal;

/** The input: stock was reserved for an order, and its amount is now due. */
public record InventoryReserved(String orderId, String sku, int quantity, BigDecimal amount) {
}
