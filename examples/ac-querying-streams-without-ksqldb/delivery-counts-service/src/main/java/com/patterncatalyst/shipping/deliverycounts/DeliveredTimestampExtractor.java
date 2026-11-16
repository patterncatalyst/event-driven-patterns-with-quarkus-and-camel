package com.patterncatalyst.shipping.deliverycounts;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.processor.TimestampExtractor;

/** Event time is the delivery time in the payload, not the time the record was written. */
public class DeliveredTimestampExtractor implements TimestampExtractor {

    @Override
    public long extract(ConsumerRecord<Object, Object> record, long partitionTime) {
        if (record.value() instanceof ShipmentDelivered delivered) {
            return delivered.deliveredAt().toEpochMilli();
        }
        return record.timestamp();
    }
}
