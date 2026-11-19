package com.patterncatalyst.shipping.analysis;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.patterncatalyst.shipping.Carrier;
import com.patterncatalyst.shipping.Shipment;
import com.patterncatalyst.shipping.ShipmentStatus;

/** Records the tests share. */
final class Samples {

    private Samples() {
    }

    static String id() {
        return "E-" + UUID.randomUUID();
    }

    static DeliveryExceptionReported reported(String exceptionId, int attempt, String note) {
        String suffix = exceptionId.substring(2);
        var shipment = new Shipment("S-" + suffix, "O-" + suffix, Carrier.FASTLINE, "TRK-" + suffix, ShipmentStatus.SCHEDULED);
        return new DeliveryExceptionReported(exceptionId, shipment, attempt, note, Instant.now().minus(1, ChronoUnit.HOURS));
    }
}
