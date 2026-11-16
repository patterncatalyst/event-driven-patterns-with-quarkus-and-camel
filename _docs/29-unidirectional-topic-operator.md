---
title: "Unidirectional Topic Operator"
order: 29
part: running-on-kubernetes-with-strimzi
permalink: /docs/29-unidirectional-topic-operator/
description: "A broker that creates topics on first use turns a typo into a new topic and leaves partitions and retention to whatever the defaults were. Every topic of the event catalog is declared as a KafkaTopic, auto-creation is off, and the Topic Operator reconciles in one direction only: from the resource to Kafka."
duration: "50 minutes"
---

The Strimzi Kafka cluster lets the broker create a topic the first time anyone sends to it, and a Job creates the topics the examples need. Both work, and neither leaves a list. A producer configured with `edp.orders.plcaed` gets a new topic, a healthy send and a consumer that waits for events that never come. A topic created by hand with two partitions in one environment has three in the next.

The list of topics a system needs is part of the system. Kubernetes already keeps lists of things and compares them with what runs, so the topics can live there too.

{% include excalidraw.html file="29-one-direction" alt="A KafkaTopic resource is read by the Topic Operator, which creates, alters and deletes the Kafka topic; an edit made in Kafka with kafka-configs.sh is reverted on the next pass, and a status written back to the resource reports Ready or NotSupported" caption="Figure 29.1: Changes go from the resource to Kafka; an edit made in Kafka is put back" %}

## Pattern: Topics as declared resources

### The problem

With auto-creation on, a topic comes into being as a side effect of a send or a subscribe. Its partitions and configuration are the broker defaults, and a later attempt to give it the configuration it should have can fail. The Strimzi documentation names the example: lowering the partition count of a topic that already has more. A name that is wrong in one service creates a second topic instead of an error. Nothing records which topics the event catalog should contain, so the only way to know is to ask the broker, which knows what exists and not what was meant.

### The solution

**Declare every topic as a Kubernetes resource, turn off broker auto-creation, and let an operator reconcile Kafka toward the resources and never the other way.**

The resources are the list. Review, rollback and diff work on them as on any manifest, and an unlisted topic is an error at the first send instead of a new topic.

### How Strimzi models it

A `KafkaTopic` names a topic, its partitions, its replicas and the configuration keys it owns. The label `strimzi.io/cluster` binds it to a cluster.

```yaml
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata:
  name: edp.orders.placed
  labels:
    strimzi.io/cluster: edp
spec:
  partitions: 3
  replicas: 1
  config:
    retention.ms: 3600000
```

The operator that reads it runs inside the entity operator of the `Kafka` resource, and by default it watches the namespace of its cluster. Two changes to the `Kafka` resource turn it on and switch auto-creation off.

```yaml
spec:
  kafka:
    config:
      auto.create.topics.enable: false
  entityOperator:
    topicOperator:
      reconciliationIntervalMs: 15000
```

The interval is how often the operator compares every resource with Kafka, in addition to acting when a resource changes. Fifteen seconds keeps the effects of an out-of-band change within a minute. The nine topics of the event catalog are nine resources in one file, and the set is checked against `Topics.ALL` of the domain model, so a topic added to the catalog without a resource is caught. The event notification services run unchanged on this cluster: both topics they use are in the set.

Strimzi names the component for that single direction: the Unidirectional Topic Operator (Strimzi blog, 2023).

## One direction only

The documentation states the rule in one sentence: configuration changes go from the `KafkaTopic` to the Kafka topic, and any change to a topic managed outside the resource is reverted. A resource is therefore the single source of truth for the topics it names, and the operator never creates a resource from a topic it finds.

The effect on the undeclared name is immediate. With auto-creation off, a producer sending to `edp.lab.undeclared` logs repeated `UNKNOWN_TOPIC_OR_PARTITION` warnings and then fails with `Topic edp.lab.undeclared not present in metadata after 8000 ms`. The topic does not exist afterwards. A typo is an error the first time it is sent.

The same property makes a missing resource visible. Leave `edp.orders.placed` out of the set and `order-service` cannot publish, so no notification arrives, while the other eight topics stay Ready.

| Question | Auto-creation on | Auto-creation off, resources declared |
|---|---|---|
| A send to a name nobody declared | A new topic with broker defaults | Warnings, then a timeout; no topic |
| Where the topic's partitions and retention are written | Wherever someone ran a command | The resource, in the repository |
| What lists the topics a system needs | The broker, after the fact | The resources, before anything runs |

## Drift

A topic can be changed in Kafka by anyone with the tools, and the resource does not hear about it until the next full pass. A retention that the resource declares as `3600000` and that is altered with `kafka-configs.sh --alter` is back at `3600000` 6 to 23 s later in three measured runs with the 15 second interval; the time depends on where in the cycle the change lands. The keys in `spec.config` are the ones to rely on, and a configuration that matters belongs in the resource.

Partitions are the exception that cannot be reverted. Kafka does not shrink a topic, so when the partitions are raised in Kafka to 6 while the resource says 3, the operator cannot make the topic match. The `KafkaTopic` stays at `Ready=False` with reason `NotSupported` and the message `Decreasing partitions not supported`. The spec still says 3, the topic keeps 6, and the status stays False until the resource and the topic agree. The mismatch is reported, not repaired.

