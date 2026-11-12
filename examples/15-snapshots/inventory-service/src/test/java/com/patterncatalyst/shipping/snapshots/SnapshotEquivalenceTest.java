package com.patterncatalyst.shipping.snapshots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The aggregate alone, no database: a snapshot plus the events after it is the same state as the whole fold. */
class SnapshotEquivalenceTest {

    /** A stream that never goes below zero: received first, then a random mix. */
    private static List<StockEvent> randomStream(long seed, int length) {
        var random = new Random(seed);
        var events = new ArrayList<StockEvent>();
        events.add(new StockEvent.StockReceived(100));
        int onHand = 100;
        for (int i = 1; i < length; i++) {
            int quantity = 1 + random.nextInt(5);
            if (random.nextInt(4) == 0 || onHand < quantity) {
                events.add(new StockEvent.StockReceived(quantity * 3));
                onHand += quantity * 3;
            } else {
                events.add(new StockEvent.StockReserved("O-" + i, quantity));
                onHand -= quantity;
            }
        }
        return events;
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 7, 100 })
    void snapshotPlusTailEqualsFullReplayAtEverySplit(int every) {
        for (long seed = 1; seed <= 20; seed++) {
            var events = randomStream(seed, 250);
            var whole = InventoryItemAggregate.replay("SKU-1", events.stream());
            for (int k = every; k < events.size(); k += every) {
                var head = InventoryItemAggregate.replay("SKU-1", events.subList(0, k).stream());
                var rebuilt = InventoryItemAggregate.from(head.snapshot(), head.version(),
                        events.subList(k, events.size()).stream());
                assertEquals(whole.quantityOnHand(), rebuilt.quantityOnHand(), "seed " + seed + " split " + k);
                assertEquals(whole.version(), rebuilt.version(), "seed " + seed + " split " + k);
            }
        }
    }

    @Test
    void aSnapshotAtTheHeadHasNothingToReplay() {
        var events = randomStream(7, 50);
        var whole = InventoryItemAggregate.replay("SKU-1", events.stream());
        var rebuilt = InventoryItemAggregate.from(whole.snapshot(), whole.version(), Stream.empty());
        assertEquals(whole.quantityOnHand(), rebuilt.quantityOnHand());
        assertEquals(whole.version(), rebuilt.version());
    }

    @Test
    void reserveBeyondTheQuantityOnHandIsRefusedAndAppliesNothing() {
        var aggregate = InventoryItemAggregate.replay("SKU-1", List.<StockEvent>of(new StockEvent.StockReceived(5)).stream());
        assertThrows(OutOfStock.class, () -> aggregate.reserve("O-1", 6));
        assertEquals(5, aggregate.quantityOnHand());
        assertEquals(1, aggregate.version());
    }
}
