package com.patterncatalyst.shipping.queues;

import java.util.concurrent.CompletionStage;

import org.apache.kafka.clients.consumer.AcknowledgeType;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;

import io.smallrye.common.annotation.Blocking;
import io.smallrye.reactive.messaging.kafka.api.IncomingKafkaRecordMetadata;
import io.smallrye.reactive.messaging.kafka.queues.ShareGroupAcknowledgement;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Takes jobs from the share group on a worker thread and answers the broker per record: ack accepts the job,
 * a nack carrying ShareGroupAcknowledgement releases it for another attempt or rejects it for good.
 */
@ApplicationScoped
public class ShareDeliveryWorker {

    private final DeliveryService delivery;

    ShareDeliveryWorker(DeliveryService delivery) {
        this.delivery = delivery;
    }

    @Incoming("delivery-jobs")
    @Blocking
    public CompletionStage<Void> deliver(Message<DeliveryJob> message) {
        IncomingKafkaRecordMetadata<String, DeliveryJob> record = message.getMetadata(IncomingKafkaRecordMetadata.class)
                .orElseThrow();
        // The broker counts how many times it has handed this record out.
        int deliveryCount = record.getRecord().deliveryCount().map(Short::intValue).orElse(1);
        Outcome outcome = delivery.attempt("share", message.getPayload(), record.getPartition(), record.getOffset(),
                deliveryCount);
        return switch (outcome) {
            case SENT -> message.ack();
            case FAILED -> message.nack(new IllegalStateException("transient gateway failure"),
                    Metadata.of(ShareGroupAcknowledgement.from(AcknowledgeType.RELEASE)));
            case REJECTED -> message.nack(new IllegalStateException("permanent gateway failure"),
                    Metadata.of(ShareGroupAcknowledgement.from(AcknowledgeType.REJECT)));
        };
    }
}
