package com.patterncatalyst.shipping.testing;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * A profile of its own makes Quarkus restart the application for the advised class. Advice rewrites the route of the
 * running application, so without a restart the next class would find the route consuming direct:in.
 */
public class AdviceProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("testing.advice", "true");
    }
}
