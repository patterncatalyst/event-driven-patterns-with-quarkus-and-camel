package com.patterncatalyst.shipping;

import java.util.List;

/** Kafka topic names, so no example hard-codes a typo. */
public final class Topics {

    public static final String ORDERS_PLACED = "edp.orders.placed";
    public static final String ORDERS_CANCELLED = "edp.orders.cancelled";
    public static final String INVENTORY_RESERVED = "edp.inventory.reserved";
    public static final String INVENTORY_INSUFFICIENT = "edp.inventory.insufficient";
    public static final String PAYMENTS_PROCESSED = "edp.payments.processed";
    public static final String PAYMENTS_FAILED = "edp.payments.failed";
    public static final String SHIPPING_SCHEDULED = "edp.shipping.scheduled";
    public static final String SHIPPING_IN_TRANSIT = "edp.shipping.in-transit";
    public static final String SHIPPING_DELIVERED = "edp.shipping.delivered";

    /** One ordered stream of shipment status changes, keyed by order id. */
    public static final String SHIPPING_SHIPMENTS = "edp.shipping.shipments";

    /** The nine event catalog topics. */
    public static final List<String> ALL = List.of(
            ORDERS_PLACED, ORDERS_CANCELLED,
            INVENTORY_RESERVED, INVENTORY_INSUFFICIENT,
            PAYMENTS_PROCESSED, PAYMENTS_FAILED,
            SHIPPING_SCHEDULED, SHIPPING_IN_TRANSIT, SHIPPING_DELIVERED);

    private Topics() {
    }
}
