package com.patterncatalyst.shipping.projections;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class GuardOffProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("projection.guard", "off");
    }
}
