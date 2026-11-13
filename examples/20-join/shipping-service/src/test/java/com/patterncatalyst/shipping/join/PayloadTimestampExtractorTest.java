package com.patterncatalyst.shipping.join;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

/** The extractor reads the time of the fact from the payload and falls back to the record timestamp. */
class PayloadTimestampExtractorTest {

    private final PayloadTimestampExtractor extractor = new PayloadTimestampExtractor();

    @Test
    void usesTheTimeOfTheFactNotTheTimeOfThePublish() {
        var payload = new InventoryReserved("O-1", "SKU-20", 1, Times.at("09:00"));
        var record = new ConsumerRecord<Object, Object>("edp.inventory.reserved", 0, 0, 1_000L, null, 0, 0, "O-1", payload,
                new org.apache.kafka.common.header.internals.RecordHeaders(), java.util.Optional.empty());
        assertEquals(Times.at("09:00").toEpochMilli(), extractor.extract(record, -1));
    }

    @Test
    void fallsBackToTheRecordTimestampWithoutAnEventTime() {
        var payload = new InventoryReserved("O-1", "SKU-20", 1, null);
        var record = new ConsumerRecord<Object, Object>("edp.inventory.reserved", 0, 0, 1_000L, null, 0, 0, "O-1", payload,
                new org.apache.kafka.common.header.internals.RecordHeaders(), java.util.Optional.empty());
        assertEquals(1_000L, extractor.extract(record, -1));
    }
}
