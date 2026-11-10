package com.patterncatalyst.shipping;

/** A ship, identified by its IMO number. */
public record Vessel(String imo, String name) {

    public Vessel {
        Checks.text(imo, "imo");
        Checks.text(name, "name");
    }
}
