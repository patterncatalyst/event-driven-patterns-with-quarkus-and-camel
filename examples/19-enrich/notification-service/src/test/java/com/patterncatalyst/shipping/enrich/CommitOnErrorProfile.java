package com.patterncatalyst.shipping.enrich;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** Demo switch: the route commits a record whose lookup failed. */
public class CommitOnErrorProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("enricher.break-on-first-error", "false");
    }
}
