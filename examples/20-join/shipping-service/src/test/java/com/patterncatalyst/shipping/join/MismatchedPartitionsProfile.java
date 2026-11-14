package com.patterncatalyst.shipping.join;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** Reservations on three partitions and payments on two: the co-partitioning rule broken. */
public class MismatchedPartitionsProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.kafka.devservices.topic-partitions.edp.inventory.reserved", "3",
                "quarkus.kafka.devservices.topic-partitions.edp.payments.processed", "2");
    }
}
