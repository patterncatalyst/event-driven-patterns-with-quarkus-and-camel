package com.patterncatalyst.shipping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DomainModelTest {

    static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    static final BigDecimal TEN = new BigDecimal("10.00");
    static final BigDecimal NEG = new BigDecimal("-0.01");

    static HandlingEvent event(String consignmentId) {
        return new HandlingEvent("e1", consignmentId, "LOAD", "NLRTM", T0);
    }

    static PortCall call() {
        return new PortCall("NLRTM", T0, T0.plusSeconds(3600));
    }

    @Nested
    class ConsignmentTest {
        @Test
        void constructsWithValidFields() {
            var c = new Consignment("c1", "NLRTM", "SGSIN", TEN, Carrier.FASTLINE);
            assertEquals("c1", c.id());
        }

        @Test
        void rejectsBlankId() {
            assertThrows(IllegalArgumentException.class, () -> new Consignment(" ", "NLRTM", "SGSIN", TEN, "FASTLINE"));
        }

        @Test
        void rejectsNullOrigin() {
            assertThrows(NullPointerException.class, () -> new Consignment("c1", null, "SGSIN", TEN, "FASTLINE"));
        }

        @Test
        void rejectsBlankDestination() {
            assertThrows(IllegalArgumentException.class, () -> new Consignment("c1", "NLRTM", "", TEN, "FASTLINE"));
        }

        @Test
        void rejectsZeroWeight() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Consignment("c1", "NLRTM", "SGSIN", BigDecimal.ZERO, "FASTLINE"));
        }

        @Test
        void rejectsNullWeight() {
            assertThrows(NullPointerException.class, () -> new Consignment("c1", "NLRTM", "SGSIN", null, "FASTLINE"));
        }

        @Test
        void rejectsBlankCarrier() {
            assertThrows(IllegalArgumentException.class, () -> new Consignment("c1", "NLRTM", "SGSIN", TEN, " "));
        }
    }

    @Nested
    class ConsignmentStatusTest {
        @Test
        void hasLifecycleInOrder() {
            assertEquals(List.of("BOOKED", "LOADED", "IN_TRANSIT", "CLEARED", "DELIVERED"),
                    java.util.Arrays.stream(ConsignmentStatus.values()).map(Enum::name).toList());
        }
    }

    @Nested
    class HandlingEventTest {
        @Test
        void constructsWithValidFields() {
            assertEquals("LOAD", event("c1").type());
        }

        @Test
        void rejectsBlankId() {
            assertThrows(IllegalArgumentException.class, () -> new HandlingEvent("", "c1", "LOAD", "NLRTM", T0));
        }

        @Test
        void rejectsBlankConsignmentId() {
            assertThrows(IllegalArgumentException.class, () -> new HandlingEvent("e1", " ", "LOAD", "NLRTM", T0));
        }

        @Test
        void rejectsBlankType() {
            assertThrows(IllegalArgumentException.class, () -> new HandlingEvent("e1", "c1", " ", "NLRTM", T0));
        }

        @Test
        void rejectsBlankLocation() {
            assertThrows(IllegalArgumentException.class, () -> new HandlingEvent("e1", "c1", "LOAD", "", T0));
        }

        @Test
        void rejectsNullTimestamp() {
            assertThrows(NullPointerException.class, () -> new HandlingEvent("e1", "c1", "LOAD", "NLRTM", null));
        }
    }

    @Nested
    class TrackingHistoryTest {
        @Test
        void constructsWithValidFields() {
            assertEquals(1, new TrackingHistory("c1", List.of(event("c1"))).events().size());
        }

        @Test
        void rejectsBlankConsignmentId() {
            assertThrows(IllegalArgumentException.class, () -> new TrackingHistory(" ", List.of()));
        }

        @Test
        void rejectsNullEvents() {
            assertThrows(NullPointerException.class, () -> new TrackingHistory("c1", null));
        }

        @Test
        void rejectsEventOfAnotherConsignment() {
            assertThrows(IllegalArgumentException.class, () -> new TrackingHistory("c1", List.of(event("c2"))));
        }

        @Test
        void copiesEventsDefensively() {
            var source = new ArrayList<>(List.of(event("c1")));
            var history = new TrackingHistory("c1", source);
            source.clear();
            assertEquals(1, history.events().size());
            assertThrows(UnsupportedOperationException.class, () -> history.events().clear());
        }
    }

    @Nested
    class TariffTest {
        @Test
        void constructsWithValidFields() {
            assertEquals(TEN, new Tariff("FASTLINE", "NLRTM", "SGSIN", TEN, "EUR").ratePerKg());
        }

        @Test
        void rejectsBlankCarrier() {
            assertThrows(IllegalArgumentException.class, () -> new Tariff(" ", "NLRTM", "SGSIN", TEN, "EUR"));
        }

        @Test
        void rejectsBlankOrigin() {
            assertThrows(IllegalArgumentException.class, () -> new Tariff("AIRCO", "", "SGSIN", TEN, "EUR"));
        }

        @Test
        void rejectsBlankDestination() {
            assertThrows(IllegalArgumentException.class, () -> new Tariff("AIRCO", "NLRTM", " ", TEN, "EUR"));
        }

        @Test
        void rejectsNegativeRate() {
            assertThrows(IllegalArgumentException.class, () -> new Tariff("AIRCO", "NLRTM", "SGSIN", NEG, "EUR"));
        }

        @Test
        void rejectsBlankCurrency() {
            assertThrows(IllegalArgumentException.class, () -> new Tariff("AIRCO", "NLRTM", "SGSIN", TEN, ""));
        }
    }

    @Nested
    class RateQuoteTest {
        @Test
        void constructsWithValidFields() {
            assertEquals(TEN, new RateQuote("q1", "c1", "AIRCO", TEN, "EUR").amount());
        }

        @Test
        void acceptsZeroAmount() {
            assertEquals(0, new RateQuote("q1", "c1", "AIRCO", BigDecimal.ZERO, "EUR").amount().signum());
        }

        @Test
        void rejectsBlankId() {
            assertThrows(IllegalArgumentException.class, () -> new RateQuote(" ", "c1", "AIRCO", TEN, "EUR"));
        }

        @Test
        void rejectsBlankConsignmentId() {
            assertThrows(IllegalArgumentException.class, () -> new RateQuote("q1", "", "AIRCO", TEN, "EUR"));
        }

        @Test
        void rejectsBlankCarrier() {
            assertThrows(IllegalArgumentException.class, () -> new RateQuote("q1", "c1", " ", TEN, "EUR"));
        }

        @Test
        void rejectsNegativeAmount() {
            assertThrows(IllegalArgumentException.class, () -> new RateQuote("q1", "c1", "AIRCO", NEG, "EUR"));
        }

        @Test
        void rejectsNullAmount() {
            assertThrows(NullPointerException.class, () -> new RateQuote("q1", "c1", "AIRCO", null, "EUR"));
        }

        @Test
        void rejectsBlankCurrency() {
            assertThrows(IllegalArgumentException.class, () -> new RateQuote("q1", "c1", "AIRCO", TEN, " "));
        }
    }

    @Nested
    class InvoiceTest {
        @Test
        void constructsWithValidFields() {
            assertEquals("i1", new Invoice("i1", "c1", TEN, "EUR").id());
        }

        @Test
        void rejectsBlankId() {
            assertThrows(IllegalArgumentException.class, () -> new Invoice("", "c1", TEN, "EUR"));
        }

        @Test
        void rejectsBlankConsignmentId() {
            assertThrows(IllegalArgumentException.class, () -> new Invoice("i1", " ", TEN, "EUR"));
        }

        @Test
        void rejectsNegativeAmount() {
            assertThrows(IllegalArgumentException.class, () -> new Invoice("i1", "c1", NEG, "EUR"));
        }

        @Test
        void rejectsBlankCurrency() {
            assertThrows(IllegalArgumentException.class, () -> new Invoice("i1", "c1", TEN, ""));
        }
    }

    @Nested
    class SettlementTest {
        @Test
        void constructsWithValidFields() {
            assertEquals("s1", new Settlement("s1", "i1", TEN, T0).id());
        }

        @Test
        void rejectsBlankId() {
            assertThrows(IllegalArgumentException.class, () -> new Settlement(" ", "i1", TEN, T0));
        }

        @Test
        void rejectsBlankInvoiceId() {
            assertThrows(IllegalArgumentException.class, () -> new Settlement("s1", "", TEN, T0));
        }

        @Test
        void rejectsNegativeAmount() {
            assertThrows(IllegalArgumentException.class, () -> new Settlement("s1", "i1", NEG, T0));
        }

        @Test
        void rejectsNullSettledAt() {
            assertThrows(NullPointerException.class, () -> new Settlement("s1", "i1", TEN, null));
        }
    }

    @Nested
    class CarrierTest {
        @Test
        void constructsWithKnownCodes() {
            assertEquals("FASTLINE", new Carrier(Carrier.FASTLINE, "Fastline").code());
            assertEquals("AIRCO", new Carrier(Carrier.AIRCO, "Airco").code());
        }

        @Test
        void rejectsBlankCode() {
            assertThrows(IllegalArgumentException.class, () -> new Carrier(" ", "Fastline"));
        }

        @Test
        void rejectsBlankName() {
            assertThrows(IllegalArgumentException.class, () -> new Carrier("FASTLINE", ""));
        }
    }

    @Nested
    class CustomsDeclarationTest {
        @Test
        void constructsWithValidFields() {
            assertEquals("SG", new CustomsDeclaration("d1", "c1", "SG", TEN, "EUR").destinationCountry());
        }

        @Test
        void rejectsBlankId() {
            assertThrows(IllegalArgumentException.class, () -> new CustomsDeclaration(" ", "c1", "SG", TEN, "EUR"));
        }

        @Test
        void rejectsBlankConsignmentId() {
            assertThrows(IllegalArgumentException.class, () -> new CustomsDeclaration("d1", "", "SG", TEN, "EUR"));
        }

        @Test
        void rejectsBlankDestinationCountry() {
            assertThrows(IllegalArgumentException.class, () -> new CustomsDeclaration("d1", "c1", " ", TEN, "EUR"));
        }

        @Test
        void rejectsNegativeDeclaredValue() {
            assertThrows(IllegalArgumentException.class, () -> new CustomsDeclaration("d1", "c1", "SG", NEG, "EUR"));
        }

        @Test
        void rejectsBlankCurrency() {
            assertThrows(IllegalArgumentException.class, () -> new CustomsDeclaration("d1", "c1", "SG", TEN, ""));
        }
    }

    @Nested
    class ManifestTest {
        @Test
        void constructsWithValidFields() {
            assertEquals(2, new Manifest("m1", "s1", List.of("c1", "c2")).consignmentIds().size());
        }

        @Test
        void rejectsBlankId() {
            assertThrows(IllegalArgumentException.class, () -> new Manifest(" ", "s1", List.of()));
        }

        @Test
        void rejectsBlankSailingId() {
            assertThrows(IllegalArgumentException.class, () -> new Manifest("m1", "", List.of()));
        }

        @Test
        void rejectsNullConsignmentIds() {
            assertThrows(NullPointerException.class, () -> new Manifest("m1", "s1", null));
        }

        @Test
        void rejectsBlankConsignmentIdElement() {
            assertThrows(IllegalArgumentException.class, () -> new Manifest("m1", "s1", List.of(" ")));
        }

        @Test
        void copiesIdsDefensively() {
            var source = new ArrayList<>(List.of("c1"));
            var manifest = new Manifest("m1", "s1", source);
            source.add("c2");
            assertEquals(1, manifest.consignmentIds().size());
        }
    }

    @Nested
    class VesselTest {
        @Test
        void constructsWithValidFields() {
            assertEquals("9811000", new Vessel("9811000", "Ever Given").imo());
        }

        @Test
        void rejectsBlankImo() {
            assertThrows(IllegalArgumentException.class, () -> new Vessel(" ", "Ever Given"));
        }

        @Test
        void rejectsBlankName() {
            assertThrows(IllegalArgumentException.class, () -> new Vessel("9811000", ""));
        }
    }

    @Nested
    class PortCallTest {
        @Test
        void constructsWithValidFields() {
            assertEquals("NLRTM", call().port());
        }

        @Test
        void acceptsDepartureEqualToArrival() {
            assertEquals(T0, new PortCall("NLRTM", T0, T0).departure());
        }

        @Test
        void rejectsBlankPort() {
            assertThrows(IllegalArgumentException.class, () -> new PortCall(" ", T0, T0));
        }

        @Test
        void rejectsNullArrival() {
            assertThrows(NullPointerException.class, () -> new PortCall("NLRTM", null, T0));
        }

        @Test
        void rejectsNullDeparture() {
            assertThrows(NullPointerException.class, () -> new PortCall("NLRTM", T0, null));
        }

        @Test
        void rejectsDepartureBeforeArrival() {
            assertThrows(IllegalArgumentException.class, () -> new PortCall("NLRTM", T0, T0.minusSeconds(1)));
        }
    }

    @Nested
    class SailingTest {
        @Test
        void constructsWithValidFields() {
            assertEquals(1, new Sailing("s1", "9811000", List.of(call())).portCalls().size());
        }

        @Test
        void rejectsBlankId() {
            assertThrows(IllegalArgumentException.class, () -> new Sailing(" ", "9811000", List.of(call())));
        }

        @Test
        void rejectsBlankVesselImo() {
            assertThrows(IllegalArgumentException.class, () -> new Sailing("s1", "", List.of(call())));
        }

        @Test
        void rejectsNullPortCalls() {
            assertThrows(NullPointerException.class, () -> new Sailing("s1", "9811000", null));
        }

        @Test
        void rejectsEmptyPortCalls() {
            assertThrows(IllegalArgumentException.class, () -> new Sailing("s1", "9811000", List.of()));
        }

        @Test
        void copiesPortCallsDefensively() {
            var source = new ArrayList<>(List.of(call()));
            var sailing = new Sailing("s1", "9811000", source);
            source.clear();
            assertEquals(1, sailing.portCalls().size());
        }
    }

    @Nested
    class SlotIdTest {
        @Test
        void constructsWithValidFields() {
            assertEquals(0, new SlotId("s1", 0).number());
        }

        @Test
        void rejectsBlankSailingId() {
            assertThrows(IllegalArgumentException.class, () -> new SlotId(" ", 1));
        }

        @Test
        void rejectsNegativeNumber() {
            assertThrows(IllegalArgumentException.class, () -> new SlotId("s1", -1));
        }
    }
}
