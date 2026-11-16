---
title: "MirrorMaker 2"
order: 34
part: running-on-kubernetes-with-strimzi
permalink: /docs/34-mirrormaker-2/
description: "A second cluster with the same topics is of no use to a consumer that does not know where to resume. MirrorMaker 2 copies the topics under their own names and translates each consumer group's committed offsets into the second cluster, so a consumer fails over by changing its bootstrap address."
duration: "50 minutes"
---

The cluster of chapter 28 is one Kafka in one namespace. If it is lost, the orders in it are lost and every service has nowhere to publish. A second cluster that receives a copy of the topics answers the first half of that. It does not say where a consumer should start.

A consumer's position is a committed offset, stored per group in the cluster it reads from. The copy of a topic is another topic in another cluster, and Kafka does not promise that a record has the same offset in both. A consumer that moves with its old position reads the wrong record. One that moves with no position starts at the beginning and processes everything again, or at the end and skips what it had not read.

{% include excalidraw.html file="34-two-clusters" alt="The cluster edp holds the topic edp.orders.placed, the group notification-service and the offset-syncs topic; MirrorMaker 2 workers run a source connector that copies records and a checkpoint connector that translates group offsets; the cluster edp-dr receives the topic under the same name, the group and the checkpoints topic" caption="Figure 34.1: One direction, edp to edp-dr, with the same topic names on both sides" %}

## Pattern: A replicated cluster for failover

### The problem

Copying records is mechanical. Offsets are assigned by the log that stores a record, so the copy has its own, and a group's committed offset in the source says nothing about the copy. Every consumer that moves would have to be told where to start, and the only party that knows how the two logs line up is the one that wrote the copy.

The failover also has to be small. A consumer that must change its topic names, its group or its code to read the second cluster is a consumer that fails over slowly and by hand.

### The solution

**Mirror the topics with MirrorMaker 2, which records how source offsets map to target offsets and writes each consumer group's translated position into the second cluster, so that a consumer moves by changing its bootstrap address and continues where it stopped.**

MirrorMaker 2 is a set of Kafka Connect connectors (KIP-382). A source connector copies records and emits an offset sync for the records it copies. A checkpoint connector reads the syncs and the committed offsets of the source's groups, writes a checkpoint for each, and, when told to, applies the checkpoint to the group on the target (Strimzi, Deploying and Managing Strimzi).

### How Kafka and Strimzi model it

The second cluster is the `Kafka` of chapter 28 under another name, with one node pool whose node is controller and broker at once.

```yaml
apiVersion: kafka.strimzi.io/v1
kind: KafkaNodePool
metadata:
  name: dual
  labels:
    strimzi.io/cluster: edp-dr
spec:
  replicas: 1
  roles: [controller, broker]
```

The mirror is one `KafkaMirrorMaker2`. Its workers run against the target cluster and keep their configuration, offsets and status in topics there. One entry of `mirrors` names a source cluster and configures the two connectors.

```yaml
apiVersion: kafka.strimzi.io/v1
kind: KafkaMirrorMaker2
metadata:
  name: edp-mm2
spec:
  replicas: 1
  target:
    alias: edp-dr
    bootstrapServers: edp-dr-kafka-bootstrap.edp-kafka.svc:9092
    groupId: edp-mm2
    configStorageTopic: mm2-configs
    offsetStorageTopic: mm2-offsets
    statusStorageTopic: mm2-status
  mirrors:
    - source:
        alias: edp
        bootstrapServers: edp-kafka-bootstrap.edp-kafka.svc:9092
      topicsPattern: "edp\\..*"
      groupsPattern: ".*"
      sourceConnector:
        config:
          replication.policy.class: org.apache.kafka.connect.mirror.IdentityReplicationPolicy
          offset.lag.max: 0
      checkpointConnector:
        config:
          replication.policy.class: org.apache.kafka.connect.mirror.IdentityReplicationPolicy
          sync.group.offsets.enabled: "true"
          sync.group.offsets.interval.seconds: 5
          emit.checkpoints.interval.seconds: 5
```

The `v1` resource has no heartbeat connector; a heartbeat is a separate `KafkaConnect` and `KafkaConnector` (Strimzi changelog). The replication factors and refresh intervals of a one-node laptop cluster are left out above. The topics pattern is the event catalog's prefix, so the mirror copies the topics of the catalog and nothing else.

## Topics and their copies

By default MirrorMaker 2 renames a replicated topic by prepending the source cluster's alias, so `edp.orders.placed` from the cluster `edp` would be `edp.edp.orders.placed` on the target (Kafka geo-replication documentation). A consumer that reads the copy then needs a new topic name as well as a new address.

