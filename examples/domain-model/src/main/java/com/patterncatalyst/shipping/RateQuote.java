package com.patterncatalyst.shipping;

import java.math.BigDecimal;

/** A price offered for a consignment by a carrier. */
public record RateQuote(String id, String consignmentId, String carrier, BigDecimal amount, String currency) {

    public RateQuote {
        Checks.text(id, "id");
        Checks.text(consignmentId, "consignmentId");
        Checks.text(carrier, "carrier");
        Checks.nonNegative(amount, "amount");
        Checks.text(currency, "currency");
    }
}
