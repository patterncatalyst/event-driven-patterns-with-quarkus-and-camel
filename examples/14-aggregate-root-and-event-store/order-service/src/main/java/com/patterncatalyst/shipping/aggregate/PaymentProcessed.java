package com.patterncatalyst.shipping.aggregate;

import com.patterncatalyst.shipping.Payment;

/** The payload of a record on edp.payments.processed. */
public record PaymentProcessed(Payment payment) {
}
