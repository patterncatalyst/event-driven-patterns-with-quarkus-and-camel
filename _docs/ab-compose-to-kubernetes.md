---
title: "Appendix B: Compose to Kubernetes"
order: 37
marker: "B"
label: "Appendix B"
part: appendices
permalink: /docs/ab-compose-to-kubernetes/
description: "A compose file and a set of Kubernetes resources describe the same Kafka and PostgreSQL in two vocabularies, and the settings a service silently relied on can be lost in the move. Where each setting lives in the Kafka resource and the CloudNativePG Cluster, and how a comparison of the running systems finds what differs."
duration: "45 minutes"
---

Moving a service from compose to Kubernetes changes its addresses. The settings underneath it move too, and nothing reports it when one is lost. A service that creates its topics by sending to them relies on `auto.create.topics.enable` and on `num.partitions`. A Debezium connector relies on logical WAL and on a free replication slot. A share group relies on broker settings that the compose file sets, two of them because one broker cannot satisfy their defaults. Each is one line in a compose file, and each has to find a new home in a resource that the reader did not write.

Reading the two files side by side finds the lines that were copied. It does not find the ones an operator adds or changes, because the operator's defaults are not in either file. CloudNativePG defaults `wal_level` to `logical`, so the Cluster needs no line for it, while the compose file has to ask for it on the command line. The only complete view is the running system's own answer: start each target fresh, ask Kafka and PostgreSQL for their effective settings and compare the two sets of answers.

{% include excalidraw.html file="ab-same-config-two-targets" alt="Two columns. Compose: a kafka container with broker and controller roles, the KAFKA environment, a postgres container run by start.sh and its command line flags, named volumes. Kubernetes: a Kafka resource with two node pools, the spec.kafka.config block, a CloudNativePG Cluster, no postgresql parameters, persistent-claim storage" caption="Figure B.1: Each compose setting has a place in a Kubernetes resource, or a default that replaces it" %}

## Kafka: one container, then a Kafka resource and two node pools

The compose broker is one container with both KRaft roles. Everything about it is an environment variable.

```yaml
# examples/_infra/compose-kafka.yaml
kafka:
  image: docker.io/apache/kafka:4.3.2
  environment:
    - KAFKA_NODE_ID=1
    - KAFKA_PROCESS_ROLES=broker,controller
    - KAFKA_AUTO_CREATE_TOPICS_ENABLE=true
    - KAFKA_NUM_PARTITIONS=3
    - KAFKA_DEFAULT_REPLICATION_FACTOR=1
    - KAFKA_MIN_INSYNC_REPLICAS=1
    - KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1
    - KAFKA_GROUP_COORDINATOR_REBALANCE_PROTOCOLS=classic,consumer,streams,share
    - KAFKA_SHARE_COORDINATOR_STATE_TOPIC_REPLICATION_FACTOR=1
    - KAFKA_SHARE_COORDINATOR_STATE_TOPIC_MIN_ISR=1
```

The Kafka image turns each `KAFKA_` variable into a broker key: the prefix goes, each underscore becomes a dot and the rest is lower case. On Kubernetes the same keys live in `spec.kafka.config` of the `Kafka` resource, with their real names, and the roles move out of the configuration into node pools.

```yaml
# examples/_k8s/kafka/kafka.yaml
spec:
  kafka:
    version: 4.3.2
    config:
      auto.create.topics.enable: true
      num.partitions: 3
      default.replication.factor: 1
      min.insync.replicas: 1
      offsets.topic.replication.factor: 1
      transaction.state.log.replication.factor: 1
      transaction.state.log.min.isr: 1
```

```yaml
# examples/_k8s/kafka/nodepool-broker.yaml
kind: KafkaNodePool
spec:
  replicas: 1
  roles: [broker]
  storage:
    type: jbod
    volumes: [{id: 0, type: persistent-claim, size: 2Gi, deleteClaim: true}]
```

Four parts of the compose service have no line of their own here. The node id is assigned by Strimzi and read from the pool's `status.nodeIds`: the broker is 0 and the controller 1, where compose pins the broker to 1. The advertised listeners disappear, because Strimzi builds them from `spec.kafka.listeners`, and the cluster has one internal listener behind the bootstrap Service. The volume is the pool's storage. The share group settings are simply absent, which matters below.

