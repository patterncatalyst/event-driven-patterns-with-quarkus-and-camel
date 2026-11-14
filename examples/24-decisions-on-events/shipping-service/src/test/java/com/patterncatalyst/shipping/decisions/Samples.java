package com.patterncatalyst.shipping.decisions;

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
        String order = "O-" + exceptionId.substring(2);
        var shipment = new Shipment("S-" + exceptionId.substring(2), order, Carrier.FASTLINE, "TRK-" + exceptionId.substring(2),
                ShipmentStatus.SCHEDULED);
        return new DeliveryExceptionReported(exceptionId, shipment, attempt, note, Instant.now().minus(1, ChronoUnit.HOURS));
    }

    static DeliveryExceptionAnalysed analysed(String exceptionId, Reason reason, int attempt) {
        return new DeliveryExceptionAnalysed(exceptionId, "O-" + exceptionId, "S-" + exceptionId, Carrier.AIRCO, attempt, reason,
                "evidence", "test-model", "test-digest", DeliveryExceptionAnalyst.PROMPT_VERSION,
                Instant.now().minus(1, ChronoUnit.HOURS));
    }
}
