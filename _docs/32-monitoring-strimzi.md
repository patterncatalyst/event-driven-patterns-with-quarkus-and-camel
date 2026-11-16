---
title: "Monitoring Strimzi"
order: 32
part: running-on-kubernetes-with-strimzi
permalink: /docs/32-monitoring-strimzi/
description: "A Kafka cluster that an operator runs hides its JVMs behind resources. Brokers and controllers report through the Strimzi Metrics Reporter, the operator reports its own reconciliations, kube-state-metrics turns the status of each resource into a series, and one collector feeds them all to the Prometheus that already holds the application telemetry."
duration: "50 minutes"
---

Chapter 26 watches the flow of orders: the lag of a consumer group, the age of an event, the backlog behind a relay. Those signals say that something is stuck and not why. A broker that spends its time in garbage collection, a cluster with no active controller and a topic the operator cannot reconcile all show up there as the same thing, a number that stops falling.

On Kubernetes the cluster is also a set of resources that an operator keeps true, and each of those can be wrong without any pod failing. The signals that explain a stuck flow come from three places: the Kafka processes, the operator, and the status of the resources the operator manages.

{% include excalidraw.html file="32-metrics-path" alt="Kafka pods serve the Strimzi Metrics Reporter on port 9404, the cluster operator serves its metrics on port 8080, and kube-state-metrics watches the status of Kafka, KafkaNodePool and KafkaTopic resources; one otel-lgtm collector scrapes all three, adds the consumer group lag it asks the broker for, and sends everything on to Prometheus, which Grafana reads" caption="Figure 32.1: Pods, operator and resources report separately and land in one store" %}

## Pattern: Metrics from the operator's resources

### The problem

An operator-managed cluster has three layers that fail independently. The JVMs run or do not. The operator reconciles or does not. A resource is accepted or is stuck with a condition that says why. Reading the first layer used to mean a JMX exporter and its mapping rules (Owen Corrigan, Strimzi blog). Reading none of the second and third leaves the questions an operator is there to answer: is it reconciling, and is the topic I just declared the one Kafka has?

Chapter 29 showed the case. Raise the partitions of a declared topic in Kafka and the resource turns `Ready=False`, reason `NotSupported`, while every broker stays healthy. Nothing in the flow breaks. Only the resource knows.

### The solution

**Expose each layer through its own native metrics endpoint, scrape all of them with the collector that already carries the application telemetry, and read the status of the custom resources through kube-state-metrics.**

One store and one query language then answer questions that cross the layers: the broker took the traffic, the controller is unique, the operator reconciled, the topic is Ready, the consumer kept up.

### How Strimzi models it

Metrics for Kafka are one stanza on the `Kafka` resource. The Strimzi Metrics Reporter implements Kafka's `MetricsReporter` interface and serves Prometheus text itself, with fixed metric names and no JMX in between (Owen Corrigan, Using Strimzi Metrics Reporter to expose Kafka metrics in Prometheus format, Strimzi blog). It is switched on for brokers and controllers alike.

```yaml
spec:
  kafka:
    metricsConfig:
      type: strimziMetricsReporter
      values:
        allowList:
          - "kafka_.*"
          - "jvm_.*"
```

The operator exposes its own metrics without configuration, including reconciliation counts and durations (Strimzi, Deploying and Managing Strimzi). Resource state is the third source. Strimzi's operator used to export some of it and no longer does; the Strimzi guidance is to read it with kube-state-metrics, which can turn the status of any custom resource into series (Sebastian Gaiser, Monitoring of custom resources, Strimzi blog; Strimzi proposal 087).

## Brokers and controllers

The reporter serves on port 9404 at `/metrics` on every Kafka pod. The `allowList` is a list of regular expressions over metric names. Leave it out and Strimzi applies a curated default for the node's role; the default list on a controller holds no `kafka_controller_` and no `kafka_server_raft_` family, so the question whether there is an active controller had nothing to read. Setting the list to `kafka_.*` and `jvm_.*` answers it. A narrower list is fine if it keeps `kafka_controller_.*` and `kafka_server_raft_metrics_.*`.

The list is also the one setting that costs nothing to change. Strimzi applies a new `allowList` to running brokers and controllers without rolling them (Strimzi, Configuring Strimzi). The pods keep their names, uids and restart counts, and the endpoint changes within seconds of the patch. Adding or removing the whole `metricsConfig` is another matter: that rolls every Kafka pod.

The collector scrapes the pods with a `prometheus` receiver. The OpenTelemetry documentation calls it a drop-in replacement for a Prometheus scrape, and it takes the same `kubernetes_sd_configs`. Chapter 26 merged a second collector configuration into otel-lgtm through `OTELCOL_EXTRA_ARGS`; this one is the same mechanism and carries both the scrape and chapter 26's lag receiver.

