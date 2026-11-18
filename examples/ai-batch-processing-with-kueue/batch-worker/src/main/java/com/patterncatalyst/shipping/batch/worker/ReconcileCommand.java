package com.patterncatalyst.shipping.batch.worker;

import java.util.Map;
import java.util.concurrent.Callable;

import org.jboss.logging.Logger;

import com.patterncatalyst.shipping.Carrier;
import com.patterncatalyst.shipping.Topics;

import jakarta.inject.Inject;
import picocli.CommandLine.Command;

/** One index of the Indexed Job: partition i of both topics. */
@Command(name = "reconcile", description = "Count one partition of both topics")
public class ReconcileCommand implements Callable<Integer> {

    private static final Logger LOG = Logger.getLogger(ReconcileCommand.class);
    static final int OK = 0;
    static final int TRANSIENT = 1;
    static final int POISON = 42;

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
        int partition = env.requirePartition();
        try {
            return span.run("batch.reconcile", Map.of("edp.batch.run", runId, "edp.batch.partition", String.valueOf(partition)),
                    () -> reconcile(runId, partition));
        } finally {
            span.flush();
        }
    }

    int reconcile(String runId, int partition) {
        boolean guard = !"off".equalsIgnoreCase(env.replayGuard().orElse(null));
        RunPartition row = store.begin(runId, partition, guard);
        if (row == null) {
            LOG.infof("run %s partition %d skipped: already DONE", runId, partition);
            return OK;
        }
        int fault = fault(runId, partition);
        if (fault != OK) {
            return fault;
        }
        slow(partition);

        Tally tally = new Tally();
        tally.records += kafka.read(Topics.SHIPPING_SCHEDULED, partition, row.scheduledFrom, row.scheduledTo, r -> {
            Carrier carrier = kafka.carrier(r);
            tally.scheduled(kafka.orderId(r), carrier);
        });
        tally.records += kafka.read(Topics.SHIPPING_DELIVERED, partition, row.deliveredFrom, row.deliveredTo, r ->
                tally.delivered(kafka.orderId(r), kafka.carrier(r)));
        store.complete(runId, partition, tally);
        LOG.infof("run %s partition %d DONE, %d records read, attempt %d", runId, partition, tally.records, row.attempts);
        return OK;
    }

    /** The demo faults: once fails the first pod of the index, always fails it for good. */
    private int fault(String runId, int partition) {
        if (env.faultPartition().filter(target -> target == partition).isEmpty()) {
            return OK;
        }
        String mode = env.faultMode().orElse("");
        if ("always".equals(mode)) {
            LOG.errorf("run %s partition %d fails for good, exit %d", runId, partition, POISON);
            return POISON;
        }
        if ("once".equals(mode) && env.indexFailureCount().filter(count -> count == 0).isPresent()) {
            LOG.warnf("run %s partition %d fails once, exit %d", runId, partition, TRANSIENT);
            return TRANSIENT;
        }
        return OK;
    }

    private void slow(int partition) {
        if (env.slowPartition().filter(target -> target == partition).isPresent() && env.slowSeconds().isPresent()) {
            long seconds = env.slowSeconds().get();
            LOG.infof("partition %d slowed by %d s", partition, seconds);
            try {
                Thread.sleep(seconds * 1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
