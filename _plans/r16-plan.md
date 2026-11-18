# r16 plan: Appendix I, batch processing with Kueue

Planned 2026-11-18 (Opus planner, lgtm-relay). Approved by the owner 2026-11-18 with these answers:

- Appendix I, placed after H; H keeps its letter and order.
- **No word cap for Appendix I** (owner): as long as needed to cover most of the topic, never padded. Build, run, observe stays short; the example stays small.
- Q1 Kueue metrics TLS: bearer token + `insecure_skip_verify`, as upstream's own ServiceMonitor does; stated once in prose.
- Q2 deck: yes, 3 slides (120 to 123).
- Q3 trigger: REST on the runner (`POST /runs`).
- Q4 priority and preemption: **one case runs live** (owner changed the recommendation). Cohorts, borrowing, partial admission and StrictFIFO are described from sources only.
- Q5 Kueue installed and removed by the appendix's own verify.sh on every run; never by `cluster.sh`.
- Q6 workload: end-of-day delivery reconciliation per carrier.

## Status

| Step | State |
|---|---|
| 0 preflight, branch, plan | done 2026-11-18 |
| 1A sources and pins | done |
| 1B substrate | done |
| G0 spike | done 2026-11-18 |
| 2A Java | done (5092158) |
| 2B manifests, verify.sh, README | done (49ece99) |
| 2C prose, example page, figures | done (aaf1e4a) |
| 2D consistency | done (b179b60) |
| 3 live window | done: 34/34 twice, witnesses match |
| 4 fixes | done (4b9a92e): 7 bugs |
| 5 deck | done: slides 118 to 120, 123 total |
| 6 final fresh run, gates | done: gates clean; repair-round live run 1 |
| 7 Opus validation | done: 0 blocking, repair round 1 |
| 8 ship, Notion | |

## Approach

The workload is an end-of-day delivery reconciliation per carrier (FASTLINE, AIRCO) over a window `[T0, T1)`: ShipmentScheduled count, ShipmentDelivered count, and shipments still open. Both input topics (`edp.shipping.scheduled`, `edp.shipping.delivered`) have 3 partitions and are keyed by order id, so partition i of both holds the same orders: the partition is the fan-out unit. Vocabulary is "reconciliation" (validate.py bans `Settlement`, `Invoice`, `Manifest`). The output is a payload record `CarrierReconciliation`, no new entity.

Each step is a Kubernetes Job admitted by Kueue, driven by a small Quarkus + Camel Process Manager (Hohpe and Woolf):

- **batch-runner** (Deployment, never Kueue-managed): Camel REST DSL `POST /runs` (body: window, optional `priority`), `POST /runs/{id}/replay`, `GET /runs/{id}`. A Camel scheduler reconcile route (2 s) reads RUNNING and QUEUED steps from CloudNativePG, reads their Jobs with `kubernetes-job:...?operation=getJob`, routes on state with `choice()`, creates the next step's Job with `operation=createJob` (`suspend: true`, labels `kueue.x-k8s.io/queue-name: reconciliation`, `edp.batch/run`, `edp.batch/step`, and `kueue.x-k8s.io/priority-class` when the run has one). Owns Flyway migrations and step metrics (Micrometer). `camel-kubernetes` has no `kubernetes-job` consumer, so polling is the controller idiom.
- **batch-worker** (one image, Quarkus command mode with Picocli: `plan`, `reconcile`, `aggregate`): Panache (schema validate, no migrations), Quarkus `kafka-client` with the injected `default-kafka-broker` config for bounded reads (`offsetsForTimes`, `assign`, `seek`; SmallRye and the Camel Kafka consumer cannot read a fixed offset range), Camel only to publish totals (`split(body())` to `kafka:edp.shipping.reconciliations`).

