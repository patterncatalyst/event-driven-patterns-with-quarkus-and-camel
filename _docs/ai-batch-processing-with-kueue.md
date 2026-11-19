---
title: "Appendix I: Batch processing with Kueue"
order: 44
marker: "I"
label: "Appendix I"
part: appendices
permalink: /docs/ai-batch-processing-with-kueue/
description: "A batch has an input that ends and a moment when it is done. Each step runs as a Kubernetes Job, the partition is the unit of parallel work, a failed partition is replayed alone, and Kueue decides which Jobs may start. Run on minikube with a Process Manager in Quarkus and Camel."
duration: "75 minutes"
---

Most event-driven work is a stream: a topic that never ends and a consumer that never finishes. A batch is the other shape of work. Its input is bounded, such as all deliveries between two instants, and it has a moment when it is done and a result that can be checked. The result here is an end-of-day reconciliation per carrier: how many shipments were scheduled in a window, how many were delivered, and how many are still open.

A batch raises questions that a consumer group does not. Which step runs next, and what happens to the steps already finished when one fails? How many pods may run at once when two batches arrive together? Which batch wins when the cluster is full? And what tells you, afterwards, why a run took as long as it did when its pods lived for seconds?

The patterns here run on minikube. A small Quarkus and Camel service, the `batch-runner`, is the Process Manager from Hohpe and Woolf: it keeps the state of each run and creates the Job for the next step. A second image, the `batch-worker`, is a Quarkus command-mode application with three commands, `plan`, `reconcile` and `aggregate`. Kueue admits the Jobs.

{% include excalidraw.html file="ai-reconciliation-run" alt="batch-runner creates a plan Job, then an Indexed reconcile Job of three pods where index i reads partition i, then an aggregate Job that writes to the reconciliations topic; run, step, run_partition, partition_result and carrier_total tables in PostgreSQL hold the state of each step" caption="Figure I.1: One run is three Jobs, created in order, with their state in PostgreSQL" %}

The input is two topics, `edp.shipping.scheduled` and `edp.shipping.delivered`, with three partitions each. Both are keyed by order id, so the same order sits in partition `i` of both. That fact decides the shape of the whole run.

## Why this matters

When a batch fails partway through, it restarts everything, repeating work that already succeeded, or leaves an operator guessing which part is done. Without admission control, a shared cluster lets two batches ask for more capacity than it has, and the loser can be a service with nothing to do with either batch. Persist step state, replay by partition, and queue for resources, and a failed or contended run stays recoverable, so a reconciliation total stays trustworthy and one low-priority batch does not starve everything running beside it.

## Learning objectives

- Persist the state of every step so a failed run can be diagnosed and resumed instead of restarted from the beginning
- Partition a bounded input by key so one worker owns everything that belongs together with no coordination
- Separate a transient failure from a poison one and replay only the partitions that did not finish
- Size a resource queue so one batch cannot starve a cluster shared with other workloads

## Pattern: Step-wise batch with persisted step state

### The problem

A batch that is one long program can be started and can finish, and nothing in between is visible. When it fails at 90 percent, the only recovery is to start again from the beginning, and the operator cannot tell which part of the work was done. When the program is split into steps run by something else, the something else needs memory: which steps exist, which one is running, which are done, and what each one decided.

### The solution

**Split the batch into named steps, run each step as its own unit of work, and record the state of every step in a database that outlives the step.**

### How Quarkus and Camel model it

The runner accepts `POST /runs` with a window and an optional priority and answers 202 with a run id. A second request for the same window answers 409 while an earlier run for it has not failed. Each run has three steps in a fixed order, `plan`, `reconcile` and `aggregate`, and each step is a row with a state.

{% include excalidraw.html file="ai-step-states" alt="A step starts PENDING, becomes QUEUED when its suspended Job is created, RUNNING when admitted, then DONE or FAILED; an eviction returns a RUNNING step to QUEUED and a replay returns a FAILED step to QUEUED" caption="Figure I.2: The state of a step, including the two ways back to QUEUED" %}

A Camel route on a two-second timer drives the runs. It reads the steps that are RUNNING or QUEUED from PostgreSQL, reads each step's Job through the Camel Kubernetes Job component, and routes on what it finds with `choice()`: a completed Job marks the step DONE and creates the next step's Job, a failed Job marks the step and the run FAILED, and a Job that is still going changes nothing. The component can create, read and delete Jobs but has no consumer that is notified of a change, so polling on a timer is the way a controller is written with it.

{% raw %}
```java
from("scheduler:reconcile?delay={{reconcile.tick}}&initialDelay=2000").routeId("reconcile-tick")
        .bean("stepService", "workQueue")
        .split(body()).to("direct:poll-step");
```
{% endraw %}

Each step found goes to `poll-step`, which reads its Job and routes on the state it finds:

