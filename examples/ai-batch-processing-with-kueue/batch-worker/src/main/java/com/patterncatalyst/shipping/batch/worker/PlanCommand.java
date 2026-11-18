package com.patterncatalyst.shipping.batch.worker;

import java.util.Map;
import java.util.concurrent.Callable;

import org.jboss.logging.Logger;

import com.patterncatalyst.shipping.Topics;

import jakarta.inject.Inject;
import picocli.CommandLine.Command;

/** Records, per partition, the offsets of both topics at the window's start and end. */
@Command(name = "plan", description = "Record the offset ranges of the run's window")
public class PlanCommand implements Callable<Integer> {

    private static final Logger LOG = Logger.getLogger(PlanCommand.class);

    @Inject
    WorkerEnv env;
    @Inject
    KafkaReader kafka;
    @Inject
    PartitionStore store;
    @Inject
    StepSpan span;

    @Override
    public Integer call() {
        String runId = env.requireRunId();
        try {
            return span.run("batch.plan", Map.of("edp.batch.run", runId), () -> plan(runId));
        } finally {
            span.flush();
        }
    }

    int plan(String runId) {
        var window = store.window(runId);
        var scheduled = kafka.offsetsAt(Topics.SHIPPING_SCHEDULED, window.from(), window.to());
        var delivered = kafka.offsetsAt(Topics.SHIPPING_DELIVERED, window.from(), window.to());
        if (!scheduled.keySet().equals(delivered.keySet())) {
            throw new IllegalStateException("the two topics do not have the same partitions");
        }
        store.savePlan(runId, scheduled, delivered);
        scheduled.forEach((p, o) -> LOG.infof("run %s plan partition %d scheduled [%d, %d) delivered [%d, %d)", runId, p,
                o[0], o[1], delivered.get(p)[0], delivered.get(p)[1]));
        return 0;
    }
}
