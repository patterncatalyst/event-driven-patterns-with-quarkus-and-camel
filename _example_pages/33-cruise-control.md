---
title: "Cruise Control: rebalancing"
order: 33
permalink: /examples/33-cruise-control/
description: "A Kafka cluster of two brokers with Cruise Control, grown to three nodes and rebalanced by an approved KafkaRebalance, then grown to four and shrunk back with autoRebalance, with the replica placement read back from Kafka at every step."
---

[`examples/33-cruise-control/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/33-cruise-control) is a manifest overlay on the shared minikube setup in `examples/_k8s`: Kafka with Cruise Control, four `KafkaTopic` resources with two replicas, and a load Job. It runs no application. It accompanies [Cruise Control: rebalancing]({{ '/docs/33-cruise-control/' | relative_url }}).

It uses kustomize through `kubectl`, Strimzi, Kafka and Cruise Control.

```bash
cd examples/33-cruise-control
./verify.sh
```

{% include excalidraw.html file="33-scale-and-move" alt="Replicas per broker in three stages: two brokers that hold every replica, a pool grown to three where the new broker holds none, and after the approved rebalance where the new broker holds a share; the operator's own rebalance does the same when the pool grows once more" caption="A broker added to the pool starts empty; replicas arrive only through a rebalance" %}

## What to observe

1. **An empty broker.** After the pool grows, the new broker holds no replica.
2. **A proposal that moves nothing.** The `KafkaRebalance` reaches `ProposalReady`, and the placement is unchanged until the approve annotation is set.
3. **A refused scale-down.** Without `autoRebalance` the broker keeps running, the pool still lists it and the `Kafka` status carries `ScaleDownPreventionCheck`.
4. **The operator's own rebalance.** With `autoRebalance`, a larger pool is rebalanced and a smaller one is drained before the pod is removed.

## Infrastructure

The namespaces, Strimzi and Kafka bases of `examples/_k8s`, patches for Cruise Control and the broker pool, and four `KafkaTopic` resources.
