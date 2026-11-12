package com.patterncatalyst.shipping.snapshots;

/** A reservation larger than the quantity on hand: refused, nothing appended. */
public class OutOfStock extends RuntimeException {

    public OutOfStock(String message) {
        super(message);
    }
}
