# Appendix I: Batch processing with Kueue

An end-of-day delivery reconciliation per carrier (FASTLINE and AIRCO) over a time window: the ShipmentScheduled and ShipmentDelivered records of the window are counted per carrier, and the shipments still open are reported. A small Process Manager, the `batch-runner`, runs each reconciliation as three Kubernetes Jobs (`plan`, an Indexed `reconcile` with one index per partition, `aggregate`), and Kueue admits each Job against a quota.

The example has two Quarkus modules (`batch-runner`, `batch-worker`) and the manifests, queues, monitoring and `verify.sh` in this directory. Order ids are `O-44xx`; the markers outside every window are `O-449x`.

## Prerequisites

The Part 6 substrate: minikube profile `edp` from `examples/_k8s/cluster.sh up`, Docker Engine, `kubectl`. `verify.sh` installs Kueue v0.20.6 itself, with `kueue_fresh` after `k8s_fresh`, and removes it with `kueue_teardown` before `k8s_teardown`; `cluster.sh` never installs it. `mvn -B -q install` in `examples/domain-model` comes first if you build the modules by hand.

## What runs

| Namespace | Holds |
|---|---|
| `edp-kafka` | Strimzi operator, Kafka `edp`, the KafkaTopics `edp.shipping.scheduled`, `edp.shipping.delivered` (3 partitions) and `edp.shipping.reconciliations` (compacted) |
| `edp` | CloudNativePG `Cluster edp-pg` (schema `reconciliation`), the `batch-runner` (NodePort 30080 on 127.0.0.1), the Jobs of each run, LocalQueue `reconciliation` |
| `edp-lgtm` | otel-lgtm with a second collector configuration that scrapes Kueue, and the dashboard `edp-batch` |
| `kueue-system` | Kueue, installed by `verify.sh` |

Nothing is forwarded: the runner, Prometheus, Tempo, Loki and Grafana are reached through the published NodePorts, everything else with `kubectl exec`.

## Layout

| Path | Purpose |
|---|---|
| `kustomization.yaml` | The shared bases and monitoring component, `apps/`, `topics/` and the Kueue scrape |
| `apps/` | `batch-runner` Deployment (`imagePullPolicy: Never`), Service, ServiceAccount and a Role for Jobs and pods |
| `topics/` | The three KafkaTopics and the Topic Operator switch |
| `kueue/` | ResourceFlavor `default-flavor`, ClusterQueue `reconciliation` (1750m, 3Gi, BestEffortFIFO, preemption by priority within the queue), LocalQueue, WorkloadPriorityClasses `batch-low` (100) and `batch-high` (1000); applied by `verify.sh` |
| `monitoring/` | Collector configuration for Kueue (bearer token, certificate check off), the binding to `kueue-metrics-reader`, the dashboard `edp-batch` |
| `tools/dataset.txt` | The one definition of the input: windows `W1` to `W7`, markers `M1` to `M8`, order, carrier and delivered flag |
| `tools/producer-job.yaml` | Produces the dataset in real time, with 2 s gaps around every window boundary, and logs the boundaries |
| `tools/batchcheck.py` | Expected counts from the dataset and JSON checks for `verify.sh` (`python3 -I`) |
| `images.txt` | The two images `verify.sh` builds and loads |

## Run

```bash
examples/_k8s/cluster.sh up
cd examples/ai-batch-processing-with-kueue
./verify.sh
KEEP=1 ./verify.sh   # leave Kueue and the namespaces in place
```

`verify.sh` starts from fresh namespaces and a fresh Kueue every time and ends with `34/34 checks passed`.

## Checks

Checks 1 to 4 and 24 are preconditions and stop the run when they fail.