A topic deleted in Kafka is recreated from its resource, 8 to 25 s after the deletion in the same three runs. The recreated topic is a new topic with a new id.

## Deletion and unmanaged topics

The operator puts a finalizer, `strimzi.io/topic-operator`, on every `KafkaTopic`, so Kubernetes keeps the resource until the operator has removed the topic. Deleting the resource deletes the topic, 1 to 2 s later in the measured runs. The documentation points out the other side: when the operator is not running, a resource with the finalizer cannot be deleted, and a namespace holding such resources stays in `Terminating`. Delete the `KafkaTopic` resources before the operator or the namespace, so the operator can remove its finalizers.

A topic that must outlive its resource is made unmanaged first.

```bash
kubectl -n edp-kafka annotate kafkatopic edp.lab.kept strimzi.io/managed=false --overwrite
```

The status shows `Unmanaged=True`. Deleting an unmanaged resource removes the resource and leaves the topic in Kafka, which is how a topic is handed over to another owner.

{% include excalidraw.html file="29-topic-lifecycle" alt="Actions in Kubernetes: applying a KafkaTopic creates the topic, deleting it deletes the topic, annotating managed false marks it Unmanaged and deleting it then keeps the topic. Actions in Kafka: an altered declared key is reverted, a deleted topic is recreated, raised partitions are reported NotSupported, and a new topic never gets a resource" caption="Figure 29.2: What each action does to the other side, as measured with a 15 second interval" %}

A topic created in Kafka with `kafka-topics.sh` gets no resource and none appears: the operator never reads in that direction. Such a topic is invisible to the list, and that is the cost of the pattern. Two resources naming one topic are not a way around it: the oldest is reconciled and the other fails with a `ResourceConflict` error (Strimzi blog).

| Action | Result |
|---|---|
| `KafkaTopic` applied | Topic created, `Ready=True` after 1 s in the measured run |
| Declared config key changed in Kafka | Reverted on a later pass |
| Partitions raised in Kafka | `Ready=False`, `NotSupported`; topic keeps the higher count |
| Topic deleted in Kafka | Recreated from the resource |
| Topic created in Kafka | No resource appears |
| `KafkaTopic` deleted | Topic deleted |
| `KafkaTopic` unmanaged, then deleted | Topic stays |

## Build, run, observe

```bash
cd examples/29-unidirectional-topic-operator
./verify.sh
```

The cluster comes up with the entity operator and the nine `KafkaTopic` resources, the event notification pair places an order across them, and changes made in Kafka and in Kubernetes are watched for their effect on the other side; the run ends with `17/17 checks passed`.

Try one change and rerun:

- `TOPIC_AUTOCREATE=on ./verify.sh` (demo code): the broker creates the undeclared topic and the producer succeeds.
- `TOPIC_SET=missing ./verify.sh` (demo code): the set lacks `edp.orders.placed`, the order cannot be published and no notification arrives.
- `KEEP=1 ./verify.sh`, then `kubectl --context edp -n edp-kafka delete kafkatopic edp.shipping.delivered`: the topic is gone from `kubectl --context edp -n edp-kafka exec edp-broker-0 -- /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list`.

## When not to use it

A topic that an application creates at run time, one per tenant or per job, has no resource written in advance. Declaring each of them means generating resources from the same source that creates the tenant, and that is more machinery than the list is worth.

One Topic Operator watches one namespace, and one namespace should be watched by only one Topic Operator. Topics declared in several namespaces need a Topic Operator for each namespace, or one namespace for all the resources.

Teams that administer topics with Kafka's own tools will see their edits reverted on the next pass. Either the resources become the only way to change a topic or the operator is not the right tool.

> **🤖 Explore with your agent**
> "In examples/29-unidirectional-topic-operator, list the KafkaTopic resources in the edp-kafka namespace and compare their names with Topics.ALL in examples/domain-model. Change retention.ms of a declared topic with kafka-configs.sh from the broker pod and watch it return. Delete one KafkaTopic as it is and another after setting strimzi.io/managed to false, then create a topic with kafka-topics.sh. Say which action leaves a topic without a resource, which leaves a resource without a topic, and why the operator never reads Kafka back."
> Check: retention.ms is back at its declared value within about 45 seconds, the first deletion removes the topic, the unmanaged one leaves it, and no KafkaTopic names the topic created in Kafka.

## References

- Strimzi, [Deploying and managing Strimzi](https://strimzi.io/docs/operators/latest/deploying.html): Topic Operator, handling changes to topics, unmanaged topics, finalizers, and automatic creation of topics
- Strimzi, [Introducing the Unidirectional Topic Operator](https://strimzi.io/blog/2023/11/02/unidirectional-topic-operator/) (2023) and [proposal 051](https://github.com/strimzi/proposals/blob/main/051-unidirectional-topic-operator.md)
- Apache Kafka, [Broker configs](https://kafka.apache.org/43/configuration/broker-configs/): `auto.create.topics.enable`
- [Strimzi and KRaft]({{ '/docs/28-strimzi-and-kraft/' | relative_url }})
- [Event notification]({{ '/docs/02-event-notification/' | relative_url }})
