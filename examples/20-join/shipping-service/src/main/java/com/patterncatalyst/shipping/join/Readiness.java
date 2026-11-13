package com.patterncatalyst.shipping.join;

import java.time.Instant;

/**
 * What shipping-service concludes about an order: ReadyToShip once stock is reserved and payment is taken,
 * PaymentOverdue when the reservation waited and no payment came. Both paths write this record, so their outputs
 * compare field for field.
 */
public record Readiness(String type, String orderId, Instant reservedAt, Instant paidAt) {

    public static final String READY_TO_SHIP = "ReadyToShip";
    public static final String PAYMENT_OVERDUE = "PaymentOverdue";

    public static Readiness readyToShip(String orderId, Instant reservedAt, Instant paidAt) {
        return new Readiness(READY_TO_SHIP, orderId, reservedAt, paidAt);
    }

    public static Readiness paymentOverdue(String orderId, Instant reservedAt) {
        return new Readiness(PAYMENT_OVERDUE, orderId, reservedAt, null);
    }

    /** The join function: a null payment is the left side of the join with nothing to match. */
    public static Readiness of(InventoryReserved reserved, PaymentProcessed paid) {
        return paid == null
                ? paymentOverdue(reserved.orderId(), reserved.occurredAt())
                : readyToShip(reserved.orderId(), reserved.occurredAt(), paid.occurredAt());
    }

    public boolean ready() {
        return READY_TO_SHIP.equals(type);
    }
}