Rejected: Camel-only batch (no per-step pods, quota or per-index retry); Kafka-driven workers (no "done" signal; Kueue admits Jobs); a driver Job (holds quota while waiting); Camel Aggregator over partition results (a replayed partition breaks `completionSize`); JobSet (one Workload for all steps, restarts the whole set; named in prose); MultiKueue (one cluster; one line); Argo Workflows / Kubeflow Pipelines (too heavy; one line); Jakarta Batch via `quarkus-jberet` (one JVM, no admission; under When not to use it; Spring Batch cited for concepts only); Kueue in `cluster.sh` (its pod webhook is cluster-wide with `failurePolicy: Fail` and would change the substrate of 28 to 35, B and F); Helm (house rule: none).

## Placement

`_docs/ai-batch-processing-with-kueue.md`, title "Appendix I: Batch processing with Kueue", `order: 44`, `marker: "I"`, `label: "Appendix I"`, `part: appendices`. Example `examples/ai-batch-processing-with-kueue/`, example page `_example_pages/ai-batch-processing-with-kueue.md`, figure specs `diagrams/specs/ai-*.py`. No reader-facing page says H is last; nothing to reword.

## Kueue survey (2026-11-18)

- Pin **Kueue v0.20.6** (2026-11-12), newest stable on the newest minor (v0.19.13 same day on the older line). Sources: https://api.github.com/repos/kubernetes-sigs/kueue/releases, https://github.com/kubernetes-sigs/kueue/releases/tag/v0.20.6.
- Manifest `https://github.com/kubernetes-sigs/kueue/releases/download/v0.20.6/manifests.yaml`, sha256 `b5f643595cfe4799b47acd3499f535fb7137af1dd734a5a66ed67a4fe9f9e466`. Image `registry.k8s.io/kueue/kueue:v0.20.6`, digest recorded in 1A and pinned with a kustomize `images:` digest (as `KSM_IMAGE`). `imagePullPolicy: Always` upstream: G0 decides whether to patch to `IfNotPresent`.
- Kubernetes: README "1.34 or newer recommended"; e2e matrix `hack/make/test.mk` `E2E_K8S_VERSIONS ?= 1.34.11 1.35.8 1.36.4 1.37.0`, go.mod `k8s.io/api v0.37.1`. Our v1.37.2 is in range.
- Install: `kubectl apply --server-side` of the sha256-checked file through `examples/_k8s/platform/kueue/kustomization.yaml` (config patch `metrics.enableClusterQueueResources: true`; image by digest).
- v0.20 facts: API `kueue.x-k8s.io/v1beta2` only; metrics HTTPS :8443 with bearer token (TokenReview + SubjectAccessReview, ClusterRole `kueue-metrics-reader`); self-signed metrics cert regenerated at start, no CA (upstream `prometheus.yaml` uses `insecureSkipVerify: true`); 11 CRDs, APIService `v1beta2.visibility.kueue.x-k8s.io`, webhooks `failurePolicy: Fail`, pod webhook on every namespace except kube-system and kueue-system; controller requests 500m / 512Mi.
- Job features: Indexed Jobs, `backoffLimitPerIndex` (GA 1.33), `podFailurePolicy` FailIndex and Ignore on `DisruptionTarget` (GA 1.31). Kueue's handling of per-index backoff and FailIndex is unconfirmed: G0 Q2.

## Frozen contract (2A and 2B build against this; G0 may amend it, recorded below)

