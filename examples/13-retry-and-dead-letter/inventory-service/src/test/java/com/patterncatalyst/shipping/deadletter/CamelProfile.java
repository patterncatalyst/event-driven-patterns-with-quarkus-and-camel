package com.patterncatalyst.shipping.deadletter;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** What CONSUMER_PROFILE=camel sets: the SmallRye channel off, the Camel route on. */
public class CamelProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "dead-letter.camel.enabled", "true",
                "mp.messaging.incoming.orders-placed.enabled", "false");
    }
}
