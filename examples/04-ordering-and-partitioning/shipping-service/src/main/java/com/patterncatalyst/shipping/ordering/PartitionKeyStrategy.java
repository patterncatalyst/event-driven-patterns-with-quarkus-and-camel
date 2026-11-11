package com.patterncatalyst.shipping.ordering;

/** What the record key is built from. The key decides the partition; the partition decides the order. */
public enum PartitionKeyStrategy {
    /** All events of one order share a partition, so Kafka keeps their order. */
    ORDER_ID,
    /** Events of one type share a partition, so one order is spread across partitions. */
    EVENT_TYPE,
    /** Events of one carrier share a partition: order per order is kept, but only two keys exist. */
    CARRIER;

    public String keyFor(ShipmentEvent event) {
        return switch (this) {
            case ORDER_ID -> event.orderId();
            case EVENT_TYPE -> event.type();
            case CARRIER -> event.carrier().name();
        };
    }
}