- Images: `localhost/edp/ai-batch-runner:local`, `localhost/edp/ai-batch-worker:local`, `imagePullPolicy: Never`, loaded with `minikube image load` (`images.txt`).
- Namespace `edp`. Runner Deployment `batch-runner`, Service NodePort 30080 (127.0.0.1), health `/q/health/ready`; ServiceAccount `batch-runner`, Role in `edp`: jobs create/get/list/delete, pods get/list.
- Runner endpoints: `POST /runs` `{"from": ISO, "to": ISO, "priority": "batch-low"|"batch-high"?}` returns 202 `{"id": ...}`, 409 on a duplicate window, 400 on an unknown priority; `POST /runs/{id}/replay` 202 (409 unless the run is FAILED); `GET /runs/{id}` JSON with run and step states.
- Job names `rec-<run>-<step>-a<attempt>`; labels `edp.batch/run`, `edp.batch/step`, `kueue.x-k8s.io/queue-name: reconciliation`, optional `kueue.x-k8s.io/priority-class`. Pod env: `RUN_ID`, `STEP`, `TRACEPARENT`, `JOB_COMPLETION_INDEX` (Indexed), `JOB_INDEX_FAILURE_COUNT` (downward API, annotation `batch.kubernetes.io/job-index-failure-count`), `envFrom` ConfigMap `ai-demo-faults` (optional).
- Fault ConfigMap keys: `FAULT_PARTITION`, `FAULT_MODE` (`once` exits 1 while `JOB_INDEX_FAILURE_COUNT` is 0; `always` exits 42), `SLOW_PARTITION`, `SLOW_SECONDS`.
- Exit codes: 0 ok, 1 transient, 42 poison (FailIndex).
- Steps: `plan` (1 pod, 250m); `reconcile` (Indexed, completions 3, parallelism 3, 500m per pod, `backoffLimitPerIndex: 1`, `maxFailedIndexes: 3`, `podFailurePolicy`: exit 42 FailIndex, `DisruptionTarget` Ignore); `aggregate` (1 pod, 250m). Memory requests set in G0 (Q8).
- Schema `reconciliation` (runner Flyway):
  - `run(id, window_from, window_to, status, priority, created_at, finished_at, traceparent)`, unique partial index on `(window_from, window_to) WHERE status <> 'FAILED'`.
  - `step(run_id, name, ordinal, status PENDING|QUEUED|RUNNING|DONE|FAILED, attempt, job_name, created_at, admitted_at, finished_at)`, PK `(run_id, name)`; `step_job(run_id, name, attempt, job_name)`.
  - `run_partition(run_id, partition, scheduled_from, scheduled_to, delivered_from, delivered_to, status, attempts, records_read, completed_at)`.
  - `partition_result(run_id, partition, carrier, scheduled, delivered, open)`, PK `(run_id, partition, carrier)`, upserted in the transaction that marks the partition DONE.
  - `carrier_total(run_id, carrier, scheduled, delivered, open)`, upserted by aggregate.
- Topics (KafkaTopic): `edp.shipping.scheduled`, `edp.shipping.delivered` (3 partitions each, catalog), `edp.shipping.reconciliations` (new, example-local, key `<runId>:<carrier>`, `cleanup.policy=compact`). Order ids `O-44xx`; out-of-window markers `O-449x`.
- Replay guard: a reconcile index whose `run_partition` row is DONE exits 0 without reading Kafka. Demo switch `REPLAY_GUARD=off` disables it.
- Step mapping: a started Job that turns back to `suspend: true` returns the step to QUEUED (not FAILED) and counts `batch_step_preemptions_total{step}`.
- Runner metrics: `batch_step_duration_milliseconds{step}`, `batch_step_queue_wait_milliseconds{step}`, `batch_step_failures_total`, `batch_step_replays_total`, `batch_step_preemptions_total{step}`, `batch_partition_records_total`.
- Demo switches on verify.sh (env, passed to the runner as config): `QUEUE=off` (no queue label), `REPLAY_GUARD=off`, `PRIORITY=off` (no priority label); optional `FAIL_POLICY=off`.

## Kueue objects

- `ResourceFlavor default-flavor` (no node labels).
- `ClusterQueue reconciliation`: `namespaceSelector` for `edp`; cpu and memory on `default-flavor`, nominal cpu `1750m`, memory `3Gi` (G0); `queueingStrategy: BestEffortFIFO`; no cohort; `preemption: {withinClusterQueue: LowerPriority, reclaimWithinCohort: Never}`.
- `LocalQueue reconciliation` in `edp`.
- `WorkloadPriorityClass batch-low` (100) and `batch-high` (1000). Runs without a priority label keep priority 0.

## Observability

