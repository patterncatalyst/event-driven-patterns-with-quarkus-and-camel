---
title: "Cruise Control: rebalancing"
order: 33
part: running-on-kubernetes-with-strimzi
permalink: /docs/33-cruise-control/
description: "Kafka places a partition when it is created and never moves it, so a broker added to a busy cluster stays empty and a broker that holds replicas cannot be removed. Cruise Control computes a plan from the load, a KafkaRebalance holds it until it is approved, and autoRebalance lets the operator ask and approve when a node pool grows or shrinks."
duration: "60 minutes"
---

A cluster that is running out of room gets another broker. In Kubernetes that is one number in a node pool. The pod starts, joins the cluster, passes its readiness probe and holds nothing. Every existing partition still lives where it was created, and the new broker takes only the partitions that are created after it arrives. The capacity was added and none of the load moved to it.

The reverse is worse. A broker that holds replicas cannot simply be deleted, because the replicas on it would go with it. Both directions need the same operation: choose which replicas go where, and move them while clients keep working.

{% include excalidraw.html file="33-rebalance-states" alt="A KafkaRebalance goes from PendingProposal to ProposalReady, where the optimization result is shown and nothing has moved, then on approval to Rebalancing and Ready; a refresh annotation computes a new plan, and an auto-approval annotation or the operator's autoRebalance replaces the manual approval" caption="Figure 33.1: A proposal is a plan, and it waits for approval before anything moves" %}

## Pattern: Rebalance by proposal

### The problem

Placement is decided once. Kafka assigns the replicas of a partition when the topic is created, and the cluster does not revisit the choice when brokers arrive or leave. A new broker stays empty until a plan moves replicas to it, and a plan written by hand has to know the size and traffic of every partition to be any better than a guess. Moving the replicas also copies data, so a plan that is applied without being read can saturate the disks and the network of a cluster that is serving traffic.

### The solution

**Ask Cruise Control for a proposal, read what it would move, approve it only when the plan is acceptable, and let the operator ask and approve for the two cases it can recognise, a node pool that grows and one that shrinks.**

Cruise Control keeps a model of the cluster built from partition load, in the words of its documentation, "standard Kafka metrics" that its load monitor turns into per-partition resource use (Cruise Control wiki). A proposal is the smallest set of movements that brings that model within its goals.

### How Strimzi and Cruise Control model it

Cruise Control is a section of the `Kafka` resource. The operator runs it as a Deployment next to the brokers and installs its metrics reporter in them.

```yaml
spec:
  cruiseControl:
    config:
      metric.sampling.interval.ms: 60000
      partition.metrics.window.ms: 60000
      num.partition.metrics.windows: 3
      min.samples.per.partition.metrics.window: 1
```

The sampling keys set how soon Cruise Control has enough windows of samples to trust its model. The interval has a lower bound: ten seconds makes the Cruise Control pod crash with a `ConfigException`, because it must be longer than the metadata refresh timeout, and the `Kafka` resource then never becomes Ready (measured, TBD(spike: interval 60000 works)). Chapter 28 introduced the node pools this cluster uses, and chapter 29 the `KafkaTopic` resources whose replicas are being placed. The catalog topics here have two replicas each, on a pool of two brokers.

A `KafkaRebalance` asks for a plan. Its `mode` names the case, and the label binds it to a cluster.

```yaml
apiVersion: kafka.strimzi.io/v1
kind: KafkaRebalance
metadata:
  name: add-broker-3
  labels:
    strimzi.io/cluster: edp
spec:
  mode: add-brokers
  brokers: [3]
```

Which id to write is not obvious. Node ids are shared by all pools of a cluster, and the controller of this cluster already holds one, so a pool that grows from two brokers to three gets the next free id, not the third number of a sequence. The id comes from `status.nodeIds` of the pool, which is also what the verification reads.

## A proposal, then an approval

The operator passes the request to Cruise Control and writes the result into the resource: a condition of `ProposalReady` and an `optimizationResult`.

