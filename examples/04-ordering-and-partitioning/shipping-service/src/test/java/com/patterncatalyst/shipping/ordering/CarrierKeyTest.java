package com.patterncatalyst.shipping.ordering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(CarrierKeyTest.CarrierKey.class)
class CarrierKeyTest {

    public static class CarrierKey implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("shipping.partition-key", "carrier");
        }
    }

    @Test
    void ordersKeepTheirOrderButUseAtMostTwoOfThreePartitions() {
        Set<Integer> used = new HashSet<>();
        for (int n = 1; n <= 12; n++) {
            String carrier = n % 2 == 0 ? "AIRCO" : "FASTLINE";
            var receipts = Placement.publish("CAR-" + n, carrier);
            assertEquals(1, receipts.stream().map(Receipt::partition).distinct().count(),
                    "one order stays in one partition");
            assertEquals(Placement.expected(carrier), receipts.getFirst().partition());
            used.addAll(receipts.stream().map(Receipt::partition).toList());
        }
        assertTrue(used.size() <= 2, "two carriers can occupy at most two of three partitions: " + used);
    }
}
