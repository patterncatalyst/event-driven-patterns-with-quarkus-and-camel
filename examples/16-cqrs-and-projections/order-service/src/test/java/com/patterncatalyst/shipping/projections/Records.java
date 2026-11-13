package com.patterncatalyst.shipping.projections;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringSerializer;

import com.patterncatalyst.shipping.Carrier;
import com.patterncatalyst.shipping.Order;
import com.patterncatalyst.shipping.Payment;
import com.patterncatalyst.shipping.PaymentStatus;
import com.patterncatalyst.shipping.Shipment;
import com.patterncatalyst.shipping.ShipmentStatus;

import io.quarkus.narayana.jta.QuarkusTransaction;

/** Test helpers: events as the owning services publish them, records on the topics, and the row as stored. */
final class Records {

    private Records() {
    }

    static Events.OrderPlaced placed(String id, String customer) {
        return new Events.OrderPlaced(new Order(id, customer, "SKU-1", 1, new BigDecimal("25.00")));
    }

    static Events.OrderCancelled cancelled(String id) {
        return new Events.OrderCancelled(id, "changed mind");
    }

    static Events.PaymentProcessed paid(String id) {
        return new Events.PaymentProcessed(new Payment("P-" + id, id, new BigDecimal("25.00"), PaymentStatus.COMPLETED));
    }

    static Shipment shipment(String id, ShipmentStatus status) {
        return new Shipment("S-" + id, id, Carrier.FASTLINE, "FL-" + id, status);
    }

    static String placedJson(String id, String customer) {
        return "{\"order\":{\"id\":\"" + id + "\",\"customerId\":\"" + customer
                + "\",\"itemSku\":\"SKU-1\",\"quantity\":1,\"amount\":25.00}}";
    }

    static String paidJson(String id) {
        return "{\"payment\":{\"id\":\"P-" + id + "\",\"orderId\":\"" + id + "\",\"amount\":25.00,\"status\":\"COMPLETED\"}}";
    }

    static String cancelledJson(String id) {
        return "{\"orderId\":\"" + id + "\",\"reason\":\"changed mind\"}";
    }

    static String shipmentJson(String id, String status) {
        return "{\"shipment\":{\"id\":\"S-" + id + "\",\"orderId\":\"" + id
                + "\",\"carrier\":\"FASTLINE\",\"trackingNumber\":\"FL-" + id + "\",\"status\":\"" + status + "\"}}";
    }

    static void send(String bootstrap, String topic, String key, String type, String json) throws Exception {
        try (var producer = new KafkaProducer<>(Map.<String, Object>of("bootstrap.servers", bootstrap),
                new StringSerializer(), new StringSerializer())) {
            var record = new ProducerRecord<>(topic, null, key, json, List.of(
                    new RecordHeader("id", UUID.randomUUID().toString().getBytes()),
                    new RecordHeader("eventType", type.getBytes())));
            producer.send(record).get(10, TimeUnit.SECONDS);
        }
    }

    /** The row as stored, read in a transaction of its own; null when the order has no row. */
    static OrderView row(String id) {
        return QuarkusTransaction.requiringNew().call(() -> OrderView.findById(id));
    }

    /** Records on partition 0 that the group has not committed past. */
    static long lag(String bootstrap, String group, String topic) throws Exception {
        try (var admin = AdminClient.create(Map.<String, Object>of("bootstrap.servers", bootstrap))) {
            var partition = new TopicPartition(topic, 0);
            var committed = admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get().get(partition);
            long end = admin.listOffsets(Map.of(partition, OffsetSpec.latest())).all().get().get(partition).offset();
            return end - (committed == null ? 0 : committed.offset());
        }
    }

    static void await(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < end) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("condition not met within 60 seconds");
    }
}
