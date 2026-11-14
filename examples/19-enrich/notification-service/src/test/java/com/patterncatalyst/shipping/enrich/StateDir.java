package com.patterncatalyst.shipping.enrich;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/** Gives every test run its own Kafka Streams state directory, so a store never meets an earlier run's broker. */
public class StateDir implements QuarkusTestResourceLifecycleManager {

    private Path dir;

    @Override
    public Map<String, String> start() {
        try {
            dir = Files.createTempDirectory("enrich-streams");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return Map.of("kafka-streams.state.dir", dir.toString());
    }

    @Override
    public void stop() {
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // a leftover temporary directory is harmless
        }
    }
}
