package com.patterncatalyst.shipping;

import java.math.BigDecimal;

/** A carrier's price per kilogram on a lane. */
public record Tariff(String carrier, String origin, String destination, BigDecimal ratePerKg, String currency) {

    public Tariff {
        Checks.text(carrier, "carrier");
        Checks.text(origin, "origin");
        Checks.text(destination, "destination");
        Checks.nonNegative(ratePerKg, "ratePerKg");
        Checks.text(currency, "currency");
    }
}
