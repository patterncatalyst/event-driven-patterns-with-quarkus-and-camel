package com.patterncatalyst.shipping.inbox;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** What QUARKUS_PROFILE=camel-repository sets, plus a demo failure for one order. */
public class CamelRepositoryProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "inventory.camel.enabled", "true",
                "mp.messaging.incoming.orders-placed.enabled", "false",
                "demo.fail-after-effect-for", "O-FAIL");
    }
}
