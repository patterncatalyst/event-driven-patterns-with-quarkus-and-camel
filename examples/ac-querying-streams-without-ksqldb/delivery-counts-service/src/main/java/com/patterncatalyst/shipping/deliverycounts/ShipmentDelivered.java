package com.patterncatalyst.shipping.deliverycounts;

import com.patterncatalyst.shipping.Carrier;
import java.time.Instant;
import java.util.Objects;

/** The event on {@code edp.shipping.delivered}: the parcel of an order reached its recipient at {@code deliveredAt}. */
public record ShipmentDelivered(String orderId, Carrier carrier, Instant deliveredAt) {

    public ShipmentDelivered {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(carrier, "carrier");
        Objects.requireNonNull(deliveredAt, "deliveredAt");
    }
}
