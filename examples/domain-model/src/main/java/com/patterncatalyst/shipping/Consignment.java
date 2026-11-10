package com.patterncatalyst.shipping;

import java.math.BigDecimal;

/** A booking to move goods from an origin to a destination with one carrier. */
public record Consignment(String id, String origin, String destination, BigDecimal weightKg, String carrier) {

    public Consignment {
        Checks.text(id, "id");
        Checks.text(origin, "origin");
        Checks.text(destination, "destination");
        Checks.positive(weightKg, "weightKg");
        Checks.text(carrier, "carrier");
    }
}