```yaml
receivers:
  prometheus/strimzi:
    config:
      scrape_configs:
        - job_name: strimzi-kafka
          scrape_interval: 10s
          kubernetes_sd_configs:
            - role: pod
              namespaces: {names: [edp-kafka]}
          relabel_configs:
            - source_labels: [__meta_kubernetes_pod_container_port_name]
              regex: tcp-prometheus
              action: keep
            - {source_labels: [__meta_kubernetes_pod_name], target_label: pod}
            - {source_labels: [__meta_kubernetes_pod_label_strimzi_io_pool_name], target_label: node_pool}
```

Pod discovery needs a `Role` that lets the collector's service account list and watch pods in the Kafka namespace. The relabelling keeps the pods that expose the reporter's named port and carries the pod name and the node pool into every series. Names pass through the OTLP conversion unchanged and counters keep `_total`:

| Question | Series |
|---|---|
| Is a node scraped? | `up{job="strimzi-kafka"}`, by `node_pool` |
| Is this broker taking the traffic? | `kafka_server_brokertopicmetrics_bytesinpersec_total`, by `topic` |
| Is there one active controller? | `kafka_controller_kafkacontroller_activecontrollercount`, summed |

Bytes in is a counter, so a panel plots `rate(...)`. The series without a `topic` label is the broker's total. The active-controller series is 1 on the active controller, so its sum over the cluster is 1, and any other sum is the alert.

## The operator and its resources

The operator is a pod with its own endpoint, scraped the same way on port 8080. `strimzi_reconciliations_total{kind="Kafka"}` counts the reconciliations of Kafka resources, and a counter that stops climbing while a resource sits unready is the first thing to read. `strimzi_reconciliations_successful_total` and the duration histograms sit beside it.

A resource that the operator cannot reconcile is not visible there. It is visible in its status, and kube-state-metrics reads status. Its custom-resource-state configuration names a kind, a path inside it and how to turn the value into a gauge. Kube-state-metrics supports this through a configuration file and a flag that restricts it to those resources (kube-state-metrics documentation). The configuration is small: a condition per kind, with `True` read as 1 and `False` as 0.

```yaml
kind: CustomResourceStateMetrics
spec:
  resources:
    - groupVersionKind: {group: kafka.strimzi.io, version: v1, kind: KafkaTopic}
      metricNamePrefix: edp_kafkatopic
      labelsFromPath: {name: [metadata, name]}
      metrics:
        - name: condition
          each:
            type: Gauge
            gauge:
              path: [status, conditions]
              labelsFromPath: {type: [type], reason: [reason]}
              valueFrom: [status]
```

The same entry exists for the `Kafka` resource and for the node pools. Strimzi ships an example configuration with its own metric prefixes, which is the place to compare names; this one uses short prefixes that read well in a query. Kube-state-metrics runs as one Deployment with a cluster role that may list and watch the CRDs and the `kafka.strimzi.io` resources. Upstream marks the feature frozen in favour of a successor, so it will not grow.

The payoff is the invalid topic. A topic declared with three partitions and raised to six in Kafka reads `edp_kafkatopic_condition{name="edp.lab.watched",type="Ready",reason="NotSupported"} 0` within about 40 s, and every other topic keeps reading 1. A partition count cannot be lowered, so the repair is to delete the resource and declare it again, which deletes and recreates the topic. A resource that cannot be reconciled is an alert condition, and no pod ever restarts for it.

## One dashboard beside the flows

Provisioning is chapter 26's: a dashboard file in a directory that Grafana reads on start, so there is nothing to click and nothing to lose when the pod is replaced. One dashboard, `edp-strimzi`, holds a panel per question in the figure below and one for the scrape targets, each a query on a series already named here.

{% include excalidraw.html file="32-what-each-signal-answers" alt="Five signals in rows: the broker with the bytes-in family answers whether it takes the traffic, the controller with the active controller count answers whether there is exactly one, the operator with reconciliation counters answers whether it reconciles, a resource with the KafkaTopic condition answers whether the operator can reconcile it, and the flow with consumer group lag answers whether a consumer keeps up" caption="Figure 32.2: Each signal answers a question the others cannot" %}

Strimzi publishes dashboards for the reporter in its own examples. They are a good source of ideas for panels; the dashboard here is smaller and uses the names above.

