package com.patterncatalyst.shipping.orchestration.payment;

import java.math.BigDecimal;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/** Take and refund, one payment per order. */
@ApplicationScoped
public class PaymentService {

    /** Demo code: an amount above this is declined, so a saga can be sent down its failure path. */
    @ConfigProperty(name = "payment.decline-above")
    BigDecimal declineAbove;

    /** Takes the payment once per order; a repeat answers like the first call. */
    @Transactional
    public Reply take(TakePayment command) {
        PaymentEntity existing = PaymentEntity.findById(command.orderId());
        if (existing != null) {
            return new Reply(command.orderId(), "FAILED".equals(existing.status) ? Reply.DECLINED : Reply.PAID);
        }
        PaymentEntity payment = new PaymentEntity();
        payment.orderId = command.orderId();
        payment.amount = command.amount();
        boolean declined = command.amount().compareTo(declineAbove) > 0;
        payment.status = declined ? "FAILED" : "COMPLETED";
        payment.persist();
        return new Reply(command.orderId(), declined ? Reply.DECLINED : Reply.PAID);
    }

    /** Refunds a completed payment once. With no payment it refuses and stores nothing; returns whether it refunded. */
    @Transactional
    public boolean refund(String orderId) {
        PaymentEntity payment = PaymentEntity.findById(orderId);
        if (payment == null || !"COMPLETED".equals(payment.status)) {
            return false;
        }
        payment.status = "REFUNDED";
        return true;
    }
}
