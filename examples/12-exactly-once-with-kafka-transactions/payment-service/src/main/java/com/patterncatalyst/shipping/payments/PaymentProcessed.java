package com.patterncatalyst.shipping.payments;

import com.patterncatalyst.shipping.Payment;
import com.patterncatalyst.shipping.PaymentStatus;

/** The output: the payment taken for one order. */
public record PaymentProcessed(Payment payment) {

    /** The one mapping both paths use, so a comparison differs only in its transaction. */
    public static PaymentProcessed of(InventoryReserved reserved) {
        return new PaymentProcessed(new Payment("P-" + reserved.orderId(), reserved.orderId(), reserved.amount(), PaymentStatus.COMPLETED));
    }
}
