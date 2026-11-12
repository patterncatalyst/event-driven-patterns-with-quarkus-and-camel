package com.patterncatalyst.shipping.payments;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import org.apache.kafka.common.errors.TransactionAbortedException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.PaymentStatus;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.kafka.KafkaRecord;
import io.smallrye.reactive.messaging.kafka.transactions.KafkaTransactions;
import jakarta.inject.Inject;

/** The read-process-write loop against the Kafka broker of Dev Services. */
@QuarkusTest
class PaymentProcessorTest {

    static final String COMMITTED = "read_committed";
    static final String UNCOMMITTED = "read_uncommitted";

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Inject
    ObjectMapper mapper;

    @Inject
    @Channel("payments-processed")
    KafkaTransactions<PaymentProcessed> transactions;

    @Test
    void aBatchOfFiveGivesExactlyFivePaymentsToAReadCommittedReader() throws Exception {
        List<String> orders = IntStream.rangeClosed(1, 5).mapToObj(i -> "O-800" + i).toList();
        for (String order : orders) {
            Kafka.reserve(bootstrapServers, order, "59.90");
        }
        await().atMost(Duration.ofSeconds(60)).untilAsserted(
                () -> assertEquals(5, outputs(COMMITTED, "O-800").size()));

        List<Kafka.Output> after = outputs(COMMITTED, "O-800");
        assertEquals(orders, after.stream().map(Kafka.Output::key).sorted().toList());
        var payment = mapper.readTree(after.getFirst().value()).get("payment");
        assertEquals("P-" + after.getFirst().key(), payment.get("id").asText());
        assertEquals(PaymentStatus.COMPLETED.name(), payment.get("status").asText());
        assertEquals(0, new BigDecimal("59.90").compareTo(payment.get("amount").decimalValue()));
    }

    @Test
    void anAbortedTransactionIsInvisibleToAReadCommittedReaderAndStillInTheLog() {
        assertThrows(TransactionAbortedException.class, () -> transactions.withTransactionAndAwait(tx -> {
            tx.send(KafkaRecord.of("O-9001", payment("O-9001")));
            tx.markForAbort();
            return Uni.createFrom().voidItem();
        }));
        transactions.withTransactionAndAwait(tx -> {
            tx.send(KafkaRecord.of("O-9002", payment("O-9002")));
            return Uni.createFrom().voidItem();
        });

        await().atMost(Duration.ofSeconds(30)).untilAsserted(
                () -> assertEquals(1, outputs(COMMITTED, "O-900").size()));
        assertEquals("O-9002", outputs(COMMITTED, "O-900").getFirst().key());
        assertEquals(List.of("O-9001", "O-9002"), outputs(UNCOMMITTED, "O-900").stream().map(Kafka.Output::key).toList());
    }

    @Test
    void theInputOffsetIsCommittedWithTheOutput() throws Exception {
        long offset = Kafka.reserve(bootstrapServers, "O-8101", "120.00");
        await().atMost(Duration.ofSeconds(60)).untilAsserted(
                () -> assertFalse(outputs(COMMITTED, "O-8101").isEmpty()));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(
                () -> assertEquals(offset + 1, Kafka.committed(bootstrapServers, "payment-service")));
        assertEquals(1, outputs(COMMITTED, "O-8101").size());
    }

    List<Kafka.Output> outputs(String isolationLevel, String keyPrefix) {
        return Kafka.payments(bootstrapServers, isolationLevel).stream()
                .filter(o -> o.key().startsWith(keyPrefix))
                .toList();
    }

    static PaymentProcessed payment(String orderId) {
        return PaymentProcessed.of(new InventoryReserved(orderId, "SKU-42", 3, new BigDecimal("59.90")));
    }
}
