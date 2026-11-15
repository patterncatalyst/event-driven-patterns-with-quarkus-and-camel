# 29 Unidirectional Topic Operator

The topics of the event catalog are declared as `KafkaTopic` resources on a Kafka cluster whose broker no longer creates topics. This example checks what the Strimzi Topic Operator does with them: the declared set equals `Topics.ALL`, a change made in Kafka is reverted or reported, a topic created in Kafka gets no resource, and deleting a resource deletes its topic unless it was unmanaged first.

Manifests only: a kustomize overlay on `examples/_k8s` (Kafka, PostgreSQL and LGTM bases, the chapter 2 deployments) and one `KafkaTopic` file. No Java and no pom. The chapter 2 images are built from their own Containerfiles, unchanged.

## What the overlay changes

| File | Purpose |
|---|---|
| `kustomization.yaml` | Builds `base/` |
| `base/kustomization.yaml` | The bases of `../_k8s`, the chapter 2 deployments, `topics/`, and the Kafka patch; kept apart so the demo overlays can build on it |
| `base/kafka-patch.yaml` | `auto.create.topics.enable: false` and `entityOperator.topicOperator.reconciliationIntervalMs: 15000` on the `Kafka` resource `edp` |
| `base/topics/kafkatopics.yaml` | The nine `KafkaTopic` resources, 3 partitions and 1 replica each; `edp.orders.placed` declares `retention.ms: 3600000` |
| `images.txt` | The two chapter 2 images loaded into the node |
| `topics_all.py` | Prints `Topics.ALL` from `examples/domain-model` (`python3 -I`) |
| `kafkatopic_state.py` | Reads `kubectl get kafkatopic -o json` and prints names, conditions, spec fields and finalizers (`python3 -I`) |
| `demo-autocreate/`, `demo-missing-topic/` | Demo code, selected by the switches below; each links `images.txt` so the images load |

The chapter 2 pair publishes and reads only `edp.orders.placed`, which is in `Topics.ALL`, so no topic is added to the set for it.

## Run

```bash
../_k8s/cluster.sh up      # once: profile `edp` and the platform tier (see ../_k8s/README.md)
./verify.sh
KEEP=1 ./verify.sh         # leave the namespaces in place
```

`verify.sh` deletes and recreates the namespaces `edp`, `edp-kafka` and `edp-lgtm`, applies the overlay with `--server-side`, and ends with `17/17 checks passed`. The cluster is reached through `kubectl exec` and loopback NodePorts only.

## Demo switches

Demo code, labelled as such in `verify.sh`, the overlays and the chapter.

| Switch | Effect | Failing set (measured) |
|---|---|---|
| `TOPIC_AUTOCREATE=on ./verify.sh` | `demo-autocreate/` sets `auto.create.topics.enable: true`, so the broker creates the undeclared topic on a send | Check 7 (16/17), as predicted |
| `TOPIC_SET=missing ./verify.sh` | `demo-missing-topic/` deletes the `edp.orders.placed` resource from the rendered set, an altered copy of the kustomization. The apps are not waited for in `k8s_fresh`; the checks poll for them | Checks 3 and 6 (15/17), as predicted |

The two switches are separate demos and cannot be combined. The sets were measured in the live window and are frozen here.

The altered copy can be built by hand: copy `base/` to a scratch directory beside the example, remove the `edp.orders.placed` document from its `topics/kafkatopics.yaml` and build that copy. `TOPIC_SET=missing` does the same with a delete patch: `kubectl kustomize examples/29-unidirectional-topic-operator/demo-missing-topic` renders eight `KafkaTopic` resources.

## Tests and checks in detail

Scratch topics named `edp.lab.*` (demo code of the verification, not part of the catalog) carry the lifecycle experiments: `drift`, `recreated`, `growth`, `removed` and `kept` are applied as `KafkaTopic` resources, and `outside` and `undeclared` are created or sent to directly in Kafka. All scratch changes to Kafka (retention altered, topic deleted, partitions raised, topic created) are started together, and each effect is then polled within a bound of three reconciliation intervals (45 s).

1. Precondition: `auto.create.topics.enable` in the `Kafka` resource is `false` (`true` under `TOPIC_AUTOCREATE=on`).
2. Precondition: the `topic-operator` container of `edp-entity-operator` has `STRIMZI_FULL_RECONCILIATION_INTERVAL_MS=15000`.
3. The `KafkaTopic` names equal `Topics.ALL`, parsed from `Topics.java`.
4. Every `KafkaTopic` is Ready.
5. Kafka holds the partitions of every `KafkaTopic` and the retention of those that declare one.
6. Order `O-2901` is placed through `order-service` (201) and its notification arrives.
7. A producer to an undeclared topic fails with `Topic edp.lab.undeclared not present in metadata after 8000 ms` and no topic is created; a control send to a declared topic succeeds first.
8. Precondition: the five scratch `KafkaTopic` resources are Ready.
9. A `KafkaTopic` carries the finalizer `strimzi.io/topic-operator`.
10. A retention declared in the resource and altered with `kafka-configs.sh` is back to the declared value within the bound.
11. A topic deleted in Kafka is back with a new topic id and its `KafkaTopic` is Ready.
12. Partitions raised from 3 to 6 in Kafka: the `KafkaTopic` shows `Ready=False`, reason `NotSupported`, message `Decreasing partitions not supported`.
13. The spec still says 3 and Kafka still holds 6.
14. The topic created with `kafka-topics.sh` exists, and no `KafkaTopic` names it for the whole bound.
15. Deleting a `KafkaTopic` removes its topic within the bound.
16. After `strimzi.io/managed=false` the `KafkaTopic` shows `Unmanaged=True`.
17. Deleting the unmanaged resource removes it and the topic stays in Kafka for a further 6 s.

Only keys declared in a `KafkaTopic` are asserted. Keys it does not declare are not measured and the chapter says nothing about them.

## Measurements

Measured in three runs (two in the live window, one on a fresh profile) on profile `edp` with the 15 s interval; `verify.sh` prints each value on a `measured:` line.

- Five `KafkaTopic` resources applied to Ready: 1 s. Declared retention reverted: 6 to 23 s. Topic deleted in Kafka recreated: 8 to 25 s. `KafkaTopic` deleted, topic gone: 1 to 2 s. A topic created in Kafka still has no `KafkaTopic` at the end of the 45 s window.
- Deleting the `KafkaTopic` resources, Kafka and the three namespaces: 33 to 43 s, with no stuck finalizer.
