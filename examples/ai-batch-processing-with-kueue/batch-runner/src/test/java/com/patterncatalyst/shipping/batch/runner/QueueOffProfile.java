package com.patterncatalyst.shipping.batch.runner;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** The demo switches QUEUE=off and PRIORITY=off, as the environment sets them in a Deployment. */
public class QueueOffProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("batch.queue", "off", "batch.priority", "off");
    }
}
