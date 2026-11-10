package com.patterncatalyst.shipping;

import java.math.BigDecimal;

/** A bill raised for a consignment. */
public record Invoice(String id, String consignmentId, BigDecimal amount, String currency) {

    public Invoice {
        Checks.text(id, "id");
        Checks.text(consignmentId, "consignmentId");
        Checks.nonNegative(amount, "amount");
        Checks.text(currency, "currency");
    }
}
