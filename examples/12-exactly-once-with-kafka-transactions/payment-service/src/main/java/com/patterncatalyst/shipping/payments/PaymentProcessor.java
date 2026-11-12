package com.patterncatalyst.shipping.payments;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.kafka.KafkaRecord;
import io.smallrye.reactive.messaging.kafka.transactions.KafkaTransactions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Read, process, write as one Kafka transaction. Each poll's batch of InventoryReserved records becomes one
 * PaymentProcessed record per input inside a single transaction; the batch's consumer offsets are sent to the same
 * transaction, so the outputs and the input position commit together or not at all.
 */
@ApplicationScoped
public class PaymentProcessor {

    @Inject
    @Channel("payments-processed")
    KafkaTransactions<PaymentProcessed> transactions;

    @Inject
    CrashSwitch crash;

    @Incoming("inventory-reserved")
    public Uni<Void> process(Message<ConsumerRecords<String, InventoryReserved>> batch) {
        return transactions.withTransactionAndAck(batch, tx -> {
            for (ConsumerRecord<String, InventoryReserved> record : batch.getPayload()) {
                if (crash.armedFor(record.key())) {
                    return crash.halt();
                }
                tx.send(KafkaRecord.of(record.key(), PaymentProcessed.of(record.value())));
            }
            return Uni.createFrom().voidItem();
        });
    }
}
