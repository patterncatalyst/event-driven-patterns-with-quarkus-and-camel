package com.patterncatalyst.shipping.join;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.processor.TimestampExtractor;

/**
 * Event time from the payload. Kafka Streams orders windows by the timestamp this returns, so a record that went
 * through an outbox or a replay is placed where its fact happened, not where it was published. A payload without an
 * event time falls back to the record timestamp.
 */
public class PayloadTimestampExtractor implements TimestampExtractor {

    @Override
    public long extract(ConsumerRecord<Object, Object> record, long partitionTime) {
        if (record.value() instanceof HasEventTime payload && payload.occurredAt() != null) {
            return payload.occurredAt().toEpochMilli();
        }
        return record.timestamp();
    }
}