```java
.setBody(variable("step"))
.choice()
    .when(variable("jobState").isEqualTo(JobState.MISSING))
        .to("direct:create-job")
    .when(variable("jobState").isEqualTo(JobState.LOST))
        .bean("stepService", "failed(${body}, 'the Job is gone')")
    .when(variable("jobState").isEqualTo(JobState.SUSPENDED))
        .bean("stepService", "preempted")
    .when(variable("jobState").isEqualTo(JobState.ACTIVE))
        .bean("stepService", "admitted")
    .when(variable("jobState").isEqualTo(JobState.COMPLETE))
        .bean("stepService", "completed")
    .when(variable("jobState").isEqualTo(JobState.FAILED))
        .bean("stepService", "failed(${body}, 'the Job failed')")
.end();
```

A missing Job goes to `create-job`, which marks the step QUEUED and creates the Job through the same component:

```java
from("direct:create-job").routeId("create-job")
        .bean("stepService", "markQueued")
        .setVariable("step", body())
        .process(jobs::prepare)
        .to("kubernetes-job:local?operation=createJob")
```

The Job is created with `suspend: true`, which the template sets for every step:

```yaml
apiVersion: batch/v1
kind: Job
spec:
  suspend: true
```

The step is QUEUED until Kueue admits it and RUNNING after that. A Job that was running and is suspended again is not a failure: the step goes back to QUEUED, and a counter on the runner records the preemption. The distinction matters because a failed step stops the run and a queued one only waits.

```java
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
```

The state lives in PostgreSQL on CloudNativePG, with Flyway migrations owned by the runner. A step is one row, keyed by run and name:

```sql
CREATE TABLE step (
    run_id       text    NOT NULL REFERENCES run (id),
    name         text    NOT NULL,
    ordinal      integer NOT NULL,
    status       text    NOT NULL,
    attempt      integer NOT NULL,
    job_name     text,
    created_at   timestamptz,
    admitted_at  timestamptz,
    finished_at  timestamptz,
    PRIMARY KEY (run_id, name)
);
```

The worker reads and writes the same tables through Hibernate ORM with Panache and validates the schema instead of migrating it, so two services share a schema without both owning it.

The runner is a long-lived Deployment and is never admitted by Kueue. A controller that waited in the queue for its own quota would be unable to create the Jobs that free it.

## Pattern: Fan-out over partitions

### The problem

A bounded input is often larger than one process should read, and the work on each slice is independent. Splitting it by hand into N Jobs means N manifests, N names and a hand-written way to tell which slice a pod owns. Splitting it by record means a join across pods whenever two records belong together.

### The solution

**Run one Indexed Job whose pod index selects a partition of the input, so that each pod owns exactly the records that belong together.**

This is the Splitter from Hohpe and Woolf, carried out by Kubernetes: one input becomes one unit of work per partition.

### How Quarkus and Camel model it

The `plan` step reads the two topics and fixes, per partition, the offset range for the window: the first offset at or after the window start and the first offset at or after the window end. It stores them in `run_partition`, one row per partition, so the range is decided once and every attempt reads the same records. The table holds the range and the progress of each partition:

```sql
CREATE TABLE run_partition (
    run_id          text    NOT NULL REFERENCES run (id),
    partition       integer NOT NULL,
    scheduled_from  bigint  NOT NULL,
    scheduled_to    bigint  NOT NULL,
    delivered_from  bigint  NOT NULL,
    delivered_to    bigint  NOT NULL,
    status          text    NOT NULL,
    attempts        integer NOT NULL DEFAULT 0,
    records_read    bigint  NOT NULL DEFAULT 0,
    completed_at    timestamptz,
    PRIMARY KEY (run_id, partition)
);
```

Offsets come from Kafka's offsets-for-times lookup. A partition whose records all precede the window start has no answer for that time, so the worker uses the end offset for it:

```java
var found = c.offsetsForTimes(query);
for (TopicPartition tp : partitions) {
    var at = found.get(tp);
    out.get(tp.partition())[i] = at == null ? ends.get(tp) : at.offset();
}
```

The `reconcile` step is one Kubernetes Job in Indexed completion mode with three completions and a parallelism of three. Kubernetes gives each pod an index, available as `JOB_COMPLETION_INDEX`, and the worker treats the index as the partition number.

```java
builder.withCompletionMode("Indexed").withCompletions(PARTITIONS).withParallelism(PARTITIONS)
        .withBackoffLimitPerIndex(1).withMaxFailedIndexes(PARTITIONS);
```

{% include excalidraw.html file="ai-co-partitioned-fan-out" alt="Partitions 0, 1 and 2 of the scheduled and delivered topics, keyed by order id, feed index 0, 1 and 2 of one Indexed Job, and each index writes one run_partition row" caption="Figure I.3: Because both topics are keyed by order id, index i can count an order's two events without help" %}