| Check | Description |
|---|---|
| 1 | Kueue is Available and runs the pinned image digest |
| 2 | the webhook suspends a Job that carries the queue label |
| 3 | ClusterQueue and LocalQueue are Active |
| 4 | both input topics: 3 partitions, keys placed alike, each window holds exactly the dataset's records |
| 5 | `POST /runs` for window 1 answers 202 with an id, the same window again 409 |
| 6 | reconcile created after plan completed, aggregate after reconcile completed |
| 7 | the 3 `run_partition` rows hold the offsets `kafka-get-offsets.sh` returns at the window's start and end |
| 8 | every step Job had the queue label and owns a Workload that was Admitted, then Finished |
| 9 | reconcile is an Indexed Job, `completedIndexes` `0-2`, one pod per index, each partition read what the dataset puts in it |
| 10 | the `O-449x` markers are on the topic and in no count |
| 11 | `carrier_total` equals the dataset's counts for both carriers |
| 12 | the output topic holds exactly 2 records for the run, equal to the totals |
| 13 | fault once on partition 1: Job Complete, index 1 in 2 pods, indexes 0 and 2 in 1 |
| 14 | partition 1 has 2 attempts, the others 1, totals right |
| 15 | fault always on partition 1 (exit 42): Job Failed, `failedIndexes` `1`, `completedIndexes` `0,2`, index 1 in 1 pod |
| 16 | step and run FAILED, no aggregate Job, no `carrier_total`, no output record |
| 17 | replay answers 202, the run keeps its one plan Job |
| 18 | the replay Job leaves partitions 0 and 2 unchanged and logged as skipped, and processes partition 1 |
| 19 | aggregate ran after the replay, totals right, 2 output records |
| 20 | one reconcile Workload waits (`WaitingForQuota`, `NoReservation`), its Job suspended with no pod, the other admitted |
| 21 | `kueue_pending_workloads` is at least 1 during the wait |
| 22 | batch pods never requested more than 1750m at once, while the two reconcile steps asked for 3000m |
| 23 | the waiting reconcile step started after the other finished; both runs DONE with right totals |
| 24 | run 6 (`batch-low`) holds the quota, partitions 0 and 1 done, index 2 running |
| 25 | run 6's Workload is Evicted (`Preempted`) and Preempted (`InClusterQueue`), its Job suspended with no active pod, its step QUEUED not FAILED |
| 26 | run 7 (`batch-high`) reconcile admitted while run 6 was suspended, finished before run 6's resumed pod started |
| 27 | `kueue_preempted_workloads_total` and `kueue_evicted_workloads_total` at least 1, runner `batch_step_preemptions_total{step="reconcile"}` 1 |
| 28 | after the resume `completedIndexes` kept, only index 2 got a new pod, partitions 0 and 1 not read again, no failure counted |
| 29 | runs 6 and 7 DONE, right totals, 2 output records each |
| 30 | Tempo: one trace of run 1 with spans of both services and 3 reconcile spans |
| 31 | Loki: the exit 42 line of run 3 with its trace id |
| 32 | runner metrics: a duration for each step, failures at least 1, replays 1 |
| 33 | `kueue_admission_wait_time_seconds_count` at least the number of step Jobs |
| 34 | Grafana serves `edp-batch` |

## Experiments

Demo code, set on the runner Deployment by `verify.sh`; each run starts fresh.

| Switch | Effect | Expected failing checks |
|---|---|---|
| `QUEUE=off ./verify.sh` | Jobs carry no queue label, Kueue never sees them; the run stops after check 23 | 8, 20, 21, 22, 23 |
| `REPLAY_GUARD=off ./verify.sh` | the worker reads a partition again although it is DONE; the totals stay right because the upsert is idempotent | 18 |
| `PRIORITY=off ./verify.sh` | run 7 carries no priority class and waits instead of preempting | 25, 26, 27, 28 |
| `FAIL_POLICY=off ./verify.sh` | the Job has no `FailIndex` rule, so exit 42 is retried | 15 |

The failing sets are frozen after the live window.

## Teardown

`verify.sh` ends with `kueue_teardown` (Jobs, Workloads, queues, priority classes, flavor, then the Kueue manifest) and `k8s_teardown`, and fails loudly when a Kueue CRD, webhook configuration, APIService or `kueue-system` remains. Finalizers are never stripped. `examples/_k8s/cluster.sh down` deletes the profile.
