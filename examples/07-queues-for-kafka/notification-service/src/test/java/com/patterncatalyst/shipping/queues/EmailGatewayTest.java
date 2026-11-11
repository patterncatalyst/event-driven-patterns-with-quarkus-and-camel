package com.patterncatalyst.shipping.queues;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmailGatewayTest {

    private final EmailGateway gateway = new EmailGateway(0, 200);

    @Test
    void anOrdinaryCustomerIsSentOnTheFirstAttempt() {
        assertDoesNotThrow(() -> gateway.send("C-1001", 1));
    }

    @Test
    void aSlowCustomerTakesTheSlowTime() {
        long start = System.nanoTime();
        gateway.send("C-SLOW-1", 1);
        assertTrue((System.nanoTime() - start) / 1_000_000 >= 200);
    }

    @Test
    void aRetryCustomerFailsTransientlyOnTheFirstAttemptOnly() {
        GatewayFailure failure = assertThrows(GatewayFailure.class, () -> gateway.send("C-RETRY-1", 1));
        assertTrue(failure.isTransient());
        assertDoesNotThrow(() -> gateway.send("C-RETRY-1", 2));
    }

    @Test
    void aBounceCustomerFailsPermanentlyOnEveryAttempt() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            int n = attempt;
            GatewayFailure failure = assertThrows(GatewayFailure.class, () -> gateway.send("C-BOUNCE-1", n));
            assertFalse(failure.isTransient());
        }
    }
}
