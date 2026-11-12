package com.patterncatalyst.shipping.inbox;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** What INBOX_ENABLED=false sets: the consumer decrements without recording the event id. */
public class NoInboxProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("demo.inbox.enabled", "false", "mp.messaging.incoming.orders-placed.group.id", "inventory-no-inbox");
    }
}
