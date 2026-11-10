package com.patterncatalyst.shipping;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** A payment applied against an invoice. */
public record Settlement(String id, String invoiceId, BigDecimal amount, Instant settledAt) {

    public Settlement {
        Checks.text(id, "id");
        Checks.text(invoiceId, "invoiceId");
        Checks.nonNegative(amount, "amount");
        Objects.requireNonNull(settledAt, "settledAt");
    }
}
