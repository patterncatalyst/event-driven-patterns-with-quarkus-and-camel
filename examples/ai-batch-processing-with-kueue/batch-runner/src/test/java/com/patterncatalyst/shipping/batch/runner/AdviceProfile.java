package com.patterncatalyst.shipping.batch.runner;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** A profile of its own makes Quarkus restart the application for the class, so the advised routes do not leak. */
public class AdviceProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of();
    }
}
