package com.patterncatalyst.shipping.evolution;

import com.patterncatalyst.shipping.Order;

/** Demo code: the shape the previous release stored (schema_version 1), written only when ORDER_PLACED_SCHEMA=1. */
public record OrderPlacedV1(Order order) {
}
