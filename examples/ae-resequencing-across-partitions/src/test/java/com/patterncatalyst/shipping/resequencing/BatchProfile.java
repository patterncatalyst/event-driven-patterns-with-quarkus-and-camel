package com.patterncatalyst.shipping.resequencing;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** The batch form with batches of six. */
public class BatchProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("resequencer.mode", "batch", "resequencer.batch-size", "6", "resequencer.batch-timeout", "5000");
    }
}
