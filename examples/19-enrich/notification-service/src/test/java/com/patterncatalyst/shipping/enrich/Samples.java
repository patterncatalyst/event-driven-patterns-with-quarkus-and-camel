package com.patterncatalyst.shipping.enrich;

import com.patterncatalyst.shipping.Carrier;
import com.patterncatalyst.shipping.Shipment;
import com.patterncatalyst.shipping.ShipmentStatus;

/** Records the tests share. */
final class Samples {

    static final String FASTLINE_TEMPLATE = "https://track.fastline.example/{trackingNumber}";
    static final String AIRCO_TEMPLATE = "https://track.airco.example/{trackingNumber}";

    private Samples() {
    }

    static CarrierReference fastline(String template) {
        return new CarrierReference(Carrier.FASTLINE, "ROAD", template);
    }

    static CarrierReference airco() {
        return new CarrierReference(Carrier.AIRCO, "AIR", AIRCO_TEMPLATE);
    }

    static ShipmentScheduled scheduled(String orderId, Carrier carrier) {
        return new ShipmentScheduled(new Shipment("S-" + orderId, orderId, carrier, "TRK-" + orderId, ShipmentStatus.SCHEDULED));
    }
}
