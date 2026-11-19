package com.patterncatalyst.shipping.analysis;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** DedupIdempotencyTest's crash scenario: DemoCrash interrupts exactly this exception id, once, after its row
 * commits and before the record publishes. */
public class CrashAfterAnalysisProfile implements QuarkusTestProfile {

    static final String CRASH_EXCEPTION_ID = "E-CRASH-DEMO";

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("demo.crash-after-analysis", CRASH_EXCEPTION_ID);
    }
}
