package com.patterncatalyst.shipping.aggregate;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class ReappendProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("retry.mode", "reappend");
    }
}
