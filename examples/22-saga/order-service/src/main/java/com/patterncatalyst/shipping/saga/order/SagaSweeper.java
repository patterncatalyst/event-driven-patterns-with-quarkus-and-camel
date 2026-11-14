package com.patterncatalyst.shipping.saga.order;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Cancels the sagas that waited past their deadline. The deadline is in PostgreSQL, so a restart does not lose it. */
@ApplicationScoped
public class SagaSweeper {

    @Inject
    SagaService saga;

    @Scheduled(every = "1s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void sweep() {
        saga.expire();
    }
}
