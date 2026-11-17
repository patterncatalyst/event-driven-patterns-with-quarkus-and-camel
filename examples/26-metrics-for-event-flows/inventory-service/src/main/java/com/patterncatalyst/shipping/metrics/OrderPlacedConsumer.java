package com.patterncatalyst.shipping.metrics;

import java.io.IOException;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Reads the raw bytes of each record, so a failure leaves the original record intact for the dead letter queue.
 * A failure is not retried: the record is nacked and lands on edp.orders.placed-dlq with its headers.
 */
@ApplicationScoped
public class OrderPlacedConsumer {

    private final ObjectMapper json;
    private final ReservationService reservations;
    private final FlowAge age;

    OrderPlacedConsumer(ObjectMapper json, ReservationService reservations, FlowAge age) {
        this.json = json;
        this.reservations = reservations;
        this.age = age;
    }

    @Incoming("placed")
    @Blocking
    public void on(ConsumerRecord<String, byte[]> record) throws IOException {
        age.record(record);
        reservations.reserve(json.readValue(record.value(), OrderPlaced.class));
    }
}
