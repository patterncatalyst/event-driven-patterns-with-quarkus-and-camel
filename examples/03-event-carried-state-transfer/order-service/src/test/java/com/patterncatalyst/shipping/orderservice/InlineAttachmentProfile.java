package com.patterncatalyst.shipping.orderservice;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** The full-state comparison: the attachment travels inside the event. */
public class InlineAttachmentProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("order-events.attachment", "inline");
    }
}
