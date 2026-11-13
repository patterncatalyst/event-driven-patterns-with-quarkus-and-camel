package com.patterncatalyst.shipping.join;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** Demo code: the aggregator keeps its groups in the JVM. */
public class MemoryRepositoryProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("readiness.repository", "memory");
    }
}
