package com.patterncatalyst.shipping.orchestration.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.Topics;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/** TakePayment and RefundPayment as commands on the topic and as service calls, with Dev Services. */
@QuarkusTest
class PaymentTest {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrap;

    @Inject
    PaymentService payments;

    private static String id() {
        return "O-" + UUID.randomUUID();
    }

    private static String status(String orderId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            PaymentEntity p = PaymentEntity.findById(orderId);
            return p == null ? null : p.status;
        });
    }

    private static String take(String id, String amount) {
        return "{\"orderId\":\"" + id + "\",\"amount\":" + amount + "}";
    }

    @Test
    void takeWithoutASagaHeaderRunsPlainAndAnswersPaid() throws Exception {
        String id = id();
        Records.command(bootstrap, Topics.PAYMENTS_COMMANDS, "TakePayment", id, take(id, "100.00"), Map.of());

        var replies = Records.replies(bootstrap, Topics.PAYMENTS_PROCESSED, id, Duration.ofSeconds(30));
        assertEquals(1, replies.size());
        assertEquals("PaymentProcessed", Records.header(replies.get(0), "type"));
        assertEquals("PaymentProcessed", Records.header(replies.get(0), "ce_type"), "every reply is a CloudEvent");
        assertEquals(null, Records.header(replies.get(0), "ce_flowinstanceid"), "no workflow instance to name");
        assertEquals("COMPLETED", status(id));
    }

    @Test
    void anAmountAboveTheLimitIsDeclinedOnTheFailedTopic() throws Exception {
        String id = id();
        Records.command(bootstrap, Topics.PAYMENTS_COMMANDS, "TakePayment", id, take(id, "900.00"),
                Map.of("flowinstanceid", "01INSTANCE"));

        var replies = Records.replies(bootstrap, Topics.PAYMENTS_FAILED, id, Duration.ofSeconds(30));
        assertEquals(1, replies.size());
        assertEquals("01INSTANCE", Records.header(replies.get(0), "ce_flowinstanceid"));
        assertEquals("PaymentFailed", Records.header(replies.get(0), "ce_type"));
        assertEquals("FAILED", status(id));
    }

    @Test
    void takeThenRefundLeavesRefunded() {
        String id = id();
        payments.take(new TakePayment(id, new BigDecimal("100.00")));
        assertTrue(payments.refund(id));
        assertEquals("REFUNDED", status(id));
        assertFalse(payments.refund(id), "a second refund changes nothing");
    }

    @Test
    void refundBeforeTakeIsRefusedAndStoresNothing() {
        String id = id();
        assertFalse(payments.refund(id));
        assertNull(status(id));
    }

    @Test
    void aRepeatedTakeTakesOnce() {
        String id = id();
        payments.take(new TakePayment(id, new BigDecimal("100.00")));
        var again = payments.take(new TakePayment(id, new BigDecimal("100.00")));
        assertEquals(Reply.PAID, again.outcome());
        assertEquals("COMPLETED", status(id));
    }
}
