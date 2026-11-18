package com.patterncatalyst.shipping.batch.runner;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;

/** Accepts a run, replays a failed one and reports on either. */
@Named("runService")
@ApplicationScoped
public class RunService {

    private static final Logger LOG = Logger.getLogger(RunService.class);
    static final Set<String> PRIORITIES = Set.of("batch-low", "batch-high");
    static final List<String> STEPS = List.of(StepRef.PLAN, StepRef.RECONCILE, StepRef.AGGREGATE);

    private final RunTracer tracer;
    private final BatchMetrics metrics;

    RunService(RunTracer tracer, BatchMetrics metrics) {
        this.tracer = tracer;
        this.metrics = metrics;
    }

    public record StepView(String name, int ordinal, String status, int attempt, String jobName, Instant createdAt,
            Instant admittedAt, Instant finishedAt) {
    }

    public record PartitionView(int partition, String status, long recordsRead, Instant completedAt) {
    }

    public record RunView(String id, Instant from, Instant to, String status, String priority, Instant createdAt,
            Instant finishedAt, List<StepView> steps, List<PartitionView> partitions) {
    }

    @Transactional
    public Map<String, String> create(RunRequest request) {
        if (request == null || request.from() == null || request.to() == null) {
            throw new BatchException(400, "from and to are required ISO-8601 instants");
        }
        if (!request.from().isBefore(request.to())) {
            throw new BatchException(400, "from must be before to");
        }
        String priority = request.priority();
        if (priority != null && !PRIORITIES.contains(priority)) {
            throw new BatchException(400, "unknown priority " + priority + ", expected one of " + PRIORITIES);
        }
        if (Run.count("windowFrom = ?1 and windowTo = ?2 and status <> ?3", request.from(), request.to(), Run.FAILED) > 0) {
            throw new BatchException(409, "a run for this window exists");
        }
        Run run = new Run();
        run.id = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        run.windowFrom = request.from();
        run.windowTo = request.to();
        run.status = Run.RUNNING;
        run.priority = priority;
        run.createdAt = Instant.now();
        run.traceparent = tracer.start(run.id, "batch.run", null);
        try {
            run.persistAndFlush();
        } catch (PersistenceException e) {
            tracer.end(run.id, true);
            throw new BatchException(409, "a run for this window exists");
        }
        for (int i = 0; i < STEPS.size(); i++) {
            Step step = new Step();
            step.runId = run.id;
            step.name = STEPS.get(i);
            step.ordinal = i;
            step.status = Step.PENDING;
            step.attempt = 1;
            step.persist();
        }
        LOG.infof("run %s accepted for [%s, %s) priority %s", run.id, run.windowFrom, run.windowTo, priority);
        return Map.of("id", run.id);
    }

    /** Creates the next attempt of the failed step. The steps that are DONE are not touched. */
    @Transactional
    public Map<String, String> replay(String id) {
        Run run = Run.findById(id);
        if (run == null) {
            throw new BatchException(404, "no run " + id);
        }
        if (!Run.FAILED.equals(run.status)) {
            throw new BatchException(409, "run " + id + " is " + run.status + ", only a FAILED run is replayed");
        }
        Step failed = Step.<Step> find("runId = ?1 and status = ?2", id, Step.FAILED).firstResult();
        if (failed == null) {
            throw new BatchException(409, "run " + id + " has no failed step");
        }
        failed.attempt++;
        failed.status = Step.PENDING;
        failed.jobName = null;
        failed.createdAt = null;
        failed.admittedAt = null;
        failed.finishedAt = null;
        run.status = Run.RUNNING;
        run.finishedAt = null;
        run.traceparent = tracer.start(run.id, "batch.replay", run.traceparent);
        try {
            run.flush();
        } catch (PersistenceException e) {
            tracer.end(run.id, true);
            throw new BatchException(409, "a run for this window exists");
        }
        metrics.replay();
        LOG.infof("run %s replays step %s as attempt %d", id, failed.name, failed.attempt);
        return Map.of("id", id, "step", failed.name, "attempt", String.valueOf(failed.attempt));
    }

    @Transactional
    public RunView view(String id) {
        Run run = Run.findById(id);
        if (run == null) {
            throw new BatchException(404, "no run " + id);
        }
        List<StepView> steps = Step.<Step> list("runId = ?1 order by ordinal", id).stream()
                .map(s -> new StepView(s.name, s.ordinal, s.status, s.attempt, s.jobName, s.createdAt, s.admittedAt,
                        s.finishedAt))
                .toList();
        List<PartitionView> partitions = RunPartition.<RunPartition> list("runId = ?1 order by partition", id).stream()
                .map(p -> new PartitionView(p.partition, p.status, p.recordsRead, p.completedAt)).toList();
        return new RunView(run.id, run.windowFrom, run.windowTo, run.status, run.priority, run.createdAt,
                run.finishedAt, steps, partitions);
    }
}