- Traces: runner run span and Camel route spans; `traceparent` stored and injected as env `TRACEPARENT`; worker extracts it with the OTel `TextMapPropagator` over `System.getenv()` (OTel spec, environment variables as context carriers). One trace per run.
- Logs: OTel logs from both services into Loki with trace ids.
- Metrics from the long-lived runner, not the pods.
- Kueue scrape: second collector job against `https://kueue-controller-manager-metrics-service.kueue-system.svc:8443/metrics`, `authorization.credentials_file` = lgtm SA token (ClusterRoleBinding to `kueue-metrics-reader`), `tls_config.insecure_skip_verify: true`. Metrics: `kueue_pending_workloads`, `kueue_admitted_active_workloads`, `kueue_admission_wait_time_seconds`, `kueue_cluster_queue_resource_usage`, `kueue_cluster_queue_nominal_quota`, `kueue_preempted_workloads_total`, `kueue_evicted_workloads_total`.
- Dashboard `edp-batch`.

## verify.sh checks (about 34; G0 freezes numbering)

Preconditions: 1 Kueue v0.20.6 Available, image digest equals the pin. 2 webhook live (server-side dry-run Job with the queue label comes back `suspend: true`). 3 ClusterQueue and LocalQueue Active. 4 input in place, 3 partitions per topic, co-partitioned by order id.

Steps: 5 `POST /runs` W1 202, second W1 409. 6 steps in order (reconcile created after plan's completionTime, aggregate after reconcile's). 7 3 `run_partition` rows with offsets equal to `kafka-get-offsets.sh --time` for T0 and T1. 8 every step Job carried the queue label and owns a Workload Admitted then Finished.

Fan-out and aggregate: 9 reconcile Indexed, `completedIndexes "0-2"`, 3 pods, partition equals index, `records_read` sums to the produced in-window count. 10 out-of-window `O-449x` in no count. 11 `carrier_total` equals expected for FASTLINE and AIRCO. 12 output topic holds exactly 2 records for R1, values equal the table.

Retry and replay: 13 `fault=once` on partition 1: Job Complete, index 1 had 2 pods, 0 and 2 had 1. 14 partition 1 `attempts` 2, others 1, W2 totals correct. 15 `fault=always` (exit 42): Job Failed, `failedIndexes "1"`, `completedIndexes "0,2"`, index 1 had 1 pod. 16 step and run FAILED, no aggregate Job, no `carrier_total` or output for R3. 17 replay 202, plan Job count for R3 stays 1. 18 replay Job: partitions 0 and 2 unchanged and logged as skipped, 1 processed. 19 aggregate ran, W3 totals correct, 2 output records for R3.

Admission and queueing: 20 R4 and R5 back to back; while R4 reconcile runs, R5 reconcile Workload `QuotaReserved=False`, Job suspended, 0 pods. 21 `kueue_pending_workloads{cluster_queue="reconciliation"}` >= 1 during the wait. 22 max overlap of batch pod CPU requests <= 1750m while summed demand was 3000m. 23 R5 reconcile started after R4's finished; both DONE with correct totals.

Priority and preemption: 24 precondition: R6 (`batch-low`, `SLOW_PARTITION=2`) reconcile admitted, partitions 0 and 1 DONE, index 2 running before R7 is submitted. 25 R6 Workload `Evicted` reason `Preempted` (and `Preempted` with `InClusterQueue`); R6 Job `suspend: true`, 0 active pods; R6 reconcile step QUEUED, never FAILED. 26 R7 (`batch-high`) reconcile admitted while R6 suspended, completes before R6's resumed index-2 pod starts. 27 `kueue_preempted_workloads_total{preempting_cluster_queue="reconciliation"}` >= 1, `kueue_evicted_workloads_total{cluster_queue="reconciliation",reason="Preempted"}` >= 1, runner `batch_step_preemptions_total{step="reconcile"}` = 1. 28 after resume: `completedIndexes` kept, only index 2 got a new pod, partitions 0 and 1 `attempts` 1 and `completed_at` unchanged, Job `status.failed` 0, index 2 failure count 0, Job Complete. 29 R6 and R7 DONE with correct totals, 2 output records per run.