Co-partitioning is what makes the fan-out free of coordination. An order's scheduled event and its delivered event share a key and therefore a partition, so one pod sees both and can decide whether the shipment is delivered or still open. If the topics were keyed differently, the pods would need to exchange records, and the Job would become a distributed join.

The worker reads a fixed range with the plain Kafka consumer from Quarkus: assign the partition, seek to the start offset and read to the end offset. SmallRye Reactive Messaging and the Camel Kafka consumer are built to follow a topic and cannot be told to stop at an offset, so they are the wrong tools here. The pod writes its counts per carrier into `partition_result` in the same transaction that marks its `run_partition` row DONE. Records outside the window are never read.

```java
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
```

In the example's first window the three partitions hold 6, 4 and 9 records, 19 in all, and the eight marker orders that lie outside every window are never read.

Each pod asks for 500 millicores of CPU. Three of them ask for 1500m together, a fact that matters once there is a queue.

## Pattern: Aggregate on completion

### The problem

The per-partition counts are partial. The reconciliation for a carrier is their sum, and the sum is only right when every partition is in. Something has to know that all partitions are done, and it has to produce the total once.

### The solution

**Run a final step that starts only after the fan-out step is complete and combines the stored partition results into one total per key.**

### How Quarkus and Camel model it

The Aggregator from Hohpe and Woolf, which Camel implements, is the familiar answer in a stream: collect messages by correlation id until a completion condition holds. It fits a batch badly. Camel completes an aggregation by size, by predicate or by timeout, and size completion counts messages, so a partition that is replayed sends its result a second time, which breaks the count. The batch has a stronger fact than a count: the fan-out Job is Complete, and the results are rows.

So the `aggregate` step is another Job. The runner creates it when the `reconcile` Job is Complete, not before. It reads `partition_result`, sums per carrier, upserts `carrier_total` and publishes one record per carrier to `edp.shipping.reconciliations` with the key `<run id>:<carrier>`. The topic is compacted, so the latest record per key is what remains. The publish is a Camel route: `split(body())`, the Splitter from Hohpe and Woolf, turns the list of totals into one message each.

```java
from("direct:publish-reconciliations").routeId("publish-reconciliations")
        .split(body())
            .process(e -> {
                var r = e.getMessage().getBody(CarrierReconciliation.class);
                e.getMessage().setHeader(KafkaConstants.KEY, r.runId() + ":" + r.carrier());
            })
            .marshal().json(JsonLibrary.Jackson)
            .to("kafka:edp.shipping.reconciliations")
        .end();
```

Summing stored rows makes the step safe to run twice. It reads the same rows and writes the same totals, and the compacted topic holds one value per key regardless. For the first window the totals are FASTLINE 7 scheduled, 4 delivered and 3 open, and AIRCO 5 scheduled, 3 delivered and 2 open.

## Pattern: Replay failed steps only

### The problem

Some failures are transient and some are not. A partition that cannot be read because of a broker restart will read fine a minute later. A partition that contains a record the code can never process will fail every time. Starting the whole run again after either one repeats the work of every partition that succeeded, and repeats it with a risk: the second pass may count something twice.

### The solution

**Retry transient failures per partition, stop a poison partition without retrying it, and replay a failed run by running only what is not done.**

### How Quarkus and Camel model it

The worker's exit code carries the verdict: 0 is success, 1 is transient, 42 is poison. The `reconcile` Job maps them to Kubernetes policy. `backoffLimitPerIndex: 1` lets each index be retried once, in its own pod, while the other indexes carry on. A `podFailurePolicy` rule turns exit code 42 into `FailIndex`, which fails that index immediately and without a retry. `maxFailedIndexes` lets the remaining indexes run to completion before the Job is marked Failed.

```java
static final int OK = 0;
static final int TRANSIENT = 1;
static final int POISON = 42;
```

```java
if (config.on(config.failPolicy())) {
    builder.withPodFailurePolicy(new PodFailurePolicyBuilder()
            .addNewRule().withAction("FailIndex").withNewOnExitCodes().withContainerName("worker")
            .withOperator("In").withValues(POISON).endOnExitCodes().endRule()
            .addNewRule().withAction("Ignore").addNewOnPodCondition().withType("DisruptionTarget")
            .withStatus("True").endOnPodCondition().endRule().build());
}
```

Each pod receives its own failure count through the downward API from the annotation `batch.kubernetes.io/job-index-failure-count`, so a pod can tell its first attempt from its retry.

When a transient fault was injected on one partition, that index had two pods, one that failed and one that completed, while the other two had one each, and the Job was Complete. That run's `reconcile` step took 28 seconds, against 10 to 12 seconds with no fault. When a poison fault was injected on partition 1, the Job ended Failed with `failedIndexes` of `1` and `completedIndexes` of `0,2`; index 1 had a single pod, and the other two finished. Kueue's Workload for that Job ended `Finished` with the reason `Failed`, and its quota was released.