```yaml
status:
  conditions:
    - type: ProposalReady
  optimizationResult:
    numReplicaMovements: 50
    dataToMoveMB: 182
    numLeaderMovements: 0
    onDemandBalancednessScoreBefore: 79.77
    onDemandBalancednessScoreAfter: 85.69
```

The numbers are what the proposal costs and buys: how many replicas it moves, how much data that copies, and how balanced the cluster is before and after (TBD(spike: 50 replica movements, 182 MB, score 79.77 to 85.69)). A proposal with a large `dataToMoveMB` is the signal to wait for a quiet hour. The replicas stay where they are at this point. The proposal is a status, and nothing is applied until the resource carries the approval.

```bash
kubectl -n edp-kafka annotate kafkarebalance add-broker-3 strimzi.io/rebalance=approve
```

The state moves to `Rebalancing`, then `Ready`, and the new broker holds replicas. The Strimzi documentation names the same states and the `refresh` annotation that discards a stale plan and computes a new one. Approval is a human decision by default. The annotation `strimzi.io/rebalance-auto-approval: "true"` on the resource removes it, and that is the exact choice a team makes when it trusts the goals more than it trusts a person at three in the morning.

The state changes on the operator's reconciliation, which by default runs every two minutes (Strimzi documentation), so a rebalance of this small cluster takes minutes from the approval to `Ready` (TBD(spike: 157 s)).

Two things in this sequence need care. A `KafkaRebalance` applied straight after the pool grew went `NotReady` with a `NullPointerException` from Cruise Control, because Cruise Control restarts with the new broker's capacity after a scale and the request arrived before it was back. Waiting for its Deployment and then annotating the resource with `refresh` gives the plan. And a producer tool run with `kubectl exec` inside a broker container with a 768 MiB limit shares that limit, so the load that fills the topics comes from a Job pod of its own.

## Rebalancing when the pool grows

Nothing in the manual path is wrong, and each step is one the operator can take itself. The `autoRebalance` section of the `Kafka` resource names a template for each mode.

```yaml
spec:
  cruiseControl:
    autoRebalance:
      - mode: add-brokers
        template:
          name: edp-add
      - mode: remove-brokers
        template:
          name: edp-remove
```

A template is a `KafkaRebalance` with the annotation `strimzi.io/rebalance-template: "true"` and, as the Strimzi blog on auto-rebalancing explains, its `mode` and `brokers` are ignored: the operator fills them in. The trigger is a change of `spec.replicas` of an existing node pool. Strimzi creates a resource named after the cluster and the mode, `edp-auto-rebalancing-add-brokers`, requests the proposal, approves it and applies it. The progress is in `status.autoRebalance` of the `Kafka` resource, which reports `RebalanceOnScaleUp` and then `Idle`, and the generated `KafkaRebalance` is deleted when it is done (measured).

The order of configuration matters for a first look. With `autoRebalance` present, the pool that grows is rebalanced at once, and no empty broker is left to propose against. The manual rebalance therefore runs on a pool that has no `autoRebalance` block, and the block is added afterwards. A pool that is created is not a scaling event: the documentation says auto-rebalancing is not triggered when a node pool is created or deleted.

The operator's own rebalance takes the same two-minute steps, one for the proposal and one for the rebalance, so growing from three brokers to four is complete about three and a half minutes after the pod exists (TBD(spike: 3 min 24 s from creation to Ready)).

{% include excalidraw.html file="33-scale-and-move" alt="Replicas per broker in three stages: two brokers that hold every replica, a pool grown to three where the new broker holds none, and after the approved rebalance where the new broker holds a share; the operator's own rebalance does the same when the pool grows once more" caption="Figure 33.2: A broker added to the pool starts empty; replicas arrive only through a rebalance" %}

## Shrinking the pool

A pool that gets smaller loses a broker. Strimzi checks first whether that broker still holds replicas. Without `autoRebalance`, the check fails: the pool's `spec.replicas` is put back to its old value, the pod keeps running and the `Kafka` status carries a warning with the reason `ScaleDownPreventionCheck`. The operator's log names the broker and says it "has assigned partition-replicas". The refusal is the safe outcome, and it is repeated on every reconciliation until someone drains the broker or turns the setting on.

