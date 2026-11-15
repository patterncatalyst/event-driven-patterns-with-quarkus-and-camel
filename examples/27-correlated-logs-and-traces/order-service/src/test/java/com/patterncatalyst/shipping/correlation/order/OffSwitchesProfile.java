package com.patterncatalyst.shipping.correlation.order;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** Both demo switches off. */
public class OffSwitchesProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("correlation.sweeper-link", "off", "correlation.order-id-in-logs", "off");
    }
}
