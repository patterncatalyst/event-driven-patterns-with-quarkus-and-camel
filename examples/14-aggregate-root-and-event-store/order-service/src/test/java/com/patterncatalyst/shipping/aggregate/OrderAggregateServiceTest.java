package com.patterncatalyst.shipping.aggregate;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@QuarkusTest
class OrderAggregateServiceTest {

    @Inject
    EventStore store;

    @Inject
    OrderTransaction transaction;

    @Inject
    OrderCommands commands;

    @Inject
    EntityManager em;

    @InjectSpy
    DemoPause pause;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrap;

    private static String id() {
        return "O-" + UUID.randomUUID();
    }

    @AfterEach
    void reset() {
        FailingExport.FAIL.set(false);
    }

    /** Runs the action once, in its own transaction, while the command waits between deciding and appending. */
    private void duringPause(String orderId, String command, Runnable action) {
        doAnswer(call -> {
            QuarkusTransaction.requiringNew().run(action);
            return null;
        }).doNothing().when(pause).afterDecide(eq(orderId), eq(command), anyInt());
    }

    @Test
    void theStreamIsTheOnlyOrderState() {
        var tables = QuarkusTransaction.requiringNew().call(() -> em
                .createNativeQuery("select table_name from information_schema.tables where table_schema = 'orders' order by 1")
                .getResultList());
        assertEquals(List.of("events", "flyway_schema_history", "outboxevent"), tables);
    }

    @Test
    void anAppendAtAStaleVersionIsRejectedAndAddsNoRow() {
        String id = id();
        transaction.handle(Streams.place(id));
        assertThrows(ConcurrencyConflict.class, () -> QuarkusTransaction.requiringNew()
                .run(() -> store.append("Order", id, 0, List.of(new OrderEvent.OrderCancelled("x")))));
        assertEquals(List.of("1:OrderPlaced"), Streams.stream(em, id));
    }

    @Test
    void aLostRaceIsDecidedAgainAndRefused() {
        String id = id();
        transaction.handle(Streams.place(id));
        transaction.handle(Streams.pay(id));
        duringPause(id, "Cancel", () -> transaction.handle(Streams.ship(id)));

        var refused = assertThrows(CommandRefused.class, () -> commands.handle(Streams.cancel(id)));

        assertEquals("SHIPPED to CANCELLED", refused.getMessage());
        assertEquals(List.of("1:OrderPlaced", "2:OrderPaid", "3:OrderShipped"), Streams.stream(em, id));
        assertEquals(List.of("OrderPlaced->edp.orders.placed"), Streams.outbox(em, id));
    }

    @Test
    void aLostRaceIsDecidedAgainAndAcceptedAtTheNextVersion() {
        String id = id();
        transaction.handle(Streams.place(id));
        duringPause(id, "Cancel", () -> transaction.handle(Streams.pay(id)));

        var result = commands.handle(Streams.cancel(id));

        assertEquals(3, result.version());
        assertEquals(List.of("1:OrderPlaced", "2:OrderPaid", "3:OrderCancelled"), Streams.stream(em, id));
        verify(pause).afterDecide(id, "Cancel", 1);
        verify(pause).afterDecide(id, "Cancel", 2); // the retry reloaded the stream in a new transaction
    }

    @Test
    void onlyPlacedAndCancelledLeaveTheServiceThroughTheOutbox() {
        String id = id();
        transaction.handle(Streams.place(id));
        transaction.handle(Streams.pay(id));
        transaction.handle(Streams.cancel(id));
        assertEquals(List.of("OrderPlaced->edp.orders.placed", "OrderCancelled->edp.orders.cancelled"),
                Streams.outbox(em, id));
    }

    @Test
    void aFailureAfterTheAppendLeavesNeitherAnEventNorAnOutboxRow() {
        String id = id();
        FailingExport.FAIL.set(true);
        assertThrows(IllegalStateException.class, () -> transaction.handle(Streams.place(id)));
        assertEquals(List.of(), Streams.stream(em, id));
        assertEquals(List.of(), Streams.outbox(em, id));
    }

    @Test
    void aStaleIfMatchIsAnswered412AndRunsNoCommand() {
        String id = id();
        transaction.handle(Streams.place(id));
        transaction.handle(Streams.pay(id));
        given().header("If-Match", "\"1\"").post("/orders/{id}/cancellation", id).then().statusCode(412);
        assertEquals(List.of("1:OrderPlaced", "2:OrderPaid"), Streams.stream(em, id));
    }

