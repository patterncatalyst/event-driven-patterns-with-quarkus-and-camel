package com.patterncatalyst.shipping.snapshots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/** The inline snapshot shares the command's transaction, so a failed snapshot write takes the command down with it. */
@QuarkusTest
class InlineSnapshotFailureTest {

    @Inject
    ReservationCommands commands;

    @Inject
    StockLedger ledger;

    @Inject
    EntityManager em;

    @InjectMock
    SnapshotStore store;

    private long events(String sku) {
        return QuarkusTransaction.requiringNew().call(() -> StoredStockEvent.count("streamId", sku));
    }

    private SnapshotRow snapshot(String sku) {
        return QuarkusTransaction.requiringNew().call(() -> SnapshotRow.findById(sku));
    }

    @Test
    void aFailedInlineSnapshotWriteRollsBackTheReservationAndAppendsNoEvent() {
        String sku = Ledgers.newSku();
        int quantity = Ledgers.seed(em, sku, 99);
        doThrow(new IllegalStateException("snapshot write failed")).when(store).upsert(any(), anyString());

        // The 100th event crosses N and triggers the inline snapshot write, which fails.
        assertThrows(IllegalStateException.class, () -> commands.reserve(sku, "O-1", 1));

        assertEquals(99, events(sku), "the stream version is unchanged: no event row was appended");
        assertEquals(quantity, ledger.load(sku, false).aggregate().quantityOnHand(), "the quantity is unchanged");
        assertNull(snapshot(sku), "nothing was snapshotted");
    }
}
