---
title: "Strimzi and KRaft"
order: 28
permalink: /examples/28-strimzi-and-kraft/
description: "Kafka as two KRaft node pools under the Strimzi operator on minikube, with the services of chapters 2, 3 and 4 run against it one set after another, unchanged."
---

[`examples/28-strimzi-and-kraft/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/28-strimzi-and-kraft) is a set of manifests and a `verify.sh`, with no Java. It declares Kafka as a `Kafka` resource and two `KafkaNodePool` resources, then deploys the services of chapters 2, 3 and 4 against it. It accompanies [Strimzi and KRaft]({{ '/docs/28-strimzi-and-kraft/' | relative_url }}).

It uses minikube, Strimzi, CloudNativePG, kustomize through `kubectl`, and the LGTM image, with the Quarkus and Camel services of examples 02, 03 and 04 as they are.

```bash
examples/_k8s/cluster.sh up
cd examples/28-strimzi-and-kraft
./verify.sh
```

{% include excalidraw.html file="28-kraft-roles" alt="The controller node leads and is the only voter of the metadata quorum, the broker node observes it, and applications reach the broker through the bootstrap Service" caption="One controller, one broker, one bootstrap address" %}

## What to observe

1. **Two pools, two roles.** `kubectl -n edp-kafka get kafkanodepool` lists `controller` and `broker` with their roles and node ids.
2. **The quorum.** `kafka-metadata-quorum.sh describe --status` inside the broker pod names the controller as leader and only voter and the broker as the only observer.
3. **Pods owned by StrimziPodSets.** The broker and controller pods are not owned by a ReplicaSet or a StatefulSet.
4. **Services unchanged.** The environment variable names of the Deployments equal those of the compose files; only the addresses differ.
5. **A replaced broker keeps its identity.** After the broker pod is deleted, the new pod has the same node id and volume claim, and the topics hold the same records.
6. **A wrong address fails loudly.** With `KAFKA_BROKERS=compose`, notification-service never becomes ready and no notification appears.

## Infrastructure

The `edp` minikube profile from `examples/_k8s`, with the Strimzi operator in `edp-kafka`, the CloudNativePG operator in `cnpg-system`, PostgreSQL in `edp` and LGTM in `edp-lgtm`. Ports 30080 and 30082 serve the application set that is deployed.