A failed run is replayed with `POST /runs/{id}/replay`, which answers 202 only for a run that is FAILED. The runner does not create a plan Job again, because the offsets are already fixed. It creates a new `reconcile` Job, a new attempt, whose name carries the attempt number.

{% include excalidraw.html file="ai-replay-failed-index" alt="Attempt 1 has index 0 and 2 done and index 1 failed with exit 42; the replay Job's index 0 and 2 see their run_partition rows DONE and exit 0 without reading Kafka, index 1 reads partition 1 and becomes DONE, and aggregate then runs once" caption="Figure I.4: A replay Job runs all indexes, and the finished ones exit at once" %}

The replay rule is a guard in the worker: an index whose `run_partition` row is already DONE exits 0 without reading Kafka.

```java
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
```

A Job cannot be told to run only index 1. A new Job runs every index, so the guard makes the finished ones free in time, and the aggregate step then runs once on three DONE partitions.

The replay costs something the guard cannot remove. Kueue reserves quota for the whole pod set, three pods at 500m, so the replay Job waits for 1500m even though only one index has work to do. A replay costs few Kafka reads and the full quota.

The guard is also what makes the totals right. The counts are an upsert keyed by run, partition and carrier, so a repeated write would store the same numbers and the totals would still be correct without the guard. The guard avoids the read, and the upsert keeps the result correct.

> **🤖 Explore with your agent**
> "In examples/ai-batch-processing-with-kueue, inject exit 42 on partition 1 through the ai-demo-faults ConfigMap, start a run and replay it. Read the batch-runner step rows and the run_partition rows before and after the replay, and say which indexes read Kafka in the second attempt and why. Then read the replay rule in the batch-worker's reconcile command and state what it checks before it reads a partition."
> Check: after the replay, partitions 0 and 2 keep their first completed_at and attempts of 1, partition 1 is DONE with attempts of 2, and the carrier totals equal the in-window counts.

## Pattern: Admission control with Kueue

### The problem

Kubernetes starts a Job as soon as it is created and lets the scheduler place its pods on whatever has room. Two batches submitted together both start, ask for more than the team's share of the cluster together, and compete for nodes with services that were there first. The scheduler has no notion of a quota for a team, no queue with an order, and no way to say that a low-priority batch should give way to a high-priority one.

### The solution

**Create every batch Job suspended, and let a queueing controller admit it only when its whole resource request fits the quota of the queue it was submitted to.**

### How Quarkus and Camel model it

Kueue is a Kubernetes controller that does exactly this. The batch code does not call it. It creates a Job, labelled with the queue, and Kueue's webhook and controller do the rest. The runner adds two labels to the Job it creates, the queue and, when the run has one, the priority class:

```java
Map<String, String> labels(StepRef ref) {
    Map<String, String> labels = new LinkedHashMap<>();
    labels.put(RUN_LABEL, ref.runId());
    labels.put(STEP_LABEL, ref.name());
    if (config.on(config.queue())) {
        labels.put(QUEUE_LABEL, config.queueName());
    }
    if (ref.priority() != null && config.on(config.priority())) {
        labels.put(PRIORITY_LABEL, ref.priority());
    }
    return labels;
}
```

{% include excalidraw.html file="ai-kueue-objects" alt="A ResourceFlavor and a WorkloadPriorityClass are cluster scoped; the ClusterQueue holds cpu 1750m and memory 3Gi, BestEffortFIFO and a preemption policy; a LocalQueue in the edp namespace feeds it; the runner's Job carries the queue-name and priority-class labels and Kueue creates a Workload that is admitted against the ClusterQueue" caption="Figure I.5: The Kueue objects behind one queue" %}

- A **ResourceFlavor** names a kind of capacity. Here there is one, `default-flavor`, with no node labels.
- A **ClusterQueue** holds the quota: 1750 millicores of CPU and 3Gi of memory on that flavor. It also holds the policies that decide order and preemption.

```yaml
# 1750m admits one reconcile step (3 x 500m) plus one plan or aggregate step (250m), and never two reconcile steps.
apiVersion: kueue.x-k8s.io/v1beta2
kind: ClusterQueue
metadata: {name: reconciliation}
spec:
  namespaceSelector:
    matchLabels: {kubernetes.io/metadata.name: edp}
  queueingStrategy: BestEffortFIFO
  preemption:
    withinClusterQueue: LowerPriority
    reclaimWithinCohort: Never
  resourceGroups:
    - coveredResources: [cpu, memory]
      flavors:
        - name: default-flavor
          resources:
            - {name: cpu, nominalQuota: 1750m}
            - {name: memory, nominalQuota: 3Gi}
```
- A **LocalQueue** is the namespaced door to a ClusterQueue. Jobs in `edp` name it with the label `kueue.x-k8s.io/queue-name`.
- A **Workload** is what Kueue creates for each Job. It is the unit that is admitted. Its pod set carries the resource request of all the pods of the Job.
- A **WorkloadPriorityClass** gives a Workload a priority without touching the priority of its pods. Here there are `batch-low` at 100 and `batch-high` at 1000, chosen by a second label on the Job. A Job with no priority label has priority 0.

