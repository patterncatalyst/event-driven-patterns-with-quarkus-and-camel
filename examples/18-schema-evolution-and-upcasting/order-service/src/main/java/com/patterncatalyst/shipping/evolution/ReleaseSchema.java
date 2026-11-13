package com.patterncatalyst.shipping.evolution;

import java.io.IOException;
import java.io.InputStream;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.patterncatalyst.shipping.Order;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Demo code. ORDER_PLACED_SCHEMA makes the service behave as the previous release (1) or as a broken release (3), so
 * that one build can play every release of the contract. A real service has one shape and none of this.
 */
@ApplicationScoped
public class ReleaseSchema {

    @ConfigProperty(name = "order.placed-schema")
    int release;

    public boolean isCurrent() {
        return release == 2;
    }

    /** The schema_version a row is stored with. */
    public int storedSchemaVersion() {
        return release == 1 ? 1 : Upcasters.CURRENT;
    }

    /** The payload the previous release stored: no shippingPriority. */
    public Object storedPayload(Order order, String priority) {
        return release == 1 ? new OrderPlacedV1(order) : new OrderEvent.OrderPlaced(order, priority);
    }

    /** The record an earlier or broken release would publish, built against its own schema file. */
    public GenericRecord wireRecord(Order order, String priority) {
        Schema schema = load(release == 1 ? "order-placed-v1.avsc" : "order-placed-v3.avsc");
        GenericRecord record = new GenericData.Record(schema);
        record.put("orderId", order.id());
        record.put("customerId", order.customerId());
        record.put("itemSku", order.itemSku());
        record.put("quantity", order.quantity());
        record.put("amount", order.amount().toPlainString());
        if (release == 3) {
            record.put("shippingPriority", priority);
        }
        return record;
    }

    private static Schema load(String name) {
        try (InputStream in = ReleaseSchema.class.getResourceAsStream("/avro/" + name)) {
            return new Schema.Parser().parse(in);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
