package com.patterncatalyst.shipping.snapshots;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "inventory")
public interface SnapshotConfig {

    Snapshot snapshot();

    Snapshotter snapshotter();

    Demo demo();

    interface Snapshot {

        /** Write a snapshot in the command's transaction once this many events follow the last one; 0 disables. */
        @WithDefault("100")
        int every();

        /** Whether commands write snapshots. The Camel snapshotter profile turns this off. */
        @WithDefault("true")
        boolean inline();

        /** The schema version of InventorySnapshot: stamped on new snapshots, and a stored one with another value is ignored. */
        @WithDefault("1")
        int schema();

        /** Demo switch: false removes the version guard from the upsert. */
        @WithDefault("true")
        boolean guard();
    }

    interface Snapshotter {

        @WithDefault("false")
        boolean enabled();

        @WithName("delay-ms")
        @WithDefault("1000")
        long delayMs();
    }

    interface Demo {

        /** Demo switch: entities keeps every replayed event in the persistence context instead of a projection. */
        @WithDefault("projection")
        Fetch fetch();
    }

    enum Fetch {
        projection, entities
    }
}