```yaml
apiVersion: kueue.x-k8s.io/v1beta2
kind: WorkloadPriorityClass
metadata: {name: batch-low}
value: 100
---
apiVersion: kueue.x-k8s.io/v1beta2
kind: WorkloadPriorityClass
metadata: {name: batch-high}
value: 1000
```

Kueue's webhook sets `suspend: true` on a Job that carries the queue label, so the runner creates the Job suspended and the webhook would do it regardless. The step is QUEUED. Kueue admits the Workload when the quota has room, unsuspends the Job, and the pods are created. When the Job finishes, the quota is released. On an empty quota the runner sees a step admitted about two seconds after it creates the Job, because it checks on a two-second tick. The step itself then runs about 10 seconds for `plan` and `aggregate`, which is JVM start plus the work. In one contended run a step waited 52 seconds for quota; it then ran 52 seconds because partition 2 was slowed by 40 seconds in that run.

A Workload that does not fit stays pending. Its `QuotaReserved` condition is False with the reason `WaitingForQuota`, and the message says what is missing, for example insufficient unused quota for cpu in the flavor and how much more is needed. `Admitted` is False with the reason `NoReservation`. The Job is suspended and has no pods.

The queueing strategy decides what a waiting Workload does to the ones behind it. With `BestEffortFIFO`, the default, the order is priority and then creation time, but an older Workload that cannot be admitted does not block a newer one that fits. A small Job passes a large one that is waiting. With `StrictFIFO`, an older Workload that cannot be admitted blocks newer ones even if they would fit (Kueue, Cluster Queue). A batch with one large step and several small ones wants the first; a queue where arrival order is a promise wants the second. The quota here is configured for `BestEffortFIFO`, and a one-pod, 250m Job was admitted while a three-pod Job waited.

Kueue's quota is a count of what the batch may request, not a measure of node capacity. The scheduler sees the node's allocatable resources, and Kueue never checks them. The queue limits what the batch may ask for; the node limits what exists. The quota here is set so the CPU is what binds: with three pods of 500m against 1750m, one `reconcile` Job runs at a time, and a second waits.

{% include excalidraw.html file="ai-queueing-timeline" alt="Two runs against a quota of 1750m: the first reconcile Job runs with 3 pods at 1500m, the second waits suspended with reason WaitingForQuota and is admitted when the first completes; without the queue label both run at once and request 3000m" caption="Figure I.6: Two runs, one quota: the second reconcile Job waits" %}

With the queue label removed, both Jobs start together and ask for 3000m against a queue that never saw them. The runner then creates the Jobs unsuspended, because with no label Kueue never sees them and nothing would start a suspended one:

```java
if (!config.on(config.queue())) {
    builder.withSuspend(false);   // no queue label: Kueue never sees the Job, so nothing would ever start a suspended one
}
```

Without the label there is no Workload and no admission wait. What the queue did is visible in what it prevented: the Workload that waited, the pending-workloads metric, and the overlap of pod requests.

### Priority and preemption

Priority orders the waiting Workloads, and preemption lets a Workload that does not fit make room. The ClusterQueue sets `withinClusterQueue: LowerPriority` and `reclaimWithinCohort: Never`: a pending Workload may evict an admitted one in the same queue only if the admitted one has a lower priority. The Kueue documentation describes the values of the first field as `Never`, `LowerPriority` and `LowerOrNewerEqualPriority`.

A low-priority run was admitted and two of its three partitions finished. The third, a slow partition made so by a fault setting, was still running. A high-priority run was then submitted.

{% include excalidraw.html file="ai-preemption" alt="The low-priority run has index 0 and 1 done and index 2 running; when the high-priority run is submitted the low run's Workload is evicted with reason Preempted and its Job is suspended with completedIndexes 0,1 kept; the high run is admitted and completes; the low run is admitted again and starts one pod for index 2 only" caption="Figure I.7: Preemption suspends a Job and keeps what it finished" %}

Kueue evicted the low-priority Workload within a tenth of a second. Its conditions were `Evicted` True with the reason `Preempted`, `Preempted` True with the reason `InClusterQueue`, and `Requeued` True. The Job was suspended again, and the runner moved the step to QUEUED, never to FAILED. The high-priority run was admitted in the same second, and the low run was admitted again 15.6 seconds after the high run was submitted.

Kueue evicted a run that had only one pod left because it reserves quota for the whole pod set: the low run still held 1500m with a single active pod, so the high run did not fit without evicting it.