    @Test
    void aMatchingIfMatchCancelsAndReturnsTheNewVersion() {
        String id = id();
        transaction.handle(Streams.place(id));
        transaction.handle(Streams.pay(id));
        given().header("If-Match", "\"2\"").post("/orders/{id}/cancellation", id).then().statusCode(200)
                .header("ETag", "\"3\"");
    }

    @Test
    void aRaceBehindAMatchingIfMatchIsReportedNotRetried() {
        String id = id();
        transaction.handle(Streams.place(id));
        transaction.handle(Streams.pay(id));
        duringPause(id, "Cancel", () -> transaction.handle(Streams.ship(id)));

        given().header("If-Match", "\"2\"").post("/orders/{id}/cancellation", id).then().statusCode(412);

        assertEquals(List.of("1:OrderPlaced", "2:OrderPaid", "3:OrderShipped"), Streams.stream(em, id));
        verify(pause).afterDecide(id, "Cancel", 2); // decided once, never decided again
    }

    @Test
    void aRefusedCancellationIsAnswered409WithTheReason() {
        String id = id();
        transaction.handle(Streams.place(id));
        transaction.handle(Streams.pay(id));
        transaction.handle(Streams.ship(id));
        given().post("/orders/{id}/cancellation", id).then().statusCode(409)
                .body("reason", org.hamcrest.Matchers.equalTo("SHIPPED to CANCELLED"));
    }

    @Test
    void placingAndReadingAnOrderOverRest() {
        String id = id();
        var body = Map.of("id", id, "customerId", "CU-1", "itemSku", "SKU-1", "quantity", 1, "amount", 25.00);
        given().contentType(ContentType.JSON).body(body).post("/orders").then().statusCode(201).header("ETag", "\"1\"");
        given().contentType(ContentType.JSON).body(body).post("/orders").then().statusCode(409);
        given().get("/orders/{id}", id).then().statusCode(200).header("ETag", "\"1\"")
                .body("status", org.hamcrest.Matchers.equalTo("PLACED"));
        given().get("/orders/{id}", "O-missing").then().statusCode(404);
    }

    @Test
    void kafkaCommandsAreDecidedByTheAggregate() throws Exception {
        String paid = id();
        String mismatch = id();
        String last = id();
        for (String id : List.of(paid, mismatch, last)) {
            transaction.handle(Streams.place(id));
        }
        String payments = "edp.payments.processed";
        Streams.send(bootstrap, payments, paid, Streams.payment("P-1", paid, "25.00"));
        Streams.send(bootstrap, payments, paid, Streams.payment("P-1", paid, "25.00")); // redelivered
        Streams.send(bootstrap, payments, paid, Streams.payment("P-OTHER", paid, "25.00")); // a second payment id
        Streams.send(bootstrap, payments, mismatch, Streams.payment("P-2", mismatch, "99.00")); // wrong amount
        Streams.send(bootstrap, payments, last, Streams.payment("P-3", last, "25.00"));
        Streams.await(() -> Streams.stream(em, last).size() == 2);

        assertEquals(List.of("1:OrderPlaced", "2:OrderPaid"), Streams.stream(em, paid));
        assertEquals(List.of("1:OrderPlaced"), Streams.stream(em, mismatch));

        Streams.send(bootstrap, "edp.shipping.scheduled", paid, Streams.shipment(paid));
        Streams.send(bootstrap, "edp.shipping.scheduled", mismatch, Streams.shipment(mismatch)); // refused: not paid
        Streams.send(bootstrap, "edp.shipping.scheduled", last, Streams.shipment(last));
        Streams.await(() -> Streams.stream(em, last).size() == 3);
        assertEquals(List.of("1:OrderPlaced", "2:OrderPaid", "3:OrderShipped"), Streams.stream(em, paid));
        assertEquals(List.of("1:OrderPlaced"), Streams.stream(em, mismatch));
        Streams.await(() -> {
            try {
                return Streams.lag(bootstrap, "order-payments", payments) == 0
                        && Streams.lag(bootstrap, "order-shipments", "edp.shipping.scheduled") == 0;
            } catch (Exception e) {
                return false;
            }
        });
    }
}
