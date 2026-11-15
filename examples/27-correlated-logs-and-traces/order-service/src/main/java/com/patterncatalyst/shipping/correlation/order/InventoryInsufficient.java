package com.patterncatalyst.shipping.correlation.order;

/** The stock could not be held. Nothing was done, so nothing needs undoing. */
public record InventoryInsufficient(String orderId, String sku, int quantity) {
}
