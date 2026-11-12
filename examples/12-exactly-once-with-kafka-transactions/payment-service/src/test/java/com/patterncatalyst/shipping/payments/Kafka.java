package com.patterncatalyst.shipping.payments;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import com.patterncatalyst.shipping.Topics;

/** Plain Kafka clients for the tests: a producer for the input and consumers that read the output at either isolation level. */
final class Kafka {

    record Output(String key, String value) {
    }

    private Kafka() {
    }

    /** Produces one InventoryReserved record and returns its offset. */
    static long reserve(String bootstrapServers, String orderId, String amount) throws Exception {
        String value = "{\"orderId\":\"%s\",\"sku\":\"SKU-42\",\"quantity\":3,\"amount\":%s}".formatted(orderId, amount);
        try (var producer = new KafkaProducer<String, String>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()))) {
            return producer.send(new ProducerRecord<>(Topics.INVENTORY_RESERVED, orderId, value)).get().offset();
        }
    }

    /** Every record of edp.payments.processed visible at the isolation level ("read_committed" or "read_uncommitted"). */
    static List<Output> payments(String bootstrapServers, String isolationLevel) {
        List<Output> seen = new ArrayList<>();
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ISOLATION_LEVEL_CONFIG, isolationLevel,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()))) {
            consumer.subscribe(List.of(Topics.PAYMENTS_PROCESSED));
            long end = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (System.nanoTime() < end) {
                consumer.poll(Duration.ofMillis(500)).forEach(r -> seen.add(new Output(r.key(), r.value())));
            }
        }
        return seen;
    }

    /** The offset the group has committed on the input topic's only partition, or -1 when it has none. */
    static long committed(String bootstrapServers, String group) throws Exception {
        try (Admin admin = Admin.create(Map.of("bootstrap.servers", bootstrapServers))) {
            Map<TopicPartition, OffsetAndMetadata> offsets = admin.listConsumerGroupOffsets(group)
                    .partitionsToOffsetAndMetadata().get();
            OffsetAndMetadata committed = offsets.get(new TopicPartition(Topics.INVENTORY_RESERVED, 0));
            return committed == null ? -1 : committed.offset();
        }
    }
}
