package com.patterncatalyst.shipping.pickups;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.processor.TimestampExtractor;

/**
 * Event time from the payload. A record that went through an outbox or a replay carries the time it was written,
 * not the time the shipment was scheduled, so a window over the record timestamp would group by the wrong clock.
 */
public class PayloadTimestampExtractor implements TimestampExtractor {

    @Override
    public long extract(ConsumerRecord<Object, Object> record, long partitionTime) {
        if (record.value() instanceof HasEventTime event) {
            return event.occurredAt().toEpochMilli();
        }
        return record.timestamp();
    }
}