Observability: 30 Tempo holds one R1 trace with spans from `ai-batch-runner` and `ai-batch-worker`, including 3 reconcile index spans. 31 Loki holds the exit-42 line for R3 index 1 with a trace id. 32 runner metrics for all 3 steps, failures >= 1, replays = 1. 33 `kueue_admission_wait_time_seconds_count{cluster_queue="reconciliation"}` >= number of step Jobs. 34 Grafana serves `edp-batch`.

Teardown (`kueue_teardown` then `k8s_teardown`) fails loudly if any Kueue CRD, webhook configuration, APIService, ClusterQueue or WorkloadPriorityClass remains. Order: Jobs, Workloads gone, LocalQueue, ClusterQueue, WorkloadPriorityClasses, ResourceFlavor, then the manifest. Finalizers are never stripped.

Negative witnesses (failing sets frozen in the window, stated in the README): `QUEUE=off` (expected 8, 20, 21, 22, 23); `REPLAY_GUARD=off` (expected 18 only; totals still pass: the upsert lesson); `PRIORITY=off` (expected 25, 26, 27, 28); optional `FAIL_POLICY=off` (expected 15).

## Figures (diagrams/specs/ai-*.py, 8)

1. `ai-reconciliation-run`: runner (Process Manager) to plan Job to reconcile Indexed Job (index i to partition i of both topics) to aggregate Job to `edp.shipping.reconciliations`; CloudNativePG tables beside each step.
2. `ai-step-states`: PENDING, QUEUED (suspended), RUNNING, DONE/FAILED; replay and preemption back to QUEUED.
3. `ai-co-partitioned-fan-out`: scheduled and delivered partitions 0 to 2 keyed by order id feeding indexes 0 to 2.
4. `ai-replay-failed-index`: attempt 1 (0 done, 1 FailIndex, 2 done); replay Job (0 skip, 1 work, 2 skip); then aggregate.
5. `ai-kueue-objects`: ResourceFlavor, ClusterQueue (quota, preemption), LocalQueue (`edp`), Job (queue-name and priority-class labels), Workload (suspended, admitted), WorkloadPriorityClass.
6. `ai-queueing-timeline`: R4 and R5 against the 1750m quota; R5 waits; contrast with `QUEUE=off` above quota.
7. `ai-preemption`: R6 (low) indexes 0 and 1 done, 2 evicted; R7 (high) admitted and done; R6 resumed with index 2 only.
8. `ai-observability-path`: worker pods push traces and logs over OTLP at exit; runner pushes metrics; collector scrapes Kueue with a token; all into otel-lgtm and the dashboard.

Plus `ad-pattern-map` gains the Appendix I patterns.

## Appendix I sections (no word cap; never padded)

Intro (bounded input and a "done" moment); `## Pattern: Step-wise batch with persisted step state`; `## Pattern: Fan-out over partitions`; `## Pattern: Aggregate on completion`; `## Pattern: Replay failed steps only` (incl. the full-quota cost of a replay Job); `## Pattern: Admission control with Kueue` (ResourceFlavor, ClusterQueue, LocalQueue, Workload, suspend, BestEffortFIFO vs StrictFIFO; subsection on priority and preemption, run live; cohorts, borrowing, partial admission from sources); `## Observing a batch run`; `## Installing and removing Kueue`; `## Build, run, observe` (about 250 words, at most 3 Try bullets: `QUEUE=off`, `REPLAY_GUARD=off` or `PRIORITY=off`, the fault ConfigMap); `## When not to use it` (stream that never ends, a small JVM job: Jakarta Batch or a scheduled Camel route, JobSet for gang start, Argo for a DAG UI, MultiKueue across clusters); `## References`. Exactly one "Explore with your agent" callout naming `batch-runner`, `run_partition` and the replay rule.

## Steps

