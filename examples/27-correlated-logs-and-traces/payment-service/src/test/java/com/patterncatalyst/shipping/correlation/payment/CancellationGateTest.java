package com.patterncatalyst.shipping.correlation.payment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.apache.camel.CamelContext;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * The gate opens the payment route once the cancellation consumer has nothing left to read.
 */
@QuarkusTest
class CancellationGateTest {

    @Inject
    CamelContext camel;

    @Test
    void theGateRunsAndOpensThePaymentRoute() {
        Awaitility.await().atMost(Duration.ofSeconds(60)).untilAsserted(
                () -> assertTrue(camel.getRouteController().getRouteStatus("take-payment").isStarted()));
    }
}
