package com.patterncatalyst.shipping.queues;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** What DELIVERY_MODE=group sets: the SmallRye channel off, the Camel consumer-group route on. */
public class GroupModeProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "delivery.group.enabled", "true",
                "mp.messaging.incoming.delivery-jobs.enabled", "false");
    }
}
