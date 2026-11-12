package com.patterncatalyst.shipping.aggregate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/** The Camel route as the command adapter: a conflict is redelivered and the payment is decided again. */
@QuarkusTest
@TestProfile(CamelCommandsProfile.class)
class CamelCommandsTest {

    @Inject
    OrderTransaction transaction;

    @Inject
    EntityManager em;

    @InjectSpy
    DemoPause pause;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrap;

    @Test
    void aConflictIsRedeliveredAndDecidedAgain() throws Exception {
        String id = "O-" + UUID.randomUUID();
        transaction.handle(Streams.place(id));
        doAnswer(call -> {
            QuarkusTransaction.requiringNew().run(() -> transaction.handle(Streams.cancel(id)));
            return null;
        }).doNothing().when(pause).afterDecide(eq(id), eq("MarkPaid"), anyInt());

        Streams.send(bootstrap, "edp.payments.processed", id, Streams.payment("P-" + id, id, "25.00"));

        verify(pause, timeout(60_000)).afterDecide(id, "MarkPaid", 1);
        verify(pause, timeout(60_000)).afterDecide(id, "Cancel", 1);
        Streams.await(() -> {
            try {
                return Streams.lag(bootstrap, "order-payments-camel", "edp.payments.processed") == 0;
            } catch (Exception e) {
                return false;
            }
        });
        assertEquals(List.of("1:OrderPlaced", "2:OrderCancelled"), Streams.stream(em, id));
    }
}
