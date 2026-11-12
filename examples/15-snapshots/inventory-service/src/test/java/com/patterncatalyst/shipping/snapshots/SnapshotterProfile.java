package com.patterncatalyst.shipping.snapshots;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** What APP_PROFILE=camel-snapshotter sets: commands stop writing snapshots, the route starts. */
public class SnapshotterProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "inventory.snapshotter.enabled", "true",
                "inventory.snapshotter.delay-ms", "500",
                "inventory.snapshot.inline", "false");
    }
}
