package com.patterncatalyst.shipping;

import java.math.BigDecimal;
import java.util.Objects;

/** A payment taken for one order. */
public record Payment(String id, String orderId, BigDecimal amount, PaymentStatus status) {

    public Payment {
        Checks.text(id, "id");
        Checks.text(orderId, "orderId");
        amount = Checks.money(amount, "amount");
        Objects.requireNonNull(status, "status");
    }
}
