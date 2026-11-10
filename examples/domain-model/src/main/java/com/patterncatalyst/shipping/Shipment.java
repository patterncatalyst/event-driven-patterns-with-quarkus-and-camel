package com.patterncatalyst.shipping;

import java.util.Objects;

/** A parcel booked with a carrier. The tracking number is absent only while PENDING. */
public record Shipment(String id, String orderId, Carrier carrier, String trackingNumber, ShipmentStatus status) {

    public Shipment {
        Checks.text(id, "id");
        Checks.text(orderId, "orderId");
        Objects.requireNonNull(carrier, "carrier");
        Objects.requireNonNull(status, "status");
        if (trackingNumber == null) {
            if (status != ShipmentStatus.PENDING) {
                throw new IllegalArgumentException("trackingNumber is required once the shipment is " + status);
            }
        } else {
            Checks.text(trackingNumber, "trackingNumber");
        }
    }
}
