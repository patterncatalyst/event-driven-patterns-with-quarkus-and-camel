package com.patterncatalyst.shipping.ordering;

/** The single-partition topic where every replica forwards its events so one consumer sees them all. */
final class ShipmentFunnel {

    static final String TOPIC = "edp.shipping.shipments-funnel";

    private ShipmentFunnel() {
    }
}
