package com.patterncatalyst.shipping;

/** One container slot on a sailing. */
public record SlotId(String sailingId, int number) {

    public SlotId {
        Checks.text(sailingId, "sailingId");
        if (number < 0) {
            throw new IllegalArgumentException("number must not be negative");
        }
    }
}