Strimzi does not let a `Kafka` resource set everything. Its documentation says that "Kafka broker config properties with certain prefixes cannot be set unless it is in the exception list", and it lists the forbidden prefixes. The keys in this comparison are ordinary broker keys, but the list is the first place to look when a setting from a compose file is refused.

## PostgreSQL: one container, then a CloudNativePG Cluster

The compose database runs CloudNativePG's operand image by hand. The image has no entrypoint, so a start script runs `initdb` on the first start and then execs `postgres` with four settings on the command line.

```yaml
# examples/_infra/compose-postgres.yaml
postgres:
  image: ghcr.io/cloudnative-pg/postgresql:18.6-202611090812-standard-trixie
  command: ["bash", "/opt/edp/start.sh"]
  environment:
    - POSTGRES_DB=edp
    - POSTGRES_USER=edp
```

```bash
# examples/_infra/postgres/start.sh, the last lines
exec postgres \
    -c wal_level=logical \
    -c max_replication_slots=10 \
    -c max_wal_senders=10 \
    -c listen_addresses='*'
```

On Kubernetes the operator is the entrypoint. The same image is named in the `Cluster`, and `bootstrap.initdb` replaces the script's `initdb`, `createdb` and password handling.

```yaml
# examples/_k8s/postgres/cluster.yaml
kind: Cluster
spec:
  instances: 1
  imageName: ghcr.io/cloudnative-pg/postgresql:18.6-202611090812-standard-trixie
  bootstrap:
    initdb: {database: edp, owner: edp}
  storage: {size: 1Gi}
```

There is no `postgresql.parameters` block, and none is needed for `wal_level`. CloudNativePG builds the configuration from four layers in order: global defaults, defaults that depend on the major version, the parameters the Cluster sets and a short list of fixed parameters; it states that the fixed ones "can't be overridden by the user via the YAML configuration". Its global defaults include `wal_level = 'logical'` and `max_replication_slots = '32'`. A start script that passes `wal_level=logical` as a flag and a Cluster that passes nothing therefore report the same level and different slot counts.

One difference sits outside the settings. Debezium on Kubernetes connects as the database owner, which needs the `REPLICATION` attribute, and the Cluster grants it through `managed.roles`. The compose files have no such line.

## What is the same, what differs and why

{% include excalidraw.html file="ab-capture-and-compare" alt="verify.sh compose and verify.sh k8s each start their target fresh and write a capture of 13 broker settings and 5 PostgreSQL settings; verify.sh compare reads both captures and reports each setting as equal, as an allowed difference or as a failure" caption="Figure B.2: Both targets are read from the running systems and compared against one list of expected differences" %}

The capture is a fixed list of keys, read the same way on both sides: `kafka-configs.sh --describe --all` for the broker, and `current_setting()` through `psql` for PostgreSQL. On compose the tools run in the `edp-kafka` and `edp-postgres` containers with `docker exec`, on Kubernetes in the broker and instance pods with `kubectl exec`. The comparison runs against files and starts nothing. Thirteen broker keys and five PostgreSQL keys are compared, plus the Kafka version.

| Equal on both targets | Value |
|---|---|
| `auto.create.topics.enable` | `true` |
| `num.partitions` | `3` |
| `default.replication.factor`, `min.insync.replicas` | `1` |
| `offsets.topic.replication.factor` | `1` |
| `log.retention.hours` | `168` |
| `transaction.state.log.replication.factor`, `transaction.state.log.min.isr` | `1` |
| `transaction.state.log.num.partitions`, `share.coordinator.state.topic.num.partitions` | `50` |
| `wal_level` | `logical` |
| `max_wal_senders`, `max_connections` | `10`, `100` |
| `server_version` | `18.6`, compared on major and minor |
| Kafka version | `4.3.2` |

Four settings differ, and the comparison lists them. Any other difference fails it.

| Setting | Compose | Kubernetes | Why |
|---|---|---|---|
| `group.coordinator.rebalance.protocols` | `classic,consumer,streams,share` | `classic,consumer,streams` | Compose lists `share` for share groups; the Kafka resource leaves Kafka's default |
| `share.coordinator.state.topic.min.isr` | `1` | `2` | Compose overrides the default; the resource does not |
| `share.coordinator.state.topic.replication.factor` | `1` | `3` | The same, and one broker cannot satisfy 3 |
| `max_replication_slots` | `10` | `32` | The start script passes 10; the Cluster leaves CloudNativePG's default |

