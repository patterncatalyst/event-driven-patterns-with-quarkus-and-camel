package com.patterncatalyst.shipping.ordering;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.OrderStatus;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class ShipmentBufferTest {

    static final String SCHEDULED = "ShipmentScheduled";
    static final String IN_TRANSIT = "ShipmentInTransit";
    static final String DELIVERED = "ShipmentDelivered";

    @Inject
    ShipmentBuffer buffer;

    private void accept(String orderId, String type, int sequence) throws Exception {
        buffer.accept(Fixtures.json(orderId, type, sequence));
    }

    @Test
    void eventsArrivingThreeTwoOneAreAppliedOneTwoThree() throws Exception {
        accept("BUF-1", DELIVERED, 3);
        accept("BUF-1", IN_TRANSIT, 2);
        assertEquals(List.of(), Fixtures.appliedTypes("BUF-1"));
        assertEquals(2, Fixtures.pending("BUF-1"));
        accept("BUF-1", SCHEDULED, 1);
        assertEquals(List.of(SCHEDULED, IN_TRANSIT, DELIVERED), Fixtures.appliedTypes("BUF-1"));
        assertEquals(OrderStatus.DELIVERED, Fixtures.statusOf("BUF-1"));
        assertEquals(0, Fixtures.pending("BUF-1"));
    }

    @Test
    void aGapHoldsTheLaterEventUntilTheMissingOneArrives() throws Exception {
        accept("BUF-2", SCHEDULED, 1);
        accept("BUF-2", DELIVERED, 3);
        assertEquals(List.of(SCHEDULED), Fixtures.appliedTypes("BUF-2"));
        assertEquals(1, Fixtures.pending("BUF-2"));
        accept("BUF-2", IN_TRANSIT, 2);
        assertEquals(List.of(SCHEDULED, IN_TRANSIT, DELIVERED), Fixtures.appliedTypes("BUF-2"));
        assertEquals(0, Fixtures.pending("BUF-2"));
    }

    @Test
    void aGapInOneOrderDoesNotHoldAnotherOrder() throws Exception {
        accept("BUF-3", DELIVERED, 3);
        accept("BUF-4", SCHEDULED, 1);
        assertEquals(List.of(SCHEDULED), Fixtures.appliedTypes("BUF-4"));
        assertEquals(List.of(), Fixtures.appliedTypes("BUF-3"));
    }

    @Test
    void aDuplicateIsIgnored() throws Exception {
        accept("BUF-5", SCHEDULED, 1);
        accept("BUF-5", SCHEDULED, 1);
        accept("BUF-5", DELIVERED, 3);
        accept("BUF-5", DELIVERED, 3);
        assertEquals(List.of(SCHEDULED), Fixtures.appliedTypes("BUF-5"));
        assertEquals(1, Fixtures.pending("BUF-5"));
    }

    @RepeatedTest(20)
    void twoReplicasHandlingANewOrderAtOnceApplyOneThenTwo(RepetitionInfo info) throws Exception {
        String id = "BUF-RACE-" + info.getCurrentRepetition();
        var start = new CyclicBarrier(2);
        var second = CompletableFuture.runAsync(() -> concurrently(start, id, IN_TRANSIT, 2));
        var first = CompletableFuture.runAsync(() -> concurrently(start, id, SCHEDULED, 1));
        CompletableFuture.allOf(first, second).get();
        assertEquals(List.of(SCHEDULED, IN_TRANSIT), Fixtures.appliedTypes(id));
        assertEquals(0, Fixtures.pending(id));
    }

    @RepeatedTest(20)
    void twoReplicasHandlingAnExistingOrderAtOnceApplyOneTwoThree(RepetitionInfo info) throws Exception {
        String id = "BUF-RACE-EXISTING-" + info.getCurrentRepetition();
        accept(id, DELIVERED, 3);
        var start = new CyclicBarrier(2);
        var second = CompletableFuture.runAsync(() -> concurrently(start, id, IN_TRANSIT, 2));
        var first = CompletableFuture.runAsync(() -> concurrently(start, id, SCHEDULED, 1));
        CompletableFuture.allOf(first, second).get();
        assertEquals(List.of(SCHEDULED, IN_TRANSIT, DELIVERED), Fixtures.appliedTypes(id));
        assertEquals(0, Fixtures.pending(id));
    }

    private void concurrently(CyclicBarrier start, String orderId, String type, int sequence) {
        try {
            start.await();
            accept(orderId, type, sequence);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