| Step | Work | Files | Depends | Owner |
|---|---|---|---|---|
| 0 | branch, plan, iteration-plan row, nothing running, host_ids | `_plans/r16-plan.md`, `_plans/iteration-plan.md` | owner OK | orchestrator |
| 1A | sources and pins | `_plans/versions.md`, `_plans/sources.md`, `bibliography.md` | 0 | Sonnet |
| 1B | substrate | `examples/_k8s/*` (versions.env, cluster.sh `kueue-manifest`, lib.sh `kueue_fresh`/`kueue_teardown`, platform/kueue/), `.gitignore`, `scripts/validate.py`, `.github/workflows/examples.yml`, `scripts/verify-all.sh` | 0 | Sonnet (parallel with 1A) |
| G0 | spike on a fresh `edp` profile, about 90 min, results in scratchpad, decisions appended here, profile deleted | scratchpad | 1B | Sonnet |
| 2A | Java modules, Dev Services tests, Containerfiles | `examples/ai-*/pom.xml`, `batch-runner/**`, `batch-worker/**` | G0 | Sonnet |
| 2B | manifests, kueue/, topics, monitoring, dashboard, producer Job, verify.sh, README, images.txt | `examples/ai-*/{kustomization.yaml,apps,kueue,topics,monitoring,tools,verify.sh,README.md,images.txt}` | G0 | Sonnet |
| 2C | appendix prose, example page, 8 figures | `_docs/ai-*.md`, `_example_pages/ai-*.md`, `diagrams/specs/ai-*.py`, `diagrams/ai-*` | G0 | Sonnet |
| 2D | glossary, pattern map, README list, part blurb, domain-model note, CLAUDE.md draft | `_docs/ad-*.md`, `diagrams/specs/ad-pattern-map.py` + outputs, `README.md`, `_parts/07-appendices.md`, `_plans/domain-model.md` | G0 | orchestrator |
| 3 | live window: fresh profile, mvn verify, verify.sh N/N, witnesses once each, teardown | logs, `claims.md`, `verification-log.md` | 2A-2D | orchestrator |
| 4 | fixes, affected checks re-run | example files | 3 | orchestrator |
| 5 | deck: 3 slides in section 08 (figures 1, 4, 5) | `lgtm-presentation/**`, `_plans/deck-evidence.md` | 3 | Sonnet |
| 6 | final fresh run on a recreated profile, gates, claims, commits | | 4, 5 | orchestrator |
| 7 | Opus validation, at most 2 repair rounds | | 6 | Opus |
| 8 | PR, CI, merge, tag r16.0, release, Notion PRD (appendix row I, Kueue stack row, roadmap r16) | | 7 | orchestrator |

validate.py changes (1B): `EXAMPLE_DIR_RE` and globs `a[a-i]`; `XREF_RE`/`XREF_WORD_RE` `[A-I]`; word-cap exemption for `_docs/ai-*` (Build, run, observe soft cap kept); an `ai-` branch requiring `k8s_fresh`, `k8s_teardown`, `kueue_fresh`, `kueue_teardown`; `VENDOR_RE` adds the Kueue vendor manifest; self-tests for each.

G0 questions: Q1 install time, webhook readiness, teardown leaves nothing (twice), namespace deletion not blocked by the APIService. Q2 Indexed Job with `backoffLimitPerIndex` and FailIndex under Kueue. Q3 contention: pending reason text, metric names and labels, BestEffortFIFO order. Q4 Kueue scrape with token and skip-verify; `enableClusterQueueResources`. Q5 Camel `kubernetes-job` createJob/getJob in-cluster and RBAC. Q6 `TRACEPARENT` propagation and OTLP flush from a command-mode pod. Q7 `offsetsForTimes` boundaries. Q8 node headroom under quota 1750m; wall clock. Q9 collector config merge (overlay or second `--config`). P1 suspend keeps `completedIndexes`, resume starts only missing indexes. P2 failure counts after preemption; `DisruptionTarget` on the deleted pod. P3 exact condition types, reasons, metric names. P4 eviction and re-admission latency. P5 worker termination grace and rollback. P6 runner QUEUED mapping on re-suspend.

## Acceptance criteria