The first three have one cause. Kafka 4.3 documents `group.coordinator.rebalance.protocols` with a default of `classic,consumer,streams`, and the share state topic with a replication factor of 3 and a minimum in-sync count of 2 ("Topic creation will fail until the cluster size meets this replication factor requirement"). Compose overrides all three because a share group on one broker needs them. The Kafka resource sets none of them, so the Kubernetes cluster as built does not list the `share` protocol. Kafka's page also marks the protocols setting deprecated, with every protocol always enabled in Kafka 5.0, so the line has a limited life.

The slot count is the reverse: compose is the one that sets a value, and CloudNativePG's default is larger. A Debezium connector holds one replication slot, so both targets have room for a few connectors, and Kubernetes has more.

The settings the compose file and the resource each set are also checked against their own file. Every key the file or the resource sets must be what the running system reports, and every key it leaves unset must be the documented default. A copied line that did not take effect fails there, before the comparison runs.

A describe for a node id that is not a broker prints nothing and exits successfully. A capture taken with the wrong id would be empty, and an empty file compared with an empty file is equal. The capture check therefore requires every key to be present, and a witness captures a node id that is no broker to show that the check rejects the empty result.

Listeners, advertised addresses, storage and resource limits are not compared. They are the parts that differ because a pod is not a container: names, volumes and memory. The comparison covers the settings that change what the broker and the database do.

> **🤖 Explore with your agent**
> "In examples/ab-compose-to-kubernetes, run verify.sh compose with DRIFT=partitions, then verify.sh k8s and verify.sh compare. Read ALLOWED in configs.py, say which setting differs and whether the allowlist covers it, and name the line of which file would make the two targets agree."
> Check: the compose capture reports num.partitions 6 against 3 on Kubernetes, compare fails on that key and on the unlisted difference, and the agent proposes a change to drift.yaml or the Kafka resource, not a new entry in ALLOWED.

## Build, run, observe

```bash
cd examples/ab-compose-to-kubernetes
./verify.sh compose
./verify.sh k8s
./verify.sh compare
```

Each half starts its target from nothing, reads it and removes its state; the Kubernetes half creates the minikube profile first if it is missing. `compare` starts nothing and reads the two captures under `target/captures`. Watch the four differences come out as listed and nothing else. The halves end with `9/9 checks passed`, and the comparison with `23/23 checks passed`.

Try one change and rerun:

- `DRIFT=partitions ./verify.sh compose` (demo code), then `./verify.sh compare`: the broker reports 6 partitions, and the comparison reports the setting as an unlisted difference.
- `KEEP=1 ./verify.sh compose`, then `docker exec edp-kafka /opt/kafka/bin/kafka-configs.sh --bootstrap-server localhost:9092 --entity-type brokers --entity-name 1 --describe --all`, to read the full effective list.

## References

- Strimzi, [Configuring Strimzi](https://strimzi.io/docs/operators/latest/configuring.html): `spec.kafka.config`, forbidden prefixes and listeners
- Apache Kafka, [Broker configs](https://kafka.apache.org/43/configuration/broker-configs/): the defaults of the keys compared, `group.coordinator.rebalance.protocols` and the share coordinator state topic
- CloudNativePG, [PostgreSQL configuration](https://cloudnative-pg.io/docs/1.30/postgresql_conf/): global defaults, user parameters and fixed parameters
- PostgreSQL, [Connections and Authentication](https://www.postgresql.org/docs/18/runtime-config-connection.html) (`max_connections`) and [Replication](https://www.postgresql.org/docs/18/runtime-config-replication.html) (`max_wal_senders`, `max_replication_slots`): the PostgreSQL defaults
- [Prerequisites and stacks]({{ '/docs/00-prerequisites-and-stacks/' | relative_url }}): the compose stack
- [Strimzi and KRaft]({{ '/docs/28-strimzi-and-kraft/' | relative_url }}): the Kafka resource and node pools
- [Debezium and Connect on Strimzi]({{ '/docs/35-debezium-and-connect-on-strimzi/' | relative_url }}): the replication role
- [Queues for Kafka]({{ '/docs/07-queues-for-kafka/' | relative_url }}): share groups
- [Log tailing with Debezium]({{ '/docs/09-log-tailing-with-debezium/' | relative_url }}): replication slots
