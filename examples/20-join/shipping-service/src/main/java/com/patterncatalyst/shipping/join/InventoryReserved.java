package com.patterncatalyst.shipping.join;

import java.time.Instant;

/** inventory-service reserved stock for an order, as the event carries it on edp.inventory.reserved. */
public record InventoryReserved(String orderId, String sku, int quantity, Instant occurredAt) implements HasEventTime {
}
