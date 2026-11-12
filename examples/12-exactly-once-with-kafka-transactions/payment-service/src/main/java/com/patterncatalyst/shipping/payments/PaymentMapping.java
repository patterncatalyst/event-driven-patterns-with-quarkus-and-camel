package com.patterncatalyst.shipping.payments;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** The mapping the Camel route applies to each record: InventoryReserved in, PaymentProcessed out. */
@ApplicationScoped
public class PaymentMapping {

    @Inject
    CrashSwitch crash;

    public PaymentProcessed toPayment(InventoryReserved reserved) {
        if (crash.armedFor(reserved.orderId())) {
            crash.halt().await().indefinitely();
        }
        return PaymentProcessed.of(reserved);
    }
}
