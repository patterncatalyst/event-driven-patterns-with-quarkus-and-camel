package com.patterncatalyst.shipping.saga.payment;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.apache.camel.CamelContext;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * The gate route calls the replay by bean name. It must reach the CDI bean, with its configuration injected, and
 * its start must open the payment route.
 */
@QuarkusTest
class CancellationGateTest {

    @Inject
    CamelContext camel;

    @Test
    void theReplayIsAContainerBeanWithItsConfigurationInjected() {
        var handle = Arc.container().instance("cancellationReplay");
        assertTrue(handle.isAvailable());
        // An application-scoped bean is a client proxy; its fields live on the contextual instance.
        var replay = (CancellationReplay) ClientProxy.unwrap(handle.get());
        assertNotNull(replay.brokers);
        assertNotNull(replay.payments);
        assertNotNull(replay.json);
    }

    @Test
    void theGateRunsAndOpensThePaymentRoute() {
        Awaitility.await().atMost(Duration.ofSeconds(60)).untilAsserted(
                () -> assertTrue(camel.getRouteController().getRouteStatus("take-payment").isStarted()));
    }
}
