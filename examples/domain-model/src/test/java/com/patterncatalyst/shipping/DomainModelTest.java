package com.patterncatalyst.shipping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DomainModelTest {

    static final BigDecimal TEN = new BigDecimal("10.00");

    static Order order(int quantity, BigDecimal amount) {
        return new Order("O-1001", "C-7", "SKU-1", quantity, amount);
    }

    @Nested
    class OrderTest {
        @Test
        void constructsWithValidFields() {
            assertEquals("O-1001", order(2, TEN).id());
        }

        @Test
        void roundsAmountToTwoDecimalsHalfUp() {
            assertEquals(new BigDecimal("20.00"), order(1, new BigDecimal("19.999")).amount());
            assertEquals(new BigDecimal("10.01"), order(1, new BigDecimal("10.005")).amount());
            assertEquals(new BigDecimal("5.00"), order(1, new BigDecimal("5")).amount());
        }

        @Test
        void rejectsBlankId() {
            assertThrows(IllegalArgumentException.class, () -> new Order(" ", "C-7", "SKU-1", 1, TEN));
        }

        @Test
        void rejectsBlankCustomer() {
            assertThrows(IllegalArgumentException.class, () -> new Order("O-1", "", "SKU-1", 1, TEN));
        }

        @Test
        void rejectsBlankSku() {
            assertThrows(IllegalArgumentException.class, () -> new Order("O-1", "C-7", " ", 1, TEN));
        }

        @Test
        void rejectsNullText() {
            assertThrows(NullPointerException.class, () -> new Order(null, "C-7", "SKU-1", 1, TEN));
        }

        @Test
        void rejectsZeroQuantity() {
            assertThrows(IllegalArgumentException.class, () -> order(0, TEN));
        }

        @Test
        void rejectsNegativeQuantity() {
            assertThrows(IllegalArgumentException.class, () -> order(-1, TEN));
        }

        @Test
        void rejectsZeroAmount() {
            assertThrows(IllegalArgumentException.class, () -> order(1, BigDecimal.ZERO));
        }

        @Test
        void rejectsNegativeAmount() {
            assertThrows(IllegalArgumentException.class, () -> order(1, new BigDecimal("-0.01")));
        }

        @Test
        void rejectsNullAmount() {
            assertThrows(NullPointerException.class, () -> order(1, null));
        }
    }

    @Nested
    class OrderStatusTest {
        @ParameterizedTest
        @CsvSource({
            "PLACED,PAID,true", "PLACED,CANCELLED,true", "PLACED,PLACED,false",
            "PLACED,SHIPPED,false", "PLACED,DELIVERED,false",
            "PAID,SHIPPED,true", "PAID,CANCELLED,true", "PAID,PLACED,false",
            "PAID,PAID,false", "PAID,DELIVERED,false",
            "SHIPPED,DELIVERED,true", "SHIPPED,CANCELLED,false", "SHIPPED,PLACED,false",
            "SHIPPED,PAID,false", "SHIPPED,SHIPPED,false",
            "DELIVERED,PLACED,false", "DELIVERED,PAID,false", "DELIVERED,SHIPPED,false",
            "DELIVERED,DELIVERED,false", "DELIVERED,CANCELLED,false",
            "CANCELLED,PLACED,false", "CANCELLED,PAID,false", "CANCELLED,SHIPPED,false",
            "CANCELLED,DELIVERED,false", "CANCELLED,CANCELLED,false"
        })
        void transitionTable(OrderStatus from, OrderStatus to, boolean legal) {
            assertEquals(legal, from.canMoveTo(to));
        }

        @Test
        void tableCoversEveryPair() {
            assertEquals(25, OrderStatus.values().length * OrderStatus.values().length);
        }
    }

    @Nested
    class InventoryItemTest {
        @Test
        void acceptsZeroStock() {
            assertEquals(0, new InventoryItem("SKU-1", 0).quantityOnHand());
        }

        @Test
        void rejectsBlankSku() {
            assertThrows(IllegalArgumentException.class, () -> new InventoryItem(" ", 1));
        }

        @Test
        void rejectsNegativeQuantity() {
            assertThrows(IllegalArgumentException.class, () -> new InventoryItem("SKU-1", -1));
        }
    }

    @Nested
    class PaymentTest {
        @Test
        void constructsAndRounds() {
            var p = new Payment("P-1", "O-1001", new BigDecimal("19.999"), PaymentStatus.PENDING);
            assertEquals(new BigDecimal("20.00"), p.amount());
        }

        @Test
        void rejectsBlankId() {
            assertThrows(IllegalArgumentException.class, () -> new Payment("", "O-1", TEN, PaymentStatus.PENDING));
        }

        @Test
        void rejectsBlankOrderId() {
            assertThrows(IllegalArgumentException.class, () -> new Payment("P-1", " ", TEN, PaymentStatus.PENDING));
        }

        @Test
        void rejectsZeroAmount() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Payment("P-1", "O-1", BigDecimal.ZERO, PaymentStatus.PENDING));
        }

        @Test
        void rejectsNullStatus() {
            assertThrows(NullPointerException.class, () -> new Payment("P-1", "O-1", TEN, null));
        }
    }

    @Nested
    class ShipmentTest {
        @Test
        void pendingMayLackTrackingNumber() {
            var s = new Shipment("S-1", "O-1", Carrier.FASTLINE, null, ShipmentStatus.PENDING);
            assertNull(s.trackingNumber());
        }

        @Test
        void scheduledWithTrackingNumber() {
            var s = new Shipment("S-1", "O-1", Carrier.AIRCO, "TRK-1", ShipmentStatus.SCHEDULED);
            assertEquals("TRK-1", s.trackingNumber());
        }

        @Test
        void rejectsMissingTrackingNumberAfterPending() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Shipment("S-1", "O-1", Carrier.AIRCO, null, ShipmentStatus.SCHEDULED));
        }

        @Test
        void rejectsBlankTrackingNumber() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Shipment("S-1", "O-1", Carrier.AIRCO, " ", ShipmentStatus.PENDING));
        }

        @Test
        void rejectsBlankId() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Shipment("", "O-1", Carrier.AIRCO, "T", ShipmentStatus.SCHEDULED));
        }

        @Test
        void rejectsBlankOrderId() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Shipment("S-1", " ", Carrier.AIRCO, "T", ShipmentStatus.SCHEDULED));
        }

        @Test
        void rejectsNullCarrier() {
            assertThrows(NullPointerException.class,
                    () -> new Shipment("S-1", "O-1", null, "T", ShipmentStatus.SCHEDULED));
        }

        @Test
        void rejectsNullStatus() {
            assertThrows(NullPointerException.class, () -> new Shipment("S-1", "O-1", Carrier.AIRCO, "T", null));
        }
    }

    @Nested
    class ShipmentStatusTest {
        @ParameterizedTest
        @CsvSource({
            "PENDING,SCHEDULED,true", "SCHEDULED,IN_TRANSIT,true", "IN_TRANSIT,DELIVERED,true",
            "PENDING,IN_TRANSIT,false", "PENDING,DELIVERED,false", "PENDING,PENDING,false",
            "SCHEDULED,PENDING,false", "SCHEDULED,DELIVERED,false", "SCHEDULED,SCHEDULED,false",
            "IN_TRANSIT,PENDING,false", "IN_TRANSIT,SCHEDULED,false", "IN_TRANSIT,IN_TRANSIT,false",
            "DELIVERED,PENDING,false", "DELIVERED,SCHEDULED,false", "DELIVERED,IN_TRANSIT,false",
            "DELIVERED,DELIVERED,false"
        })
        void transitionTable(ShipmentStatus from, ShipmentStatus to, boolean legal) {
            assertEquals(legal, from.canMoveTo(to));
        }
    }

    @Nested
    class CarrierTest {
        @Test
        void unknownCarrierFails() {
            assertThrows(IllegalArgumentException.class, () -> Carrier.valueOf("FEDEX"));
        }

        @Test
        void knownCarriers() {
            assertEquals(Carrier.AIRCO, Carrier.valueOf("AIRCO"));
            assertEquals(2, Carrier.values().length);
        }
    }

    @Nested
    class NotificationTest {
        @Test
        void constructs() {
            assertEquals("OrderPlaced", new Notification("N-1", "O-1", "C-7", "OrderPlaced").eventType());
        }

        @Test
        void rejectsBlankId() {
            assertThrows(IllegalArgumentException.class, () -> new Notification(" ", "O-1", "C-7", "X"));
        }

        @Test
        void rejectsBlankOrderId() {
            assertThrows(IllegalArgumentException.class, () -> new Notification("N-1", "", "C-7", "X"));
        }

        @Test
        void rejectsBlankCustomerId() {
            assertThrows(IllegalArgumentException.class, () -> new Notification("N-1", "O-1", " ", "X"));
        }

        @Test
        void rejectsBlankEventType() {
            assertThrows(IllegalArgumentException.class, () -> new Notification("N-1", "O-1", "C-7", ""));
        }
    }

    @Nested
    class TopicsTest {
        @Test
        void catalogHasNineDistinctPrefixedTopics() {
            assertEquals(9, Topics.ALL.size());
            assertEquals(9, new HashSet<>(Topics.ALL).size());
            for (String t : Topics.ALL) {
                assertTrue(t.startsWith("edp."), t);
                assertTrue(!t.contains("_"), t);
            }
        }

        @Test
        void shipmentStreamIsOutsideTheCatalog() {
            assertEquals("edp.shipping.shipments", Topics.SHIPPING_SHIPMENTS);
            assertTrue(!Topics.ALL.contains(Topics.SHIPPING_SHIPMENTS));
        }

        @Test
        void deadLetterTopicIsOutsideTheCatalog() {
            assertEquals("edp.orders.placed-dlq", Topics.ORDERS_PLACED_DLQ);
            assertTrue(Topics.ORDERS_PLACED_DLQ.matches("edp\\.[a-z]+\\.[a-z-]+"));
            assertTrue(!Topics.ALL.contains(Topics.ORDERS_PLACED_DLQ));
        }

        @Test
        void inventoryCdcTopicsAreOutsideTheCatalog() {
            assertEquals("edp.inventory.item-changes", Topics.INVENTORY_ITEM_CHANGES);
            assertEquals("edp.inventory.items", Topics.INVENTORY_ITEMS);
            for (String topic : List.of(Topics.INVENTORY_ITEM_CHANGES, Topics.INVENTORY_ITEMS)) {
                assertTrue(topic.matches("edp\\.[a-z]+\\.[a-z-]+"), topic);
                assertTrue(!Topics.ALL.contains(topic));
            }
            assertTrue(!Topics.INVENTORY_ITEM_CHANGES.equals(Topics.INVENTORY_ITEMS));
        }

        @Test
        void catalogIsUnmodifiable() {
            assertThrows(UnsupportedOperationException.class, () -> Topics.ALL.add("edp.x"));
        }
    }
}
