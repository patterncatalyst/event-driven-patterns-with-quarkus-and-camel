package com.patterncatalyst.shipping.replay;

import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Keeps {@code carrier_daily_shipments} current. Each tick applies batches until the events below the watermark
 * are used up, then publishes how many committed events are still waiting.
 */
@ApplicationScoped
public class CarrierDailyProjector {

    @Inject
    ProjectionBatch batch;

    @Inject
    MeterRegistry meters;

    @ConfigProperty(name = "projection.batch")
    int batchSize;

    private final AtomicLong lag = new AtomicLong();

    @PostConstruct
    void registerGauge() {
        meters.gauge("projection.lag.events", lag);
    }

    @Scheduled(every = "${projection.poll}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void tick() {
        int read;
        do {
            read = batch.run(batchSize);
        } while (read == batchSize);
        lag.set(batch.lagEvents());
    }
}
