package com.patterncatalyst.shipping.queues;

import org.apache.kafka.clients.consumer.AcknowledgeType;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.reactive.messaging.kafka.api.IncomingKafkaRecordMetadata;
import io.smallrye.reactive.messaging.kafka.queues.ShareGroupAcknowledgement;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Takes one job at a time from the share group and answers the broker per record: ack accepts the job,
 * a nack carrying ShareGroupAcknowledgement releases it for another attempt or rejects it for good.
 */
@ApplicationScoped
public class ShareDeliveryWorker {

    private final DeliveryService delivery;

    ShareDeliveryWorker(DeliveryService delivery) {
        this.delivery = delivery;
    }

    @Incoming("delivery-jobs")
    public Uni<Void> deliver(Message<DeliveryJob> message) {
        IncomingKafkaRecordMetadata<String, DeliveryJob> record = message.getMetadata(IncomingKafkaRecordMetadata.class)
                .orElseThrow();
        // The broker counts how many times it has handed this record out.
        int deliveryCount = record.getRecord().deliveryCount().map(Short::intValue).orElse(1);
        // The gateway call blocks, so it runs on the worker pool. The method returns only when the job is
        // answered, which is what keeps the consumer from acquiring a second record meanwhile.
        return Uni.createFrom()
                .item(() -> delivery.attempt("share", message.getPayload(), record.getPartition(), record.getOffset(),
                        deliveryCount))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .chain(outcome -> Uni.createFrom().completionStage(switch (outcome) {
                    case SENT -> message.ack();
                    case FAILED -> message.nack(new IllegalStateException("transient gateway failure"),
                            Metadata.of(ShareGroupAcknowledgement.from(AcknowledgeType.RELEASE)));
                    case REJECTED -> message.nack(new IllegalStateException("permanent gateway failure"),
                            Metadata.of(ShareGroupAcknowledgement.from(AcknowledgeType.REJECT)));
                }));
    }
}
