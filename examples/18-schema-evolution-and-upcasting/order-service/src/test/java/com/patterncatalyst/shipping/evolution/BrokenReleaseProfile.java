package com.patterncatalyst.shipping.evolution;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** Demo code switch ORDER_PLACED_SCHEMA=3: the service publishes a shippingPriority without a default. */
public class BrokenReleaseProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("order.placed-schema", "3");
    }
}
