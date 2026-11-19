package com.patterncatalyst.shipping.analysis;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** Keeps the Kafka-consuming route stopped, so a note placed on the delivery-exceptions topic is visible to {@link
 * RecordedTopic} (and so to the backfill endpoint) without the live route also analysing it the normal way first
 * and pre-empting the scenario under test. */
public class NoLiveRouteProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("analyst.enabled", "false");
    }
}
