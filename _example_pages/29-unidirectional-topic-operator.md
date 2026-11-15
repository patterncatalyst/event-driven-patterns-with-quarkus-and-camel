---
title: "Unidirectional Topic Operator"
order: 29
permalink: /examples/29-unidirectional-topic-operator/
description: "The nine topics of the event catalog as KafkaTopic resources on a Kafka cluster with auto-creation off, with the Topic Operator's reconciliation of drift, deletion, unmanaged topics and topics created outside Kubernetes checked against the cluster."
---

[`examples/29-unidirectional-topic-operator/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/29-unidirectional-topic-operator) is a manifest overlay on the minikube substrate of chapter 28: the Kafka cluster with the Topic Operator switched on and auto-creation off, nine `KafkaTopic` resources, and the services of chapter 2. It accompanies [Unidirectional Topic Operator]({{ '/docs/29-unidirectional-topic-operator/' | relative_url }}).

It uses kustomize through `kubectl`, Strimzi, Kafka, CloudNativePG, the LGTM image and the unchanged chapter 2 images.

```bash
cd examples/29-unidirectional-topic-operator
./verify.sh
```

{% include excalidraw.html file="29-topic-lifecycle" alt="Actions in Kubernetes: applying a KafkaTopic creates the topic, deleting it deletes the topic, annotating managed false marks it Unmanaged and deleting it then keeps the topic. Actions in Kafka: an altered declared key is reverted, a deleted topic is recreated, raised partitions are reported NotSupported, and a new topic never gets a resource" caption="What each action does to the other side" %}

## What to observe

1. **A set equal to the catalog.** The `KafkaTopic` names are the nine of `Topics.ALL`, all Ready, and Kafka holds the partitions and retention they declare.
2. **No topic from a typo.** A producer sending to an undeclared name is refused and no topic appears.
3. **One direction.** An altered retention is reverted, a deleted topic is recreated, raised partitions are reported as `NotSupported`, and a topic created in Kafka gets no resource.
4. **Deletion.** Deleting a `KafkaTopic` deletes the topic; unmanaging it first leaves the topic in Kafka.

## Infrastructure

The cluster, PostgreSQL and LGTM bases of `examples/_k8s`, one patch to the `Kafka` resource, and the chapter 2 deployments.
