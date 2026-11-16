---
title: "Debezium and Connect on Strimzi"
order: 35
permalink: /examples/35-debezium-and-connect-on-strimzi/
description: "A Connect image built from a KafkaConnect resource into a registry inside the cluster, the outbox and inventory connectors as KafkaConnector resources on CloudNativePG, and the inventory connector's offsets listed, refused, altered and resumed through the resource."
---

[`examples/35-debezium-and-connect-on-strimzi/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/35-debezium-and-connect-on-strimzi) is a set of manifests over `examples/_k8s`: a `KafkaConnect` with a `build` section, two `KafkaConnector` resources, the access Connect needs to read the database secret and the `managed.roles` patch that gives the application role the `REPLICATION` attribute. It accompanies [Debezium and Connect on Strimzi]({{ '/docs/35-debezium-and-connect-on-strimzi/' | relative_url }}).

The applications are the unchanged outbox `order-service` and `inventory-service`, loaded into minikube. There is no pom and no Java.

```bash
examples/_k8s/cluster.sh up
cd examples/35-debezium-and-connect-on-strimzi
./verify.sh
```

{% include excalidraw.html file="35-build-and-run" alt="A KafkaConnect resource starts a build pod that downloads the plugin and checks its sha512, pushes the image to a registry inside the cluster, and the Connect pod pulls it by digest" caption="The Connect image is built from the resource and stays in the cluster" %}

## What to observe

1. **An image built from a resource.** The first run builds the Connect image inside the cluster and the pod runs it from the registry in the cluster.
2. **Two connectors with no REST write.** An order placed through the outbox and a change to an inventory row both arrive on their topics.
3. **An offset change as a resource edit.** Listing writes a ConfigMap, an alter while the connector runs is refused with a warning, and an alter while it is stopped skips the row changed in between.

## Infrastructure

The minikube profile `edp` from `examples/_k8s`: Strimzi and Kafka, CloudNativePG, otel-lgtm with the monitoring component, and a registry in `edp-kafka`.