`IdentityReplicationPolicy` keeps the name, and Strimzi calls it suitable for unidirectional replication, migration and failover. The class goes into both connectors, and the two values have to be the same. The cost is that the policy cannot tell a mirrored record from an original one, so MirrorMaker 2 cannot prevent a cycle with it (the class's own documentation).

With the names kept, `edp.orders.placed` exists on `edp-dr` after the first order and has the same end offset in every partition. The copy sits beside four internal topics. The workers' own `mm2-configs`, `mm2-offsets` and `mm2-status` are on the target. The checkpoints topic is on the target too, named from the source alias: `edp.checkpoints.internal`. The offset syncs are different. They are written where the records are read, so `mm2-offset-syncs.edp-dr.internal` is a topic of the source cluster by default. Strimzi's own example sets `offset-syncs.topic.location: "target"`, which gives the mirror read-only access to the source; the default needs it to write there. The default stays here.

Chapter 32 monitors the cluster `edp`, and the same Metrics Reporter runs on MirrorMaker 2 workers. Its scrape job finds the worker pod by the Strimzi kind label, the allow list asks for the Connect families, and the worker reports its two connectors as `kafka_connect_connect_worker_metrics_connector_count`. The dashboard `edp-mirrormaker2` is provisioned beside `edp-strimzi`.

## Consumer offsets across clusters

{% include excalidraw.html file="34-offset-translation" alt="A record at source offset 4 gets an offset sync, the group commits 4 on the source and is stopped, a checkpoint carries the translated offset, the group on the target is written with 4, and a consumer with a new bootstrap address reads from 4" caption="Figure 34.2: A checkpoint turns a source offset into the target group's committed offset" %}

The settings that make the translation work are few.

| Setting | Value here | Default | Effect |
|---|---|---|---|
| `sync.group.offsets.enabled` | `"true"` | `false` | The checkpoint connector writes translated offsets into the target group |
| `offset.lag.max` | `0` | `100` | How far out of sync a target partition may get before it is synced again |
| `emit.checkpoints.interval.seconds`, `sync.group.offsets.interval.seconds` | 5 | 60 | How often checkpoints are written and applied |
| `refresh.groups.interval.seconds` | 5 | 600 | How often new groups are found |

The intervals are short so a laptop run finishes in minutes; Strimzi's example for group sync uses a minute for checkpoints and ten minutes for finding groups (`kafka-mirror-maker-2-sync-groups.yaml`). The one that matters for correctness is `offset.lag.max`. With the default of 100, a target partition may trail the source by up to 100 offsets before it is synced again, so on a short topic the group on `edp-dr` stays behind the source's committed offsets. With 0, every change is synced and the group matches within seconds. On a short topic it is the setting that makes the translation happen at all.

Two properties of the checkpoint apply to every failover. The checkpoint connector writes a group on the target only while no consumer is active in that group there; a group with a live consumer on the target is skipped with a warning. And a translated offset can be earlier than the source's position, so records are read twice: Strimzi's documentation says "This duplication is expected." A consumer that fails over must tolerate a record twice, which is what the idempotent consumer of chapter 11 is for.

Groups come from `groupsPattern`, minus the connector's default exclusions, `console-consumer-.*`, `connect-.*` and `__.*`. A group the source has never committed on a partition does not appear on the target for that partition until it commits.

## Failing a consumer over

The consumer is `notification-service` of chapter 2. Five orders have been notified on `edp`, and the group there has committed the five records. `edp-dr` holds the same five records and, after a few seconds, the same group with the same committed offsets, before any consumer has connected to it.

The failover is a stop and a start with one new value.

```yaml
env:
  - name: CAMEL_COMPONENT_KAFKA_BROKERS
    value: edp-dr-kafka-bootstrap.edp-kafka.svc:9092
```

The service is stopped first, which leaves the group on the source inactive. Five more orders go to `edp`; nothing is notified, because nothing reads. The mirror copies them, so `edp-dr` holds ten records and the group on `edp-dr` still sits at five. The service starts again with the new address and nothing else changed: same topic, same group, same code. It becomes Ready, the five orders are notified within 8 s, and the group on `edp-dr` ends at ten, five more than it started.

Two readings show that nothing was read twice. The group's committed offset on `edp-dr` rose by exactly five. And each of the ten orders has exactly one stored notification, because `notification-service` stores a row for every record it reads, so a record read a second time would leave a second row.

| After the failover | Group offset not translated | Group offset translated |
|---|---|---|
| Where the consumer starts | The reset policy: `earliest` in the services of chapter 2, the first record | The checkpoint's offset: the sixth |
| What the group on `edp-dr` needs | Nothing, but every record is read again | The sync setting, and the source group stopped |
| What fails over by hand | The consumer's position | The bootstrap address |

With `sync.group.offsets.enabled` set to `false`, the mirror never writes the group on `edp-dr`, so the service falls back to its reset policy: it starts at the first record and the five earlier orders are notified a second time.

## Build, run, observe

```bash
cd examples/34-mirrormaker-2
./verify.sh
```

A second cluster and a MirrorMaker 2 come up beside the services of chapter 2, five orders are mirrored, the consumer is stopped, five more orders are placed and the consumer restarts against `edp-dr`; the run ends with `18/18 checks passed`.

Try one change and rerun:

- `OFFSET_SYNC=off ./verify.sh` (demo code): the group on `edp-dr` is missing until the consumer fails over, and the five earlier orders are notified twice.
- `KEEP=1 ./verify.sh`, then `kubectl --context edp -n edp-kafka exec edp-dr-dual-0 -- /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group notification-service`: the committed offsets of the group on the second cluster.
- `KEEP=1 ./verify.sh`, then the same pod with `kafka-topics.sh --list`: the copy and the internal topics, `edp.checkpoints.internal` among them.

## When not to use it

The copy is made after the write, by a consumer and a producer, so a failover loses the records the mirror had not yet copied. The MBean `replication-latency-ms` of the source connector measures how far behind it is (Kafka geo-replication documentation); a recovery objective of zero records needs a different design.

A failover duplicates records. The checkpoint can land before the consumer's real position, and the target group is written only while it is inactive. Consumers that cannot tolerate a record twice need the inbox of chapter 11 before this pattern is safe.

The two clusters here share a namespace, a Kubernetes cluster and one machine. Losing the node loses both, so the pattern is modelled here and the protection is not. A real failover cluster lives in a different failure domain, and the mirror's bandwidth and latency are then a cost.

The identity policy cannot prevent cycles, so a mirror in each direction would copy its own copies. Failing back means replicating from the second cluster to the first with the policy and offsets thought through again, which is a second design.

> **🤖 Explore with your agent**
> "In examples/34-mirrormaker-2, read base/mirrormaker2.yaml and run verify.sh with KEEP=1. With kafka-consumer-groups.sh in edp-broker-0 and in edp-dr-dual-0, describe the group notification-service on both clusters. Run it again with OFFSET_SYNC=off and describe the group on edp-dr. Say which connector writes the group on edp-dr, which setting turns that on, and why notification-service has to be stopped before the failover."
> Check: with the sync on, the committed offsets of the group on edp-dr equal those on edp; with OFFSET_SYNC=off edp-dr has no notification-service group before the failover, and afterwards O-3401 has two notifications.

## References

- Strimzi, [Deploying and Managing Strimzi](https://strimzi.io/docs/operators/latest/deploying.html): MirrorMaker 2 (the `KafkaMirrorMaker2` resource, replication policies, offset syncs, checkpoints and group offset synchronization) and the Metrics Reporter
- Roman Martin and Manuel Schindler, [Migrating Kafka clusters with MirrorMaker2 and Strimzi](https://strimzi.io/blog/2021/11/22/migrating-kafka-with-mirror-maker2/) (Strimzi blog, 2021) for the migration scenario, and the Strimzi [changelog](https://github.com/strimzi/strimzi-kafka-operator/blob/main/CHANGELOG.md) for the `v1` shape of the resource
- Strimzi, [example `kafka-mirror-maker-2-sync-groups.yaml`](https://github.com/strimzi/strimzi-kafka-operator/blob/main/packaging/examples/mirror-maker/kafka-mirror-maker-2-sync-groups.yaml)
- Apache Kafka, [Geo-replication](https://kafka.apache.org/43/operations/geo-replication-cross-cluster-data-mirroring/) (replication policy, monitoring), and the source of [`IdentityReplicationPolicy`](https://github.com/apache/kafka/blob/4.3/connect/mirror-client/src/main/java/org/apache/kafka/connect/mirror/IdentityReplicationPolicy.java) and [`MirrorCheckpointConfig`](https://github.com/apache/kafka/blob/4.3/connect/mirror/src/main/java/org/apache/kafka/connect/mirror/MirrorCheckpointConfig.java)
- [KIP-382: MirrorMaker 2.0](https://cwiki.apache.org/confluence/display/KAFKA/KIP-382%3A+MirrorMaker+2.0)
- Chapter 2 for the services that fail over, chapter 11 for the consumer that tolerates a duplicate, chapter 28 for the cluster, chapter 32 for monitoring
