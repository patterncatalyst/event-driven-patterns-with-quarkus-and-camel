package com.patterncatalyst.shipping.aggregate;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class CamelCommandsProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("commands.camel.enabled", "true", "mp.messaging.incoming.payments.enabled", "false");
    }
}