```promql
sum by (topic) (rate(kafka_server_brokertopicmetrics_bytesinpersec_total{topic!=""}[1m]))
sum(kafka_controller_kafkacontroller_activecontrollercount)
sum by (kind) (rate(strimzi_reconciliations_total[5m])) * 60
min(edp_kafkatopic_condition{type="Ready"})
sum by (group) (kafka_consumer_group_lag_ratio)
sum by (job) (up)
```

The last is chapter 26's. The collector asks the broker for it through the bootstrap Service, which keeps it available when a consumer's own metric disappears with the consumer.

Two sources failing independently is a property worth keeping. Remove the `metricsConfig` and the pod series are empty, while the resource series, the operator's counters and the lag stay. A dashboard that reads from only one source loses everything in that case.

## Build, run, observe

```bash
cd examples/32-monitoring-strimzi
./verify.sh
```

Kafka comes up with the reporter on both pools, the pair of chapter 2 places ten orders, and the signals are read from Prometheus on its published port: broker traffic, the single controller, the operator's counters, the state of the resources and the dashboard. A topic is made invalid and repaired, and the `allowList` is narrowed and restored while the pods are watched; the run ends with `20/20 checks passed`.

- `METRICS=off ./verify.sh` (demo code): no `metricsConfig`, so the pod series are empty while the resource and operator series pass.
- `KEEP=1 ./verify.sh`, then `kubectl --context edp -n edp-kafka patch kafka edp --type merge -p '{"spec":{"kafka":{"metricsConfig":{"values":{"allowList":["jvm_.*"]}}}}}'`: the broker's bytes-in series stops and no pod restarts.
- `KEEP=1 ./verify.sh`, then raise the partitions of `edp.orders.placed` with `kafka-topics.sh` from the broker pod: its `edp_kafkatopic_condition` goes to 0.

## When not to use it

A cluster with one broker and one consumer rarely needs more than lag. The reporter, the scrape and kube-state-metrics are three more things to run and to keep named correctly.

The names are the reporter's, fixed and few. A narrowed `allowList` hides a signal without any error: take out the controller families and the active-controller panel is empty, which looks the same as a cluster that has no controller.

Kube-state-metrics adds a cluster role that can read every Strimzi resource, and its custom-resource configuration is yours to keep in step with the resources. A field that Strimzi renames leaves a series that quietly stops.

> **🤖 Explore with your agent**
> "In examples/32-monitoring-strimzi, list the Kafka pods in edp-kafka and read the metricsConfig of the Kafka resource edp. Query Prometheus for the up series of job strimzi-kafka and for kafka_controller_kafkacontroller_activecontrollercount, then patch the allowList to jvm_.* and read the endpoint on port 9404 of edp-broker-0 before and after. Raise the partitions of a declared KafkaTopic with kafka-topics.sh and watch edp_kafkatopic_condition for it. Say which of the three sources reports each change and which one stays silent."
> Check: the allowList patch changes the broker endpoint with the same pod uids and restart counts, the controller series sums to 1 before the narrowing, and the raised topic reads Ready 0 with reason NotSupported while edp.orders.placed stays at 1.

## References

- Strimzi, [Deploying and Managing Strimzi](https://strimzi.io/docs/operators/latest/deploying.html), the metrics sections (enabling the Metrics Reporter, the operator's metrics and kube-state-metrics), and [Configuring Strimzi](https://strimzi.io/docs/operators/latest/configuring.html), using the Strimzi Metrics Reporter (the `allowList` that applies without a rolling update)
- Owen Corrigan, [Using Strimzi Metrics Reporter to expose Kafka metrics in Prometheus format](https://strimzi.io/blog/2025/10/06/strimzi-metrics-reporter/) (Strimzi blog, 2025)
- Sebastian Gaiser, [Monitoring of custom resources](https://strimzi.io/blog/2025/10/01/monitoring-of-custom-resources/) (Strimzi blog, 2025), Strimzi [proposal 087](https://github.com/strimzi/proposals/blob/main/087-monitoring-of-custom-resources.md) and the [changelog](https://github.com/strimzi/strimzi-kafka-operator/blob/main/CHANGELOG.md) (resource state metrics removed from the operator)
- kube-state-metrics, [custom resource state metrics](https://github.com/kubernetes/kube-state-metrics/blob/main/docs/metrics/extend/customresourcestate-metrics.md)
- OpenTelemetry Collector contrib, [Prometheus receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/receiver/prometheusreceiver)
- Grafana, [docker-otel-lgtm](https://github.com/grafana/docker-otel-lgtm) (custom dashboards, `OTELCOL_EXTRA_ARGS`)
- Chapter 26 for the lag receiver and provisioned dashboards, chapter 28 for the cluster, chapter 29 for the topic resources
