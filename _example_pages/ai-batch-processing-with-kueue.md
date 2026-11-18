---
title: "Batch processing with Kueue"
order: 44
permalink: /examples/ai-batch-processing-with-kueue/
description: "An end-of-day delivery reconciliation per carrier, run as three Kubernetes Jobs admitted by Kueue, with a failed partition replayed alone and a low-priority run preempted by a high-priority one."
---

[`examples/ai-batch-processing-with-kueue/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/ai-batch-processing-with-kueue) reconciles the scheduled and delivered shipment events of a time window per carrier. It accompanies [Appendix I]({{ '/docs/ai-batch-processing-with-kueue/' | relative_url }}).

A Quarkus and Camel `batch-runner` keeps the state of each run in PostgreSQL and creates the Job for each step. A Quarkus command-mode `batch-worker` image runs the three steps: `plan`, `reconcile` and `aggregate`. Kueue is installed and removed by `verify.sh` itself.

```bash
examples/_k8s/cluster.sh up
cd examples/ai-batch-processing-with-kueue
./verify.sh
```

{% include excalidraw.html file="ai-replay-failed-index" alt="Attempt 1 has index 0 and 2 done and index 1 failed with exit 42; the replay Job's index 0 and 2 see their run_partition rows DONE and exit 0 without reading Kafka, index 1 reads partition 1 and becomes DONE, and aggregate then runs once" caption="A replay Job runs all indexes, and the finished ones exit at once" %}

## What to observe

1. **A run in three Jobs.** The `reconcile` Job is Indexed with three pods, one per partition, and the totals per carrier equal the in-window counts.
2. **A retried and a poisoned partition.** A transient fault gives one index a second pod; exit 42 fails only that index and leaves `completedIndexes` at `0,2`.
3. **A replay.** The finished partitions keep their completion time and only the failed one is read.
4. **Two runs, one quota.** The second `reconcile` Workload waits with the reason `WaitingForQuota` and its Job has no pods.
5. **Preemption.** A high-priority run evicts a low-priority one, which resumes with only its missing index.

## Infrastructure

Strimzi, Kafka, CloudNativePG and the observability stack from `examples/_k8s` on the `edp` minikube profile, Kueue v0.20.6, the runner on NodePort 30080, and a producer Job that writes the input.
