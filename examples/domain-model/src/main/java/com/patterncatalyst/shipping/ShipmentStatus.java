package com.patterncatalyst.shipping;

/** Shipment lifecycle: one step forward at a time. */
public enum ShipmentStatus {
    PENDING, SCHEDULED, IN_TRANSIT, DELIVERED;

    public boolean canMoveTo(ShipmentStatus next) {
        return ordinal() + 1 == next.ordinal();
    }
}
