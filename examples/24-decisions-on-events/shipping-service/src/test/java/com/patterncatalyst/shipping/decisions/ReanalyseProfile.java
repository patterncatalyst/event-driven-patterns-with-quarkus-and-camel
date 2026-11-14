package com.patterncatalyst.shipping.decisions;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** Demo code switched on: the replay asks the model again. */
public class ReanalyseProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("replay.mode", "reanalyse");
    }
}
