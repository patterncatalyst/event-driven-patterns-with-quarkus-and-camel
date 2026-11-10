package com.patterncatalyst.shipping.crudvsevents;

import com.patterncatalyst.shipping.ConsignmentStatus;

/** BOOKED, LOADED, IN_TRANSIT, CLEARED, DELIVERED: one step forward at a time. */
final class Lifecycle {

    private Lifecycle() {
    }

    static boolean allows(ConsignmentStatus from, ConsignmentStatus to) {
        return to.ordinal() == from.ordinal() + 1;
    }
}
