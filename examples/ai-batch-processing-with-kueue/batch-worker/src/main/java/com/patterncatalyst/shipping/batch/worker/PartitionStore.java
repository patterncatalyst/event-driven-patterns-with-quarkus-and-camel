package com.patterncatalyst.shipping.batch.worker;

import java.time.Instant;
import java.util.Map;

import org.jboss.logging.Logger;

import com.patterncatalyst.shipping.Carrier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/** Every write the worker makes, one transaction each. */
@ApplicationScoped
public class PartitionStore {

    private static final Logger LOG = Logger.getLogger(PartitionStore.class);

    public record Window(Instant from, Instant to) {
    }

    @Transactional
    public Window window(String runId) {
        Run run = Run.findById(runId);
        if (run == null) {
            throw new IllegalStateException("no run " + runId);
        }
        return new Window(run.windowFrom, run.windowTo);
    }

    /** The plan: one row per partition with the offsets it will read. A repeated plan writes the same rows again. */
    @Transactional
    public void savePlan(String runId, Map<Integer, long[]> scheduled, Map<Integer, long[]> delivered) {
        RunPartition.delete("runId", runId);
        for (var e : scheduled.entrySet()) {
            RunPartition row = new RunPartition();
            row.runId = runId;
            row.partition = e.getKey();
            row.scheduledFrom = e.getValue()[0];
            row.scheduledTo = e.getValue()[1];
            row.deliveredFrom = delivered.get(e.getKey())[0];
            row.deliveredTo = delivered.get(e.getKey())[1];
            row.status = RunPartition.PENDING;
            row.persist();
        }
    }

    /**
     * Starts an attempt. Returns null when the partition is DONE and the guard is on: the work is not repeated.
     * An attempt that goes on counts in attempts, so a retried index shows 2.
     */
    @Transactional
    public RunPartition begin(String runId, int partition, boolean guard) {
        RunPartition row = RunPartition.findById(new RunPartition.Key(runId, partition));
        if (row == null) {
            throw new IllegalStateException("run " + runId + " has no partition " + partition + ", plan has not run");
        }
        if (guard && RunPartition.DONE.equals(row.status)) {
            return null;
        }
        row.attempts++;
        return row;
    }

    /** The results and the DONE mark in one transaction: both exist or neither does. */
    @Transactional
    public void complete(String runId, int partition, Tally tally) {
        for (var e : tally.counts().entrySet()) {
            PartitionResult.Key key = new PartitionResult.Key(runId, partition, e.getKey().name());
            PartitionResult row = PartitionResult.findById(key);
            if (row == null) {
                row = new PartitionResult();
                row.runId = runId;
                row.partition = partition;
                row.carrier = e.getKey().name();
            }
            row.scheduled = e.getValue().scheduled();
            row.delivered = e.getValue().delivered();
            row.open = e.getValue().open();
            row.persist();
        }
        RunPartition partitionRow = RunPartition.findById(new RunPartition.Key(runId, partition));
        partitionRow.status = RunPartition.DONE;
        partitionRow.recordsRead = tally.records;
        partitionRow.completedAt = Instant.now();
    }

    /** How many partitions are not DONE, or -1 when the plan wrote none. */
    @Transactional
    public long notDone(String runId) {
        if (RunPartition.count("runId", runId) == 0) {
            return -1;
        }
        return RunPartition.count("runId = ?1 and status <> ?2", runId, RunPartition.DONE);
    }

    /** Sums the partitions into one row per carrier and returns them; a repeated aggregate leaves the same rows. */
    @Transactional
    public java.util.List<CarrierReconciliation> totals(String runId, Window window) {
        var results = PartitionResult.<PartitionResult> list("runId", runId);
        var out = new java.util.ArrayList<CarrierReconciliation>();
        for (Carrier carrier : Carrier.values()) {
            long s = 0;
            long d = 0;
            long o = 0;
            for (PartitionResult r : results) {
                if (r.carrier.equals(carrier.name())) {
                    s += r.scheduled;
                    d += r.delivered;
                    o += r.open;
                }
            }
            CarrierTotal total = CarrierTotal.findById(new CarrierTotal.Key(runId, carrier.name()));
            if (total == null) {
                total = new CarrierTotal();
                total.runId = runId;
                total.carrier = carrier.name();
            }
            total.scheduled = s;
            total.delivered = d;
            total.open = o;
            total.persist();
            out.add(new CarrierReconciliation(runId, carrier.name(), s, d, o, window.from(), window.to()));
        }
        LOG.infof("run %s totals %s", runId, out);
        return out;
    }
}
