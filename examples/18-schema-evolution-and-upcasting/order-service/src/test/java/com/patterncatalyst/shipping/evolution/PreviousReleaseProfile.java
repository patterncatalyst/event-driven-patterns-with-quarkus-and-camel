package com.patterncatalyst.shipping.evolution;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** Demo code switch ORDER_PLACED_SCHEMA=1: the service behaves as the previous release. */
public class PreviousReleaseProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("order.placed-schema", "1");
    }
}
