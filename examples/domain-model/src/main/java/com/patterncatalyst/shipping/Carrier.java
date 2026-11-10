package com.patterncatalyst.shipping;

/** A company that moves consignments. Known codes are {@link #FASTLINE} and {@link #AIRCO}. */
public record Carrier(String code, String name) {

    public static final String FASTLINE = "FASTLINE";
    public static final String AIRCO = "AIRCO";

    public Carrier {
        Checks.text(code, "code");
        Checks.text(name, "name");
    }
}