- verify.sh N/N with N >= 30 on two fresh runs (window run and final run on a recreated profile).
- `QUEUE=off`, `REPLAY_GUARD=off` and `PRIORITY=off` each fail exactly their frozen sets.
- `mvn -B verify` passes for both modules (CI too), with a mocked-endpoint runner test and a worker idempotency test.
- Kueue from the sha256-checked v0.20.6 manifest, server-side, image by digest.
- After teardown: no Kueue CRD, webhook configuration, APIService, WorkloadPriorityClass or `kueue-system` namespace; profile deleted at the end.
- No port-forward or tunnel; NodePorts on 127.0.0.1; nothing pushed to an external registry.
- Appendix: `order: 44`, marker I; 5 `## Pattern:` sections plus topical sections; one callout; Build, run, observe about 250 words, at most 3 Try bullets, no check numbers; no dashes, cross-references in prose, test names or banned domain words; 8 figures from specs; every claim has a `claims.md` row; cohorts, borrowing and partial admission only from fetched sources.
- Gates: `validate.py` (new self-tests), `voice-scan.sh`, `idiomatic-scan.sh`, `host_ids.py check`, `site-build.sh`, CI incl. the deck drift check.
- Earlier examples unchanged: `git diff --stat main -- examples/[0-9]* examples/a[a-h]-*` empty; `examples/_k8s` changes additive.
- Deck 123 slides, notes floors met, `deck_text --check` clean.

## Risks

| Risk | Detection | Response |
|---|---|---|
| Kueue pod webhook (`failurePolicy: Fail`) blocks pods cluster-wide when the controller is down or half removed | G0 Q1; teardown asserts | install after `k8s_fresh`; `kueue_teardown` before `k8s_teardown`, fails loudly |
| Dangling APIService stalls namespace deletion | G0 Q1 | delete with the manifest after the CRs; assert gone |
| Finalizers hang CRD deletion if the controller goes first | teardown timeout | ordered teardown; never strip finalizers |
| Kueue mishandles per-index backoff or FailIndex | G0 Q2 | measurement wins; fall back to step-level replay and say so |
| Pods admitted but Pending on node capacity | G0 Q8; check 9 | lower requests or raise `MK_MEMORY`; quota is not capacity |
| Short-lived pods lose telemetry | G0 Q6; checks 30, 31 | metrics from the runner; spans and logs flush on shutdown |
| `offsetsForTimes` off by one | check 10 | 2 s producer gaps around T0 and T1 |
| Overlap check 22 vacuous | check 20 precondition | `QUEUE=off` must fail it |
| Preemption timing flaky (index 2 finishes before R7 arrives) | check 24 precondition | `SLOW_PARTITION` long enough; precondition fails loudly rather than passing vacuously |
| Window over budget | timestamps | G0 about 90 min, window about 80, final about 30 |
| Banned domain word | validate.py | "reconciliation" only |
| `XREF_RE` `[A-I]` flags existing text such as "part I" | validate.py in 1B | fix any hit at once |

## G0 decisions (spike 2026-11-18, Kueue v0.20.6 on Kubernetes v1.37.2; evidence kept in the session scratchpad)

Confirmed as planned: per-index retry (index 1 gets 2 pods, `job-index-failure-count` 0 then 1, downward API works); exit 42 FailIndex (`failedIndexes "1"`, `completedIndexes "0,2"`, one pod, other indexes finish; Workload `Finished=True` reason `Failed`, quota released); BestEffortFIFO lets a 250m Job pass; token + `insecure_skip_verify` scrape (`up{job="kueue"}=1`); `enableClusterQueueResources` produces usage and nominal quota series; preemption keeps `completedIndexes` and Completed pods and starts only the missing index on re-admission; dry-run probe returns `suspend: true` with or without a LocalQueue.

Amendments to the frozen contract and checks:

