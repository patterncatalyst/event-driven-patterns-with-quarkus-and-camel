package com.patterncatalyst.shipping.evolution.notification;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class CamelReaderProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("reader.camel.enabled", "true", "mp.messaging.incoming.placed.enabled", "false");
    }
}
