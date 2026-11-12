package com.patterncatalyst.shipping.inbox;

import java.math.BigDecimal;

/** The event payload: the order as it was placed, in the shape the outbox of the order service publishes. */
public record OrderPlaced(String orderId, String customerId, String itemSku, int quantity, BigDecimal amount) {
}