When the high run completed, the low run was admitted again. `completedIndexes` still read `0,1`, the pods of indexes 0 and 1 were the same pods as before, and the evicted pod of index 2 was deleted and replaced by one new pod, the only one created. Partitions 0 and 1 were not read again: their `attempts` stayed at 1 and their `completed_at` did not change. Index 2 shows 2 attempts, because the interrupted attempt had already counted when the worker began it. `status.failed` stayed at 0 and the failure count of every index was 0, because an eviction does not count as a failed attempt.

The pod that was running when the Job was suspended stayed Terminating for its grace period and overlapped with the high run's pods, so the sum of pod requests can briefly exceed the quota. Quota limits admission and does not account for the time a pod takes to terminate.

The `podFailurePolicy` also has a rule that ignores pod failures with the condition `DisruptionTarget`. It guards against API-initiated evictions such as a node drain, so that they do not spend the per-index retry. Kueue preemption does not exercise it: Kueue suspends the Job and the Job controller deletes the pod without that condition, so the deleted pod carried no `DisruptionTarget` condition, which is why the failure count stayed at 0 without it.

### Cohorts, borrowing and partial admission

Three more features are not used here. They are described from the Kueue documentation.

A **cohort** groups ClusterQueues so that they can borrow unused quota from each other. A ClusterQueue that borrows must define a nominal quota for the resource and flavor, which can be zero (Kueue, Cohort). The `borrowingLimit` caps how much a queue may borrow, and the `lendingLimit` caps how much it lets others borrow. `reclaimWithinCohort` lets a queue take back its own quota by evicting what others borrowed, with the values `Never`, `LowerPriority` and `Any`, and `borrowWithinCohort` controls whether a Workload that needs to borrow may preempt in other queues (Kueue, Cluster Queue). With one team and one queue, none of this applies. With several teams that each need a guaranteed share and can use each other's idle capacity, it is the reason to use Kueue at all.

**Partial admission** lets a Job run with less parallelism than it asked for. The annotation `kueue.x-k8s.io/job-min-parallelism` sets the minimum, and Kueue reduces the parallelism only after borrowing and preemption are not possible. The completions count does not change (Kueue, Run A Kubernetes Job). A reconciliation Job gains nothing from it, because the three indexes are three partitions and none can be dropped, but it suits work where fewer workers only means slower.

**JobSet** treats a group of Jobs as one unit with one Workload, so Kueue admits all of them or none. The reconciliation steps are sequential and each is admitted separately, which is the point: with one Workload for all steps, a failure would restart the whole set. **MultiKueue** dispatches Workloads across clusters; this example uses one cluster.

## Observing a batch run

A batch run is hard to observe for two reasons: its pods live for seconds, and the question "why was it slow?" is often a question about waiting for quota.

{% include excalidraw.html file="ai-observability-path" alt="Worker pods push traces and logs by OTLP when they exit, the runner pushes spans and step metrics, and the collector scrapes the Kueue controller over HTTPS with a bearer token; Tempo, Loki and Prometheus metrics feed the Grafana dashboard edp-batch" caption="Figure I.8: Pods push at exit, the runner reports for them, and the collector scrapes Kueue" %}

**One trace per run.** The runner opens a span for the run and stores its `traceparent` in PostgreSQL with the run. When it creates a Job it passes the value to the pod as the environment variable `TRACEPARENT`. The runner sets it in the Job's environment:

```java
if (ref.traceparent() != null) {
    env.add(new EnvVarBuilder().withName("TRACEPARENT").withValue(ref.traceparent()).build());
}
```

The worker reads `TRACEPARENT` through its config mapping and hands it to the W3C trace-context propagator as the carrier:

```java
Map<String, String> carrier = env.traceparent().map(value -> Map.of("traceparent", value)).orElse(Map.of());
Context parent = W3CTraceContextPropagator.getInstance().extract(Context.root(), carrier, CARRIER);
```

This is how the OpenTelemetry specification propagates context to a process that cannot receive headers (OpenTelemetry, Environment Variables as Context Propagation Carriers). The spans of the three steps and the index pods land in one trace. A pod that lives seconds pushes its spans and logs over OTLP as it exits, and the logs carry the trace id.

**Metrics from the runner, not the pods.** A Prometheus scrape every ten seconds mostly misses a pod that lives five. The long-lived runner counts instead: step duration, time in the queue, failures, replays, preemptions and records read, all as Micrometer meters. Prometheus receives them as `batch_step_duration_milliseconds`, `batch_step_queue_wait_milliseconds`, `batch_step_failures_total{step}`, `batch_step_replays_total`, `batch_step_preemptions_total{step}` and `batch_partition_records_total`.

**Metrics from Kueue.** The Kueue controller serves metrics over HTTPS on port 8443 and requires a bearer token that Kubernetes accepts through TokenReview and SubjectAccessReview (Kueue, metrics access). A second scrape job in the collector uses the service account token of the observability pod, bound to the `kueue-metrics-reader` ClusterRole. The controller's certificate is self-signed and regenerated at start, with no CA to verify against, so the job sets `insecure_skip_verify`. That is also what upstream's own Prometheus configuration does (Kueue, Configure Prometheus with TLS). It means the token authenticates the reader but the reader does not authenticate the server, which is acceptable for a controller inside the same cluster and a local demo, and should not be copied to a scrape across a network.

