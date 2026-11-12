package com.patterncatalyst.shipping.deadletter;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** What FAILURE_STRATEGY=fail sets, on a topic and group of its own so the other tests' records stay out of it. */
public class FailStrategyProfile implements QuarkusTestProfile {

    static final String TOPIC = "edp.orders.placed-fail-test";

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "mp.messaging.incoming.orders-placed.failure-strategy", "fail",
                "mp.messaging.incoming.orders-placed.topic", TOPIC,
                "mp.messaging.incoming.orders-placed.group.id", "inventory-fail");
    }
}
