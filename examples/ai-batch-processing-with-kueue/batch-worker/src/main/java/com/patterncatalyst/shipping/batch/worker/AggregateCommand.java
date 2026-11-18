package com.patterncatalyst.shipping.batch.worker;

import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.camel.ProducerTemplate;
import org.jboss.logging.Logger;

import jakarta.inject.Inject;
import picocli.CommandLine.Command;

/** Sums the partitions into one total per carrier and publishes one record per carrier. */
@Command(name = "aggregate", description = "Total the run per carrier and publish the totals")
public class AggregateCommand implements Callable<Integer> {

    private static final Logger LOG = Logger.getLogger(AggregateCommand.class);

    @Inject
    WorkerEnv env;
    @Inject
    PartitionStore store;
    @Inject
    ProducerTemplate producer;
    @Inject
    StepSpan span;

    @Override
    public Integer call() {
        String runId = env.requireRunId();
        try {
            return span.run("batch.aggregate", Map.of("edp.batch.run", runId), () -> aggregate(runId));
        } finally {
            span.flush();
        }
    }

    int aggregate(String runId) {
        long open = store.notDone(runId);
        if (open != 0) {
            LOG.errorf("run %s is not ready to aggregate: %s", runId,
                    open < 0 ? "no partitions were planned" : open + " partitions are not DONE");
            return 1;
        }
        var totals = store.totals(runId, store.window(runId));
        producer.sendBody("direct:publish-reconciliations", totals);
        LOG.infof("run %s aggregated, %d records published", runId, totals.size());
        return 0;
    }
}