- `completedIndexes` prints a list for two indexes ("0,1", not "0-1"); three contiguous print "0-2". Check 24 uses "0,1".
- Pending Workload: `QuotaReserved=False` reason `WaitingForQuota`, message "couldn't assign flavors to pod set main: insufficient unused quota for cpu in flavor default-flavor, <n> more needed"; `Admitted=False` reason `NoReservation`. Check 20 asserts these.
- Preempted Workload: `Evicted=True` reason `Preempted`, `Preempted=True` reason `InClusterQueue`, `Requeued=True` reason `Preempted`, `QuotaReserved=False` reason `WaitingForQuota`; after re-admission `Evicted` and `Preempted` flip to False reason `QuotaReserved`. Suspension within 0.09 s of the high submit; high submit to low resumed 15.6 s. Kueue reserves quota for the whole pod set (1500m with one pod left), so preemption triggers regardless. The evicted pod stays Terminating for its grace period and overlaps the high Job's pods: check 22's overlap rule must exclude Terminating time or apply only to the R4/R5 window.
- After preemption `status.failed` 0 and failure count 0 on every pod; the deleted pod had **no** `DisruptionTarget` condition. Prose must not claim the Ignore rule was exercised; it is kept as a guard for evictions and stated as such.
- Metrics (exact): `kueue_pending_workloads{cluster_queue,status=active|inadmissible}`, `kueue_admitted_active_workloads{cluster_queue}`, `kueue_admitted_workloads_total{cluster_queue}`, `kueue_admission_wait_time_seconds_{bucket,sum,count}{cluster_queue}`, `kueue_cluster_queue_resource_usage{cluster_queue,flavor,resource}`, `kueue_cluster_queue_nominal_quota{cluster_queue,flavor,resource}` (cpu in cores, memory in bytes), `kueue_preempted_workloads_total{preempting_cluster_queue,reason="InClusterQueue"}` (no `cluster_queue` label), `kueue_evicted_workloads_total{cluster_queue,priority_class,reason="Preempted"}`. Preempted and evicted series do not exist before the first event. Series lag one 10 s scrape; Kueue series appear about 45 s after the lgtm pod starts (verify.sh waits for `up{job="kueue"}=1` before check 21).
- Offsets by time: first offset with timestamp >= T (T equal to a record's timestamp returns it). A partition whose last timestamp is below T is omitted by `kafka-get-offsets.sh --time` and `offsetsForTimes` returns null: the worker falls back to the end offset; check 7 handles the omitted partition the same way.
- Resources: ClusterQueue memory quota **3Gi** (cpu stays 1750m). Requests: reconcile pods 500m / 384Mi (limit 512Mi), plan and aggregate 250m / 256Mi. JVM sizing unconfirmed (spike used busybox): 2A sets `-XX:MaxRAMPercentage` and confirms in the window.
- Scheduler sees host allocatable, not the profile size; only the 10 GiB memory cgroup is enforced. Quota, not node capacity, is what limits the batch.
- Every batch Job carries `edp.batch/run` (an unlabelled Job leaves a Workload and breaks the teardown wait).
- Teardown: the APIService blocks namespace deletion when the controller goes first (Workload finalizer plus `NamespaceDeletionDiscoveryFailure`); order stays content first, controller last. `kueue_teardown` now skips `delete -k` when nothing is installed (1 s no-op instead of 17 s).
- Collector: a second ConfigMap with a `prometheus/kueue` receiver and `metrics/kueue` pipeline; the lgtm Deployment's `OTELCOL_EXTRA_ARGS` holds two `--config=file:...` flags (the collector merges them); the env patch by name replaces the monitoring component's value; apply from one overlay to avoid field-manager conflicts. Dashboard: ConfigMap plus a provider file mounted at `dashboards/edp-batch.yaml` (subPath) with path `/prov/batch`; `/api/dashboards/uid/edp-batch` returns 200.
- Kueue controller `imagePullPolicy: Always` kept (digest check 0.56 s).
- Timings: `k8s_fresh` 115 s cold, `kueue_fresh` 13 to 31 s, `k8s_teardown` 35 s.
- Not measured in G0, settled in 2A tests and the window: Q5 Camel `kubernetes-job` in-cluster and RBAC, Q6 `TRACEPARENT` and OTLP flush from a command-mode pod, P5 worker termination and rollback, P6 runner QUEUED mapping on re-suspend.
