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

    /** Raw Debezium change envelopes of the legacy inventory_item table. */
    public static final String INVENTORY_ITEM_CHANGES = "edp.inventory.item-changes";

    /** Compacted InventoryItem state documents, keyed by sku. */
    public static final String INVENTORY_ITEMS = "edp.inventory.items";

    /** Dead letters of edp.orders.placed: records that cannot be parsed or are invalid. */
    public static final String ORDERS_PLACED_DLQ = "edp.orders.placed-dlq";

    /** Compacted carrier reference records keyed by carrier, published by shipping-service. */
    public static final String SHIPPING_CARRIERS = "edp.shipping.carriers";

    /** ShipmentScheduled enriched with carrier mode and tracking URL (Kafka Streams output). */
    public static final String SHIPPING_SCHEDULED_ENRICHED = "edp.shipping.scheduled-enriched";

    /** ShipmentScheduled enriched with carrier mode and tracking URL (Camel output). */
    public static final String SHIPPING_SCHEDULED_ENRICHED_CAMEL = "edp.shipping.scheduled-enriched-camel";

    /** ReadyToShip and PaymentOverdue results of the order join (Kafka Streams output). */
    public static final String SHIPPING_ORDER_READINESS = "edp.shipping.order-readiness";

    /** ReadyToShip and PaymentOverdue results of the order join (Camel output). */
    public static final String SHIPPING_ORDER_READINESS_CAMEL = "edp.shipping.order-readiness-camel";

    /** Per-carrier pickup lists (Kafka Streams output). */
    public static final String SHIPPING_PICKUP_LISTS = "edp.shipping.pickup-lists";

    /** Per-carrier pickup lists (Camel output). */
    public static final String SHIPPING_PICKUP_LISTS_CAMEL = "edp.shipping.pickup-lists-camel";

    /** The nine event catalog topics. */
    public static final List<String> ALL = List.of(
            ORDERS_PLACED, ORDERS_CANCELLED,
            INVENTORY_RESERVED, INVENTORY_INSUFFICIENT,
            PAYMENTS_PROCESSED, PAYMENTS_FAILED,
            SHIPPING_SCHEDULED, SHIPPING_IN_TRANSIT, SHIPPING_DELIVERED);

    private Topics() {
    }
}
