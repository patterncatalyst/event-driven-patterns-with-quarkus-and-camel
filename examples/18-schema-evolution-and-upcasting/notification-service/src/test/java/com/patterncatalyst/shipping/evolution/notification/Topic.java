package com.patterncatalyst.shipping.evolution.notification;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.ConfigProvider;

import com.patterncatalyst.shipping.Topics;
import com.patterncatalyst.shipping.evolution.avro.OrderPlaced;

import io.apicurio.registry.serde.avro.AvroKafkaSerializer;

/** Writes OrderPlaced records the way order-service releases do: Avro with the schema id in the first bytes. */
final class Topic {

    private Topic() {
    }

    /** A record of the previous release: written with v1, which has no shippingPriority. */
    static void sendV1(String orderId) throws Exception {
        Schema v1;
        try (var in = Topic.class.getResourceAsStream("/avro/order-placed-v1.avsc")) {
            v1 = new Schema.Parser().parse(in);
        }
        GenericRecord record = new GenericData.Record(v1);
        record.put("orderId", orderId);
        record.put("customerId", "C-1");
        record.put("itemSku", "SKU-1");
        record.put("quantity", 1);
        record.put("amount", "25.00");
        send(orderId, record);
    }

    static void sendV2(String orderId, String priority) throws Exception {
        send(orderId, OrderPlaced.newBuilder().setOrderId(orderId).setCustomerId("C-1").setItemSku("SKU-1").setQuantity(1)
                .setAmount("25.00").setShippingPriority(priority).build());
    }

    private static void send(String key, Object record) throws Exception {
        var serializer = new AvroKafkaSerializer<Object>();
        Map<String, Object> config = new HashMap<>();
        config.put("apicurio.registry.url", ConfigProvider.getConfig().getValue("evolution.registry-url", String.class));
        config.put("apicurio.registry.auto-register", "true");
        config.put("apicurio.registry.headers.enabled", "false");
        serializer.configure(config, false);
        byte[] bytes = serializer.serialize(Topics.ORDERS_PLACED, record);

        Properties props = new Properties();
        props.put("bootstrap.servers", ConfigProvider.getConfig().getValue("kafka.bootstrap.servers", String.class));
        props.put("key.serializer", StringSerializer.class.getName());
        props.put("value.serializer", ByteArraySerializer.class.getName());
        try (var producer = new KafkaProducer<String, byte[]>(props)) {
            producer.send(new ProducerRecord<>(Topics.ORDERS_PLACED, key, bytes)).get();
        }
    }
}
