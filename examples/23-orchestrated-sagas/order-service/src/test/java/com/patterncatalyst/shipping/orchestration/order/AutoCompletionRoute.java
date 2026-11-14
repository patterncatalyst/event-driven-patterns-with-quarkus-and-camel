package com.patterncatalyst.shipping.orchestration.order;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.SagaCompletionMode;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * The saga with the default completion mode, AUTO: it completes when its own route ends, before any participant has
 * answered. Kept to show why the orchestrator sets MANUAL.
 */
@ApplicationScoped
public class AutoCompletionRoute extends RouteBuilder {

    static final AtomicInteger COMPLETED = new AtomicInteger();
    static final AtomicInteger COMPENSATED = new AtomicInteger();

    @Override
    public void configure() {
        from("direct:auto-saga")
                .saga().completionMode(SagaCompletionMode.AUTO)
                    .completion("direct:auto-completed").compensation("direct:auto-compensated")
                .log("command sent, no reply yet");
        from("direct:auto-completed").process(e -> COMPLETED.incrementAndGet());
        from("direct:auto-compensated").process(e -> COMPENSATED.incrementAndGet());
    }
}