With the `remove-brokers` template configured, the same scale-down is accepted. The operator creates `edp-auto-rebalancing-remove-brokers`, moves the replicas off the broker, and only then does the pod go (TBD(spike: 66 s)). Afterwards every partition has both of its replicas on the remaining brokers and none is out of sync.

| Question | Manual KafkaRebalance | autoRebalance |
|---|---|---|
| Who asks for the proposal | A person, with a resource | The operator, from the template |
| Who approves | A person, by annotation | The operator |
| What triggers it | Any time | A change of replicas in an existing pool |
| Scale-down of a broker with replicas | Refused | Drained, then removed |

## Build, run, observe

```bash
cd examples/33-cruise-control
./verify.sh
```

A cluster of two brokers and Cruise Control is filled by a load Job, grown to three nodes and rebalanced by a proposal that is approved, then grown to four and shrunk back to three with `autoRebalance`; the run ends with `N/N checks passed` and takes TBD minutes, most of it waiting for the operator's two-minute steps.

Try one change and rerun:

- `AUTO_REBALANCE=off ./verify.sh` (demo code): the pool grows to four and the new broker holds no replica, because no resource asks for a plan.
- `kubectl --context edp -n edp-kafka annotate kafkarebalance <name> strimzi.io/rebalance=refresh` while a proposal waits: Cruise Control computes a new plan from the current load.
- Add `strimzi.io/rebalance-auto-approval: "true"` to the manual resource: it goes from `ProposalReady` to `Rebalancing` with no annotation from you.

## When not to use it

A cluster that does not grow, shrink or skew has no use for it, and Cruise Control then costs a Deployment and the minutes it needs to build a model after each restart. Strimzi runs it only when the `Kafka` resource asks.

A rebalance copies data. On a loaded cluster the copy competes with producers and consumers for disks and network, so a large `dataToMoveMB` is a reason to read the proposal, not to approve it. Automatic approval trades that review for speed, and it is a fit for clusters where the goals are trusted and the movement is small.

Auto-rebalancing reacts to a changed replica count in an existing pool and to nothing else. A skewed cluster that did not scale needs a manual `full` rebalance.

> **🤖 Explore with your agent**
> "In examples/33-cruise-control, read status.nodeIds of the broker KafkaNodePool, scale it by one with kubectl patch, and apply a KafkaRebalance with mode add-brokers for the new id. Compare kafka-topics.sh --describe before and after the approve annotation, and say what the optimizationResult predicted, which resource the operator creates instead when autoRebalance is configured, and why scaling down a broker that holds replicas is refused without it."
> Check: the new broker holds no replica until the approve annotation is set, the replica counts then change as numReplicaMovements said, and the refused scale-down shows the reason ScaleDownPreventionCheck on the Kafka resource.

## References

- Strimzi, [Deploying and Managing Strimzi](https://strimzi.io/docs/operators/latest/deploying.html): Cruise Control, auto-rebalancing on scaling, running an optimized cluster rebalance (the `KafkaRebalance` modes, states and annotations)
- Paolo Patierno, [Auto-rebalancing on cluster scaling](https://strimzi.io/blog/2024/11/25/autorebalancing-on-scaling/) (Strimzi blog, 2024)
- LinkedIn, [Cruise Control wiki](https://github.com/linkedin/cruise-control/wiki/Overview): the load monitor and the cluster model
- Strimzi, [CHANGELOG](https://github.com/strimzi/strimzi-kafka-operator/blob/1.3.1/CHANGELOG.md): auto-rebalancing in 0.44, the scale-up fix in 0.51, broker cordoning in 1.2
- Apache Kafka, [KIP-1066: Mechanism to cordon brokers and log directories](https://cwiki.apache.org/confluence/spaces/KAFKA/pages/311627566/KIP-1066+Mechanism+to+cordon+brokers+and+log+directories)
- Chapter 28 for node pools and node ids, chapter 29 for the topics whose replicas move
