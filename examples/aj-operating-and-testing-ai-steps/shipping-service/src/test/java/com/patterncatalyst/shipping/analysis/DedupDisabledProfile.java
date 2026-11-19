package com.patterncatalyst.shipping.analysis;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** What ANALYSIS_DEDUP=off removes: the keyed lookup, so a duplicate delivery pays for the model twice. */
public class DedupDisabledProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("analysis.dedup", "false");
    }
}
