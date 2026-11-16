---
title: "Deploying event-driven services on minikube"
order: 41
permalink: /examples/af-deploying-event-driven-services/
description: "The event notification service and the order service, unchanged, replaced by a stop and restart, a rolling update, a schema change and a blue-green cut-over under a producer that numbers every record."
---

[`examples/af-deploying-event-driven-services/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/af-deploying-event-driven-services) changes two running services in five ways while a producer Job sends uniquely numbered records at a fixed rate. It accompanies [Appendix F]({{ '/docs/af-deploying-event-driven-services/' | relative_url }}).

It is a kustomize overlay over the shared minikube setup in `examples/_k8s` and a `verify.sh`, with no Java. The services are the notification-service and the order-service, built from their own Containerfiles and loaded into the node unchanged.

```bash
examples/_k8s/cluster.sh up
cd examples/af-deploying-event-driven-services
./verify.sh
```

{% include excalidraw.html file="af-bg-cutover" alt="Four steps in order: scale blue to 0, export blue's offsets, import them into green's group, scale green to 3; records after blue's last commit are processed again by green" caption="Green starts where blue's commits ended" %}

## What to observe

1. **A stop and restart.** No row is written while the pods are replaced, the lag drains afterwards and every record has a row.
2. **A rolling update.** Each pod is replaced in turn, every record has a row, and a bounded number are delivered again.
3. **Requests during a rollout.** Connection-level failures stay below a ceiling, no request gets a 5xx, and every 201 left exactly one event.
4. **A breaking change.** Records with a renamed field produce no row, the group's lag is zero and the log holds the only trace; a record with both fields is stored.
5. **Blue-green.** Green holds blue's committed offsets before it starts, nothing is missing across the cut-over and the rollback, and the groups share only the records after the last commit.

## Infrastructure

Strimzi, Kafka and CloudNativePG from `examples/_k8s` on the `edp` minikube profile, the two services, and a producer Job per scenario. The order-service is reached on NodePort 30080.
