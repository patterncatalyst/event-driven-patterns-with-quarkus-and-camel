package com.patterncatalyst.shipping.aggregate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/** The negative witness: with RETRY_MODE=reappend the history gets a cancellation after a shipment. */
@QuarkusTest
@TestProfile(ReappendProfile.class)
class ReappendTest {

    @Inject
    OrderTransaction transaction;

    @Inject
    OrderCommands commands;

    @Inject
    EntityManager em;

    @InjectSpy
    DemoPause pause;

    @Test
    void aBlindReappendProducesAnIllegalHistory() {
        String id = "O-" + UUID.randomUUID();
        transaction.handle(Streams.place(id));
        transaction.handle(Streams.pay(id));
        doAnswer(call -> {
            QuarkusTransaction.requiringNew().run(() -> transaction.handle(Streams.ship(id)));
            return null;
        }).doNothing().when(pause).afterDecide(eq(id), eq("Cancel"), anyInt());

        commands.handle(Streams.cancel(id));

        assertEquals(List.of("1:OrderPlaced", "2:OrderPaid", "3:OrderShipped", "4:OrderCancelled"),
                Streams.stream(em, id));
        assertEquals(List.of("OrderPlaced->edp.orders.placed", "OrderCancelled->edp.orders.cancelled"),
                Streams.outbox(em, id));
    }
}
