---
title: "MirrorMaker 2"
order: 34
permalink: /examples/34-mirrormaker-2/
description: "A second Kafka cluster beside the first, a MirrorMaker 2 that copies the event catalog's topics and the consumer group offsets from one to the other, and a consumer that fails over by changing its bootstrap address and resumes from the translated offset."
---

[`examples/34-mirrormaker-2/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/34-mirrormaker-2) is a manifest overlay on the minikube substrate of chapter 28: the Kafka cluster `edp`, a second cluster `edp-dr` with one dual-role node, a `KafkaMirrorMaker2` from `edp` to `edp-dr`, the monitoring of chapter 32, and the services of chapter 2. It accompanies [MirrorMaker 2]({{ '/docs/34-mirrormaker-2/' | relative_url }}).

It uses kustomize through `kubectl`, Strimzi, Kafka, CloudNativePG, the LGTM image and the unchanged chapter 2 images.

```bash
cd examples/34-mirrormaker-2
./verify.sh
```

{% include excalidraw.html file="34-offset-translation" alt="A record at source offset 4 gets an offset sync, the group commits 4 on the source and is stopped, a checkpoint carries the translated offset, the group on the target is written with 4, and a consumer with a new bootstrap address reads from 4" caption="How a source group offset becomes a target group offset" %}

## What to observe

1. **The same names.** `edp.orders.placed` exists on both clusters, with the same number of records, and the offset syncs and checkpoints sit in the internal topics the connectors use.
2. **A group on the copy.** The group `notification-service` appears on `edp-dr` with the committed offsets it has on `edp`, before any consumer there has joined.
3. **A failover that changes one setting.** `notification-service` is stopped, five more orders arrive, and the service started against `edp-dr` reads exactly those five.
4. **A witness.** With `OFFSET_SYNC=off` the group never appears on `edp-dr`.

## Infrastructure

The cluster, PostgreSQL, LGTM, metrics and monitoring bases of `examples/_k8s`, the second cluster and the mirror, one dashboard, and the chapter 2 deployments.
