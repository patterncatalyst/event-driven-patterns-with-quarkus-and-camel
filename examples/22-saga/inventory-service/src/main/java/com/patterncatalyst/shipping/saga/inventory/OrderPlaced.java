package com.patterncatalyst.shipping.saga.inventory;

import java.math.BigDecimal;

/** The order as it was placed, in the shape order-service publishes. */
public record OrderPlaced(String orderId, String customerId, String itemSku, int quantity, BigDecimal amount) {
}
