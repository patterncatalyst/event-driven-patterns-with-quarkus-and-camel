package com.patterncatalyst.shipping.evolution;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** Demo code switch UPCASTERS=off. */
public class UpcastersOffProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("upcasters", "off");
    }
}
