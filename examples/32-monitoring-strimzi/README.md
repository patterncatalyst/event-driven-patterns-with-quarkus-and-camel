# 32 Monitoring Strimzi

Kafka run by Strimzi reports on three layers that fail independently: the brokers and controllers (the Strimzi Metrics Reporter), the cluster operator, and the status of the resources the operator manages (kube-state-metrics). This example turns all three on beside the chapter 2 services, scrapes them with the otel-lgtm collector and reads the results from Prometheus: broker traffic, the single active controller, the operator's reconciliations, the Ready condition of the Kafka and KafkaTopic resources, consumer group lag and a provisioned dashboard. It also makes a KafkaTopic invalid and changes the reporter's `allowList` while watching the pods.

Manifests only: a kustomize overlay on `examples/_k8s` and no Java or pom. The chapter 2 images are built from their own Containerfiles, unchanged. The namespaces carry no Pod Security labels.

## What the overlay changes

| File | Purpose |
|---|---|
| `kustomization.yaml` | Builds `base/` and lists the components `../_k8s/monitoring` (collector scrape configuration, kube-state-metrics, the `edp-strimzi` dashboard) and `../_k8s/kafka-metrics` (the reporter, with `allowList` `kafka_.*` and `jvm_.*`) |
| `base/kustomization.yaml` | The bases of `../_k8s`, the chapter 2 deployments, `topics/` and the Kafka patch; kept apart so that the demo overlay can build on it |
| `base/kafka-patch.yaml` | `entityOperator.topicOperator.reconciliationIntervalMs: 15000`, so a KafkaTopic has a Ready condition |
| `base/topics/kafkatopics.yaml` | One `KafkaTopic`, `edp.orders.placed`, 3 partitions |
| `demo-metrics-off/` | Demo code: the base with the monitoring component and without the reporter component |
| `images.txt` | The two chapter 2 images loaded into the node |
| `kafkapods.py`, `kafkatopic_state.py` | Pod names, uids and restart counts; KafkaTopic conditions (`python3 -I`). Prometheus is queried with `../_k8s/tools/prom.py` |

## Run

```bash
../_k8s/cluster.sh up      # once: profile `edp` and the platform tier (see ../_k8s/README.md)
./verify.sh
KEEP=1 ./verify.sh         # leave the namespaces in place
```

`verify.sh` deletes and recreates the namespaces `edp`, `edp-kafka` and `edp-lgtm`, applies the overlay with `--server-side`, and ends with `20/20 checks passed`. Prometheus is read on its published NodePort and the pods with `kubectl exec`; nothing is forwarded.

## Demo switch

Demo code, labelled as such in `verify.sh`, the overlay and the chapter.

| Switch | Effect | Failing set |
|---|---|---|
| `METRICS=off ./verify.sh` | `demo-metrics-off/` builds the cluster with no `metricsConfig` | Checks 3, 4, 6, 7, 18, 19 and 20 (13/20): the reporter checks and the `allowList` checks. The operator, resource-state, lag and dashboard checks pass, which shows the sources are independent |

## Tests and checks in detail

Metric names are those the reporter, the operator and kube-state-metrics produce after the OTLP conversion; counters keep `_total`. Prometheus reads every 10 s, so each Prometheus check polls within 120 s. `edp.lab.watched` is a scratch KafkaTopic (demo code of the verification, not part of the catalog).

1. Precondition: the Kafka resource has `metricsConfig.type: strimziMetricsReporter` (`none` under `METRICS=off`).
2. Precondition: the Kafka pods are one broker and one controller.
3. The broker and the controller answer on port 9404 with `jvm_` families.
4. Both node pools are scrape targets: `count by (node_pool) (up{job="strimzi-kafka"} == 1)` has two series.
5. Orders `O-3201` to `O-3210` are placed (201) and `O-3210` is notified.
6. `kafka_server_brokertopicmetrics_bytesinpersec_total` for `edp.orders.placed` is above its value before the orders.
7. `sum(kafka_controller_kafkacontroller_activecontrollercount)` is 1.
8. The operator is a scrape target with `up` 1.
9. `strimzi_reconciliations_total{kind="Kafka"}` is above 0.
10. `edp_kafka_condition{name="edp",type="Ready"}` is 1.
11. Every KafkaTopic that exists reads `edp_kafkatopic_condition{type="Ready"}` 1.
12. `kafka_consumer_group_lag_ratio{group="notification-service"}` has series.
13. Grafana returns the dashboard `edp-strimzi` by uid.
14. Precondition: the scratch topic `edp.lab.watched` reads Ready 1.
15. After its partitions are raised from 3 to 6 in Kafka, the topic reads Ready 0 with reason `NotSupported` within 60 s.
16. `edp.orders.placed` still reads Ready 1 at that moment.
17. After the resource is deleted and declared again, the topic reads Ready 1 with 3 partitions.
18. After the `allowList` is patched to `kafka_network_.*` and `jvm_.*`, the broker endpoint no longer holds the bytes-in family (it held it before the patch).
19. Once the operator has reconciled the change (the `Kafka` resource is Ready with `observedGeneration` equal to `generation`), the Kafka pods keep their names, uids and restart counts.
20. After the `allowList` is restored the family is back and, once the resource is reconciled again, the pods are still unchanged.

In-run changes to the Kafka resource use `kubectl patch --type merge`; the initial apply is server-side.

## Measurements

`verify.sh` prints the two timings it takes on `measured:` lines; the other two come from the cluster spike and were measured the same way.

| What | Value |
|---|---|
| Apply to the first broker sample in Prometheus | about 80 s on a warm node (38 s to the broker pod, 38 s from there to the first `up` 1) |
| Topic raised from 3 to 6 partitions to Ready 0 with `NotSupported` | 18 to 39 s over six runs (the Topic Operator reconciles every 15 s) |
| `allowList` patched to the family gone from the endpoint | 1 to 3 s, pods unchanged |
| `metricsConfig` removed (`METRICS=off`), pods rolled to Ready | about 75 s; restoring it rolls them again |
| Deleting the topics, Kafka and the three namespaces | 38 to 45 s |
