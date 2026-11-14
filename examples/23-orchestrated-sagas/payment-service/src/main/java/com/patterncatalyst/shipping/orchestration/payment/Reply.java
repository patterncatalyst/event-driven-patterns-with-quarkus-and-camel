package com.patterncatalyst.shipping.orchestration.payment;

import com.patterncatalyst.shipping.Topics;

/** The answer to TakePayment. The outcome is in the payload, so a workflow can branch on it. */
public record Reply(String orderId, String outcome) {

    public static final String PAID = "PAID";
    public static final String DECLINED = "DECLINED";

    public String topic() {
        return PAID.equals(outcome) ? Topics.PAYMENTS_PROCESSED : Topics.PAYMENTS_FAILED;
    }

    public String eventType() {
        return PAID.equals(outcome) ? "PaymentProcessed" : "PaymentFailed";
    }
}
