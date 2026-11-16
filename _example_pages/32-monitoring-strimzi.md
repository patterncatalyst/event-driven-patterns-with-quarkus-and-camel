---
title: "Monitoring Strimzi"
order: 32
permalink: /examples/32-monitoring-strimzi/
description: "Kafka on Strimzi with the Metrics Reporter on brokers and controllers, the operator's own metrics and kube-state-metrics for resource state, all scraped by the otel-lgtm collector, with a provisioned dashboard and checks that read each signal from Prometheus."
---

[`examples/32-monitoring-strimzi/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/32-monitoring-strimzi) is a manifest overlay on the shared minikube setup in `examples/_k8s`: Kafka with the reporter on both node pools, the collector's scrape configuration, kube-state-metrics with a small custom-resource configuration, a dashboard and the `order-service` and `notification-service`. It accompanies [Monitoring Strimzi]({{ '/docs/32-monitoring-strimzi/' | relative_url }}).

It uses kustomize through `kubectl`, Strimzi, Kafka, CloudNativePG, the LGTM image, kube-state-metrics and the unchanged `order-service` and `notification-service` images.

```bash
cd examples/32-monitoring-strimzi
./verify.sh
```

{% include excalidraw.html file="32-metrics-path" alt="Kafka pods serve the Strimzi Metrics Reporter on port 9404, the cluster operator serves its metrics on port 8080, and kube-state-metrics watches the status of Kafka, KafkaNodePool and KafkaTopic resources; one otel-lgtm collector scrapes all three, adds the consumer group lag it asks the broker for, and sends everything on to Prometheus, which Grafana reads" caption="Where each signal comes from" %}

## What to observe

1. **Pods.** Both node pools are scrape targets, bytes in rises for `edp.orders.placed` after ten orders, and exactly one controller is active.
2. **The operator.** Its reconciliation counter is above zero and its pod is a scrape target.
3. **Resources.** The Kafka resource and every KafkaTopic read Ready 1; a topic whose partitions are raised in Kafka reads Ready 0 with reason `NotSupported`.
4. **No restart.** Narrowing the reporter's `allowList` changes the endpoint and leaves the pods as they were.

## Infrastructure

The cluster, PostgreSQL and LGTM bases of `examples/_k8s`, the monitoring base, the reporter component, one `KafkaTopic`, and the `order-service` and `notification-service` deployments.