Setting `enableClusterQueueResources` in Kueue's configuration adds the quota series, usage and nominal quota per queue. The series that tell the story are `kueue_pending_workloads` (by `active` or `inadmissible`), `kueue_admitted_active_workloads`, `kueue_admission_wait_time_seconds`, `kueue_cluster_queue_resource_usage` and `kueue_cluster_queue_nominal_quota`. After a preemption, `kueue_preempted_workloads_total` appears, labelled by the preempting queue and the reason, and `kueue_evicted_workloads_total` by queue, priority class and reason. Neither exists before its first event, and every series lags the API by one scrape. The Kueue series appear in Prometheus about 45 seconds after the observability pod starts.

A dashboard, `edp-batch`, shows the queue depth, the quota in use against the nominal quota, the admission wait and the runner's step metrics.

## Installing and removing Kueue

Kueue v0.20.6 is the newest stable release on the newest minor line. It is installed from the release manifest, checked against a recorded sha256, and applied server-side.

```bash
kubectl apply --server-side -f \
  https://github.com/kubernetes-sigs/kueue/releases/download/v0.20.6/manifests.yaml
```

The example applies the same file through a kustomization that sets the controller image by digest and turns on the queue resource metrics. Kubernetes 1.34 or newer is recommended by the project, and the cluster here runs v1.37.2.

Two properties of the install decide how it must be removed. The Kueue pod webhook intercepts pods in every namespace except `kube-system` and `kueue-system`, and it fails closed: its `failurePolicy` is `Fail`, so while the controller is down or half removed, pod creation in those namespaces is refused. Kueue is therefore installed by the example's own `verify.sh` and removed by it, and is not part of the shared cluster setup that other examples rely on. The install also registers an APIService, `v1beta2.visibility.kueue.x-k8s.io`, served by the controller.

The teardown order follows from them. Jobs go first, then the Workloads, which carry a finalizer that only the controller removes. Then the LocalQueue, the ClusterQueue, the WorkloadPriorityClasses and the ResourceFlavor, and last the manifest, which removes the controller, the webhooks, the CRDs and the APIService. If the controller goes first, a Workload stays behind with its finalizer and namespace deletion hangs, because the APIService can no longer be discovered. It recovered about a minute after the controller returned. Finalizers are never stripped by hand, and the teardown fails loudly if any Kueue CRD, webhook configuration or APIService remains.

Every batch Job carries the `edp.batch/run` label. A Job without it leaves a Workload that the teardown waits on.

## Build, run, observe

```bash
examples/_k8s/cluster.sh up
cd examples/ai-batch-processing-with-kueue
./verify.sh
```

`verify.sh` installs Kueue, runs a reconciliation, injects a transient and a poison fault, replays the failed run, submits two runs against one quota, preempts a low-priority run, and removes Kueue again. Watch the Workloads, the `run_partition` rows and the `edp-batch` dashboard. It ends with `34/34 checks passed`.

Try one change and rerun:

- `QUEUE=off ./verify.sh` (demo code): the Jobs start unsuspended and unqueued, both runs start together and the queue checks fail.
- `REPLAY_GUARD=off ./verify.sh` (demo code): the replay reads every partition again, and the totals still come out right because the counts are upserted.
- Put `FAULT_PARTITION` and `FAULT_MODE` in the `ai-demo-faults` ConfigMap: that partition fails once or for good, and the Job's `completedIndexes` and `failedIndexes` change with it.

## When not to use it

A stream that never ends has no "done", so it does not fit a Job: a consumer group is the right shape. A small job that one JVM can finish in a minute needs no queue and no pods per step: a scheduled Camel route, or Jakarta Batch with its chunk and restart model in one process, is less to operate. Spring Batch's reference is a good source for the concepts of steps, partitioning and restart even where the code is not used.

Kueue earns its cost when several batches share a cluster and one of them must not starve the others. For a single batch on a cluster of its own, the Job's `parallelism` is enough. When the steps must start together or not at all, a JobSet is a closer fit. When the real need is a graph of steps with a UI, Argo Workflows is the established answer, with a larger footprint. When the work must be spread across clusters, that is MultiKueue.

The pod webhook is cluster-wide and fails closed, and the install adds eleven CRDs and a controller that asks for 500m of CPU and 512Mi of memory. That is a platform decision and not a per-example one.

## Common pitfalls

