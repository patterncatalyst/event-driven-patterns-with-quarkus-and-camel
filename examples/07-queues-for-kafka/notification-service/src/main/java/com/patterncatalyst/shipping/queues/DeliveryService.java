package com.patterncatalyst.shipping.queues;

import java.time.Instant;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * One delivery attempt for one record: call the gateway, then log the outcome. Both the share consumer and the
 * consumer-group route call it, so the two modes differ only in how Kafka hands them the records.
 */
@ApplicationScoped
public class DeliveryService {

    private final EmailGateway gateway;
    private final DeliveryLog log;
    private final String worker;

    DeliveryService(EmailGateway gateway, DeliveryLog log, @ConfigProperty(name = "delivery.worker") String worker) {
        this.gateway = gateway;
        this.log = log;
        this.worker = worker;
    }

    public Outcome attempt(String mode, DeliveryJob job, int partition, long offset, int deliveryCount) {
        Instant started = Instant.now();
        Outcome outcome = Outcome.SENT;
        try {
            gateway.send(job.customerId(), deliveryCount);
        } catch (GatewayFailure failure) {
            outcome = failure.isTransient() ? Outcome.FAILED : Outcome.REJECTED;
        }
        DeliveryRow row = new DeliveryRow();
        row.orderId = job.orderId();
        row.customerId = job.customerId();
        row.mode = mode;
        row.worker = worker;
        row.recordPartition = partition;
        row.recordOffset = offset;
        row.deliveryCount = deliveryCount;
        row.startedAt = started;
        row.finishedAt = Instant.now();
        row.outcome = outcome.text();
        log.append(row);
        return outcome;
    }
}
