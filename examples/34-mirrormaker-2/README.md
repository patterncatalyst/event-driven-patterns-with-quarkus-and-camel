# 34 MirrorMaker 2

A second Kafka cluster, `edp-dr`, runs beside `edp` in the same namespace, and a Strimzi `KafkaMirrorMaker2` copies the event catalog's topics and the consumers' group offsets from `edp` to `edp-dr`. This example fails the example 02 `notification-service` over to `edp-dr` by changing only its bootstrap address, and checks where the consumer resumes.

Manifests only: a kustomize overlay on `examples/_k8s` (the Kafka, PostgreSQL, LGTM, monitoring and metrics bases and the example 02 deployments) and two files for the second cluster and the mirror. No Java and no pom. The example 02 images are built from their own Containerfiles, unchanged. The namespaces carry no Pod Security labels in this example.

## What the overlay changes

| File | Purpose |
|---|---|
| `kustomization.yaml` | Builds `base/` |
| `base/kustomization.yaml` | The bases and components of `../_k8s`, the example 02 deployments, the two files below, and the dashboard of this example; kept apart so the demo overlay can build on it |
| `base/edp-dr.yaml` | The `Kafka` `edp-dr` and its one dual-role `KafkaNodePool` `dual` (1 GiB, 512 MiB heap) |
| `base/mirrormaker2.yaml` | The `KafkaMirrorMaker2` `edp-mm2`: target `edp-dr`, one mirror from `edp`, `IdentityReplicationPolicy` on both connectors, checkpoints and group syncs every 5 s, `offset.lag.max: 0` |
| `base/dashboards/edp-mirrormaker2.json` | A Grafana dashboard of connector count, connectors running and the source group's lag |
| `base/lgtm-dashboards-patch.yaml` | Turns the `dashboards` volume of the monitoring component into a projection of its ConfigMap and the one for this dashboard |
| `images.txt` | The two example 02 images loaded into the node |
| `mm2_state.py` | Prints a connector's state and its tasks' states from `kubectl get kafkamirrormaker2 -o json` (`python3 -I`) |
| `demo-offset-sync-off/` | Demo code, selected by the switch below; links `images.txt` so the images load |

The example 02 pair publishes and reads `edp.orders.placed`, which is in `Topics.ALL`. Both clusters keep broker auto-creation on.

## Run

```bash
../_k8s/cluster.sh up      # once: profile `edp` and the platform tier (see ../_k8s/README.md)
./verify.sh
KEEP=1 ./verify.sh         # leave the namespaces in place
```

`verify.sh` deletes and recreates the namespaces `edp`, `edp-kafka` and `edp-lgtm`, applies the overlay with `--server-side`, and ends with `18/18 checks passed`. Both clusters and the mirror are reached through `kubectl exec` and loopback NodePorts only. The order ids are `O-3401` to `O-3410`.

## Demo switches

Demo code, labelled as such in `verify.sh`, the overlay and the chapter.

| Switch | Effect | Failing set |
|---|---|---|
| `OFFSET_SYNC=off ./verify.sh` | `demo-offset-sync-off/` sets `sync.group.offsets.enabled: "false"` on the checkpoint connector with a JSON patch, so `edp-dr` never gets the group | Checks 9, 13 and 16 (15/18): no group appears on `edp-dr` before the failover, so the delta checks fail and the earlier orders are notified twice; the records, the connectors and the monitoring checks pass |

The patch is a JSON patch because kustomize replaces a whole list of a custom resource when it merges one, and `mirrors` is a list.

## What verify.sh does

1. Checks that both Kafka clusters and the mirror are Ready, and that the checkpoint connector's setting is what the switch says (preconditions).
2. Checks that both connectors and their tasks are `RUNNING`.
3. Places five orders on `edp` and waits for their notifications, then checks the copy on `edp-dr`: same topic name, same number of records, and the internal topics where they are expected.
4. Checks that the group `notification-service` exists on `edp-dr` with the committed offsets it has on `edp`.
5. Stops `notification-service` (precondition), places five more orders, checks that nobody is notified and that `edp-dr` holds all ten records, and that the group there did not move.
6. Starts `notification-service` with `CAMEL_COMPONENT_KAFKA_BROKERS` set to the `edp-dr` bootstrap address and nothing else changed, and checks the five orders are notified, the group on `edp-dr` advanced by exactly five and every order has one notification.
7. Checks that the mirror's worker reports its two connectors through the monitoring component and that Grafana serves the dashboard of this example.

## Tests and checks in detail

There is no `mvn verify`: the example has no Java. Offsets are read with `kafka-consumer-groups.sh` and `kafka-get-offsets.sh` in `edp-broker-0` and `edp-dr-dual-0`; the committed offset of a partition the group has never committed is counted as 0. `notification-service` stores one row for every record it reads, so a count of notifications per order shows a record read twice, and the difference of the committed offsets shows how many records the group read.

1. Precondition: `edp` and `edp-dr` are Ready.
2. Precondition: `edp-mm2` is Ready.
3. Precondition: `sync.group.offsets.enabled` in the checkpoint connector is `true` (`false` under `OFFSET_SYNC=off`).
4. `edp->edp-dr.MirrorSourceConnector` and `edp->edp-dr.MirrorCheckpointConnector` and one task each are `RUNNING`.
5. `O-3401` to `O-3405` are accepted (201) and their notifications arrive.
6. `edp.orders.placed` exists on `edp-dr`.
7. The end offsets of `edp.orders.placed` sum to 5 on both clusters.
8. `mm2-offset-syncs.edp-dr.internal` exists on `edp` and `edp.checkpoints.internal` on `edp-dr`.
9. The group `notification-service` exists on `edp-dr` and its committed offsets sum to 5 there and on `edp`.
10. Precondition: the `notification-service` pods are gone.
11. `O-3406` to `O-3410` are accepted (201) and no notification exists for them.
12. The end offsets sum to 10 on both clusters.
13. The group on `edp-dr` is at 5 and not ahead of the records there.
14. `notification-service` is Ready again with `CAMEL_COMPONENT_KAFKA_BROKERS` equal to the `edp-dr` bootstrap address.
15. The notifications of `O-3406` to `O-3410` arrive.
16. The group on `edp-dr` reaches 10 and has advanced by exactly 5 since check 13, and each of `O-3401` to `O-3410` has exactly one notification.
17. `kafka_connect_connect_worker_metrics_connector_count{strimzi_kind="KafkaMirrorMaker2"}` is 2 in Prometheus.
18. Grafana serves the dashboard `edp-mirrormaker2`.

Checks 9, 13 and 16 depend on the group offset sync. They are the negative witness: with the sync off the group never appears on `edp-dr`.

## Measurements

`verify.sh` prints each value on a `measured:` line; ranges are over the verified runs.

- The `Kafka` `edp-dr` Ready: 33 to 37 s after its creation. `edp-mm2` Ready: 68 to 86 s.
- Group offsets on `edp-dr` with 5 s intervals and `offset.lag.max: 0`: 6 to 7 s after the five orders were notified and the record counts on both clusters matched. With the default `offset.lag.max` of 100, a cluster spike found the group still behind the source after 140 s.
- From the change of the bootstrap address to the five orders notified: 7 to 8 s.
- Deleting the mirror, both clusters, the apps and the three namespaces: 30 to 68 s.
