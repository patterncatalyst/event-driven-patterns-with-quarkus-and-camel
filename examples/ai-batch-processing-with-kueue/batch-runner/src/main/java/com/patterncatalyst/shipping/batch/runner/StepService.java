package com.patterncatalyst.shipping.batch.runner;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import jakarta.transaction.Transactional;

/** The step state machine: every transition is one short transaction. */
@Named("stepService")
@ApplicationScoped
public class StepService {

    private static final Logger LOG = Logger.getLogger(StepService.class);

    private final BatchMetrics metrics;
    private final RunTracer tracer;

    StepService(BatchMetrics metrics, RunTracer tracer) {
        this.metrics = metrics;
        this.tracer = tracer;
    }

    /** The one step of each running run that has work: the first step that is not DONE. */
    @Transactional
    public List<StepRef> workQueue() {
        List<StepRef> out = new ArrayList<>();
        for (Run run : Run.<Run> list("status", Run.RUNNING)) {
            Step next = Step.<Step> find("runId = ?1 and status <> ?2 order by ordinal", run.id, Step.DONE).firstResult();
            if (next != null && !Step.FAILED.equals(next.status)) {
                out.add(ref(run, next));
            }
        }
        return out;
    }

    /** The Job is about to be created, or was created and the runner stopped before it noted that. */
    @Transactional
    public StepRef markQueued(StepRef ref) {
        Run run = Run.findById(ref.runId());
        Step step = step(ref);
        if (Step.PENDING.equals(step.status)) {
            step.status = Step.QUEUED;
            step.createdAt = Instant.now();
        }
        step.jobName = ref.jobName();
        if (StepJob.findById(key(ref)) == null) {
            StepJob job = new StepJob();
            job.runId = ref.runId();
            job.name = ref.name();
            job.attempt = ref.attempt();
            job.jobName = ref.jobName();
            job.persist();
        }
        return ref(run, step);
    }

    @Transactional
    public void admitted(StepRef ref) {
        Step step = step(ref);
        if (step.admittedAt == null) {
            step.admittedAt = Instant.now();
            metrics.queueWait(step.name, Duration.between(step.createdAt, step.admittedAt));
        }
        step.status = Step.RUNNING;
        LOG.infof("step %s of run %s admitted (%s)", step.name, step.runId, ref.jobName());
    }

    /** A started Job that is suspended again was preempted. The step waits again; it did not fail. */
    @Transactional
    public void preempted(StepRef ref) {
        Step step = step(ref);
        if (Step.RUNNING.equals(step.status)) {
            step.status = Step.QUEUED;
            metrics.preemption(step.name);
            LOG.warnf("step %s of run %s preempted, queued again (%s)", step.name, step.runId, ref.jobName());
        }
    }

    @Transactional
    public void completed(StepRef ref) {
        Step step = step(ref);
        step.status = Step.DONE;
        step.finishedAt = Instant.now();
        Instant started = step.admittedAt != null ? step.admittedAt : step.createdAt;
        metrics.duration(step.name, Duration.between(started, step.finishedAt));
        if (StepRef.RECONCILE.equals(step.name)) {
            long read = RunPartition.<RunPartition> list("runId = ?1 and completedAt >= ?2", step.runId, step.createdAt)
                    .stream().mapToLong(p -> p.recordsRead).sum();
            metrics.partitionRecords(read);
        }
        LOG.infof("step %s of run %s DONE (%s)", step.name, step.runId, ref.jobName());
        if (StepRef.AGGREGATE.equals(step.name)) {
            Run run = Run.findById(step.runId);
            run.status = Run.DONE;
            run.finishedAt = step.finishedAt;
            tracer.end(run.id, false);
            LOG.infof("run %s DONE", run.id);
        }
    }

    /** The Job failed, or an admitted Job vanished. The run stops here until someone replays it. */
    @Transactional
    public void failed(StepRef ref, String reason) {
        Step step = step(ref);
        step.status = Step.FAILED;
        step.finishedAt = Instant.now();
        Run run = Run.findById(step.runId);
        run.status = Run.FAILED;
        run.finishedAt = step.finishedAt;
        metrics.failure(step.name);
        tracer.end(run.id, true);
        LOG.errorf("step %s of run %s FAILED attempt %d: %s", step.name, step.runId, step.attempt, reason);
    }

    private static Step step(StepRef ref) {
        return Step.findById(new Step.Key(ref.runId(), ref.name()));
    }

    private static StepJob.Key key(StepRef ref) {
        StepJob.Key key = new StepJob.Key();
        key.runId = ref.runId();
        key.name = ref.name();
        key.attempt = ref.attempt();
        return key;
    }

    private static StepRef ref(Run run, Step step) {
        return new StepRef(run.id, step.name, step.attempt, step.status, run.priority, run.traceparent);
    }
}