- Removing the queue label from a batch Job to skip the wait: without it the admission controller never sees the Job, so nothing throttles two batches that together ask for more than the cluster's quota.
- Replaying a failed run without the per-partition guard: every index reads Kafka again, and although the totals still come out right because of the upsert, the replay pays the full quota for work that is mostly already done.
- Tearing down the queue controller before the Jobs and Workloads that depend on it: a Workload's finalizer is removed only by the controller, so a namespace delete hangs until the controller returns.
- Assuming a preempted or evicted run frees its quota right away: quota is reserved for a Workload's whole pod set until every pod of it has actually terminated, so a replacement run can still wait out a pod in its grace period.

## References

- Kueue, [Overview](https://kueue.sigs.k8s.io/docs/overview/), [Resource Flavor](https://kueue.sigs.k8s.io/docs/concepts/resource_flavor/), [Cluster Queue](https://kueue.sigs.k8s.io/docs/concepts/cluster_queue/) (quota, `queueingStrategy`, preemption fields), [Local Queue](https://kueue.sigs.k8s.io/docs/concepts/local_queue/), [Workload](https://kueue.sigs.k8s.io/docs/concepts/workload/)
- Kueue, [Preemption](https://kueue.sigs.k8s.io/docs/concepts/preemption/), [Workload Priority Class](https://kueue.sigs.k8s.io/docs/concepts/workload_priority_class/), [Cohort](https://kueue.sigs.k8s.io/docs/concepts/cohort/) (borrowing and lending)
- Kueue, [Run A Kubernetes Job](https://kueue.sigs.k8s.io/docs/tasks/run/jobs/) (the queue-name label and partial admission), [Run job with WorkloadPriority](https://kueue.sigs.k8s.io/docs/tasks/manage/run_job_with_workload_priority/)
- Kueue, [Prometheus Metrics](https://kueue.sigs.k8s.io/docs/reference/metrics/), [Scrape metrics from a sidecar](https://kueue.sigs.k8s.io/v0.20/docs/tasks/manage/observability/metrics_access_control/) (bearer-token access), [Configure Prometheus with TLS](https://kueue.sigs.k8s.io/docs/tasks/manage/productization/prometheus/)
- Kueue, [Installation](https://kueue.sigs.k8s.io/docs/getting-started/installation/), [MultiKueue](https://kueue.sigs.k8s.io/docs/concepts/multikueue/)
- JobSet, [Documentation](https://jobset.sigs.k8s.io/docs/)
- Kubernetes, [Jobs](https://kubernetes.io/docs/concepts/workloads/controllers/job/) (Indexed completion mode, backoff limit per index, pod failure policy, suspending a Job), [Indexed Job for Parallel Processing with Static Work Assignment](https://kubernetes.io/docs/tasks/job/indexed-parallel-processing-static/), [Handling retriable and non-retriable pod failures with Pod failure policy](https://kubernetes.io/docs/tasks/job/pod-failure-policy/)
- Kubernetes blog, [Kubernetes v1.33: Job's Backoff Limit Per Index Goes GA](https://kubernetes.io/blog/2025/05/13/kubernetes-v1-33-jobs-backoff-limit-per-index-goes-ga/); [Kubernetes 1.31: Pod Failure Policy for Jobs Goes GA](https://kubernetes.io/blog/2024/08/19/kubernetes-1-31-pod-failure-policy-for-jobs-goes-ga/)
- Apache Camel, [Kubernetes Job component](https://camel.apache.org/components/next/kubernetes-job-component.html)
- Quarkus, [Command mode applications](https://quarkus.io/version/3.40/guides/command-mode-reference), [Command mode with Picocli](https://quarkus.io/version/3.40/guides/picocli), [Apache Kafka reference guide](https://quarkus.io/version/3.40/guides/kafka)
- OpenTelemetry, [Environment Variables as Context Propagation Carriers](https://opentelemetry.io/docs/specs/otel/context/env-carriers/)
- Jakarta EE, [Jakarta Batch 2.1](https://jakarta.ee/specifications/batch/2.1/); Spring, [Spring Batch reference](https://docs.spring.io/spring-batch/reference/), [Scaling and Parallel Processing](https://docs.spring.io/spring-batch/reference/scalability.html), [Configuring a Step for Restart](https://docs.spring.io/spring-batch/reference/step/chunk-oriented-processing/restart.html)
- Gregor Hohpe and Bobby Woolf, *Enterprise Integration Patterns*, Addison-Wesley, 2003: [Process Manager](https://www.enterpriseintegrationpatterns.com/patterns/messaging/ProcessManager.html), [Aggregator](https://www.enterpriseintegrationpatterns.com/patterns/messaging/Aggregator.html), [Splitter](https://www.enterpriseintegrationpatterns.com/patterns/messaging/Sequencer.html)
- [Idempotent consumer and inbox]({{ '/docs/11-idempotent-consumer-and-inbox/' | relative_url }}): making a repeat harmless
- [Strimzi and KRaft]({{ '/docs/28-strimzi-and-kraft/' | relative_url }}): the cluster
- [Deploying event-driven services on minikube]({{ '/docs/af-deploying-event-driven-services/' | relative_url }}): the shared cluster setup
