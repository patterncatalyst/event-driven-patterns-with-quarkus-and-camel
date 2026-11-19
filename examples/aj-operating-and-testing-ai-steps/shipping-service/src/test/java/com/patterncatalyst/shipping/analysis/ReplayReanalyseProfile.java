package com.patterncatalyst.shipping.analysis;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** Demo code switched on: the replay reads the original notes again and calls the model, instead of reading the
 * disposition snapshot. */
public class ReplayReanalyseProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("replay.mode", "reanalyse");
    }
}
