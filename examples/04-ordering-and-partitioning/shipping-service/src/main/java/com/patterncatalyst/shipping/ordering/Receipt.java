package com.patterncatalyst.shipping.ordering;

import java.util.List;

import org.apache.camel.ExchangeProperty;
import org.apache.camel.Header;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.kafka.clients.producer.RecordMetadata;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

/** Where the broker put one event: the key it was sent with, the partition and the offset. */
public record Receipt(String type, int sequence, long streamSequence, String key, int partition, long offset) {

    @ApplicationScoped
    @Named("receipts")
    public static class Factory {

        public Receipt of(@ExchangeProperty("event") ShipmentEvent event,
                @Header(KafkaConstants.KEY) String key,
                @Header(KafkaConstants.KAFKA_RECORD_META) List<RecordMetadata> metadata) {
            RecordMetadata sent = metadata == null ? null : metadata.getFirst();
            return new Receipt(event.type(), event.sequence(), event.streamSequence(), key,
                    sent == null ? -1 : sent.partition(), sent == null ? -1 : sent.offset());
        }
    }
}
