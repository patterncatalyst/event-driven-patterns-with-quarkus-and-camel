package com.patterncatalyst.shipping.pickups;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** A new state directory for every test run, so a store left by an earlier run never meets a new broker. */
public class StreamsStateDir implements QuarkusTestResourceLifecycleManager {

    private Path dir;

    @Override
    public Map<String, String> start() {
        try {
            dir = Files.createTempDirectory("pickup-state");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Map.of("kafka-streams.state.dir", dir.toString());
    }

    @Override
    public void stop() {
        if (dir != null) {
            try (var files = Files.walk(dir)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            } catch (IOException e) {
                // the directory is in the temp area; leaving it is harmless
            }
        }
    }
}
