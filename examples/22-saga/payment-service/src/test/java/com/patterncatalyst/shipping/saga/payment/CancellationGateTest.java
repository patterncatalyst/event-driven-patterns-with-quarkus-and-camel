package com.patterncatalyst.shipping.saga.payment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import org.apache.camel.CamelContext;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.patterncatalyst.shipping.Topics;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * The gate route calls the lag check by bean name and opens the payment route when the cancellation consumer has
 * nothing left to read.
 */
@QuarkusTest
class CancellationGateTest {

    @Inject
    CamelContext camel;

    @Inject
    CancellationLag lag;

    @Inject
    EntityManager em;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Test
    void theLagCheckIsAContainerBeanWithItsConfigurationInjected() {
        var handle = Arc.container().instance("cancellationLag");
        assertTrue(handle.isAvailable());
        // An application-scoped bean is a client proxy; its fields live on the contextual instance.
        var check = (CancellationLag) ClientProxy.unwrap(handle.get());
        assertNotNull(check.brokers);
    }

    @Test
    void theGateRunsAndOpensThePaymentRoute() {
        Awaitility.await().atMost(Duration.ofSeconds(60)).untilAsserted(
                () -> assertTrue(camel.getRouteController().getRouteStatus("take-payment").isStarted()));
    }

    @Test
    void aCancellationNotYetReadKeepsTheGateShutUntilItIsRecorded() throws Exception {
        Fixtures.await(() -> !behind());
        camel.getRouteController().stopRoute("record-cancellation");
        try {
            String id = Fixtures.newId();
            Fixtures.send(bootstrapServers, Topics.ORDERS_CANCELLED, id, "{\"orderId\":\"%s\",\"reason\":\"TIMED_OUT\"}".formatted(id));
            assertTrue(lag.behind());
            assertFalse(Fixtures.cancelledRow(em, id));
            camel.getRouteController().startRoute("record-cancellation");
            Fixtures.await(() -> !behind());
            assertTrue(Fixtures.cancelledRow(em, id));
        } finally {
            camel.getRouteController().startRoute("record-cancellation");
        }
    }

    @Test
    void aLagCheckThatFailsOnceIsRetriedAndTheGateStillOpens() throws Exception {
        Fixtures.await(() -> !behind());
        var check = Mockito.spy((CancellationLag) ClientProxy.unwrap(Arc.container().instance("cancellationLag").get()));
        Mockito.doThrow(new TimeoutException("admin call timed out")).doCallRealMethod().when(check).behind();
        QuarkusMock.installMockForType(check, CancellationLag.class);

        camel.getRouteController().stopRoute("take-payment");
        camel.getRouteController().stopRoute("cancellation-gate");
        camel.getRouteController().startRoute("cancellation-gate");

        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(
                () -> assertTrue(camel.getRouteController().getRouteStatus("take-payment").isStarted()));
        Mockito.verify(check, Mockito.atLeast(2)).behind();
    }

    private boolean behind() {
        try {
            return lag.behind();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
