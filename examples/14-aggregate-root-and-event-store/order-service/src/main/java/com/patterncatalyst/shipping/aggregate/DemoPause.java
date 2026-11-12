package com.patterncatalyst.shipping.aggregate;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Demo code, off unless DEMO_PAUSE_FOR is set. Holds a listed command ("O-1402:Cancel") once, between deciding and
 * appending, so another writer can move the stream while the command is in flight. Nothing in a real service
 * waits here.
 */
@ApplicationScoped
public class DemoPause {

    private static final Logger LOG = Logger.getLogger(DemoPause.class);

    @ConfigProperty(name = "demo.pause-for")
    Optional<List<String>> pauseFor;

    @ConfigProperty(name = "demo.pause-ms", defaultValue = "6000")
    long pauseMs;

    private final Set<String> done = ConcurrentHashMap.newKeySet();

    public void afterDecide(String orderId, String command, int version) {
        String key = orderId + ":" + command;
        if (pauseFor.orElse(List.of()).contains(key) && done.add(key)) {
            LOG.infof("paused %s %s at version %d", command, orderId, version);
            try {
                Thread.sleep(pauseMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
