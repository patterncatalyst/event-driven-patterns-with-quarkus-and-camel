# 35 Debezium and Connect on Strimzi

Kafka Connect and two Debezium connectors run as resources on the Strimzi cluster of Part 6. A `KafkaConnect` builds its own image with the PostgreSQL connector plugin and pushes it to a registry inside the cluster. Two `KafkaConnector` resources declare the chapter 08 outbox connector and the chapter 10 inventory connector against CloudNativePG, and the inventory connector's offsets are listed, refused, altered and resumed through its resource.

It is manifests and a `verify.sh`: no Java. The applications are the chapter 08 `order-service` and the chapter 09 `inventory-service`, built from their own Containerfiles and loaded into the node unchanged. The connectors are the chapter 08 and chapter 10 configurations with the host and the credentials replaced and, for the inventory connector, a different `topic.prefix` (see the chapter).

## What runs

| Namespace | Holds |
|---|---|
| `edp-kafka` | Strimzi operator, Kafka `edp`, the registry (fixed ClusterIP, emptyDir), `KafkaConnect edp-connect` and its build pod, the two `KafkaConnector` resources |
| `edp` | CloudNativePG `Cluster edp-pg` with the `edp` role declared with `replication: true`, `order-service` (NodePort 30080), `inventory-service` (NodePort 30082), a Role and RoleBinding that let Connect read the secret `edp-pg-app` |
| `edp-lgtm` | otel-lgtm and kube-state-metrics from `examples/_k8s/monitoring`, which scrape the Connect pod |

No Pod Security labels are set on the namespaces, so the build pod runs unlabelled.

## Layout

| Path | Purpose |
|---|---|
| `kustomization.yaml` | The platform, the registry, the monitoring component, the two services and the `managed.roles` patch on the Cluster |
| `connect-secret-access.yaml` | Role and RoleBinding for the Connect ServiceAccount, one secret, get only |
| `apps/` | `order-service` and `inventory-service` Deployments and Services |
| `connect/kafkaconnect.yaml` | `KafkaConnect` with `build`: plugin archive, sha512, output to the registry; the config provider; the Metrics Reporter |
| `connectors/` | `order-outbox` and `inventory-cdc` as `KafkaConnector` resources |
| `demo/plugin-sha-wrong/` | Demo code: the same `KafkaConnect` with a checksum that does not match |
| `tables/inventory.sql` | The chapter 10 legacy tables, created as the role `edp` |
| `tools/read.py` | Reads consumer output and offset JSON for the checks |
| `images.txt` | The two images `verify.sh` builds and loads |

`verify.sh` applies `connect/` and `connectors/` itself, after the platform is up, so that it can time the build and create the captured tables first. A connector whose table does not exist yet, or is owned by another role, fails; the tables are created as `edp`.

## Run

```bash
examples/_k8s/cluster.sh up
cd examples/35-debezium-and-connect-on-strimzi
./verify.sh
KEEP=1 ./verify.sh   # leave the namespaces in place
```

The first run builds the Connect image inside the cluster. `verify.sh` starts from fresh namespaces every time, so the registry is empty and the image is built again. It ends with `25/25 checks passed`.

| Setting | Default | Effect |
|---|---|---|
| `PLUGIN_SHA` | `pinned` | Demo code. `wrong` applies a checksum that does not match the plugin archive (the negative witness) |

Order id: `O-3501`. Nothing in `verify.sh` writes through the Connect REST API; `connect_get` only reads.

## What verify.sh does

1. Preconditions: Kafka Ready, the `edp` role has `REPLICATION`, the registry holds no repository (checks 1 to 3).
2. Applies `KafkaConnect`, waits for it and checks the image in the registry, the Ready condition, the image the pod runs and the plugin it lists (4 to 6).
3. Creates the inventory tables, applies both `KafkaConnector` resources and checks Ready, RUNNING tasks, credentials by reference, slots and publications (7 to 9).
4. Places `O-3501`: one keyed record with the `eventType` header, an empty outbox table, a refused duplicate (10 to 13).
5. Reads the snapshot, one update and its document (14 to 16).
6. Lists the offsets, tries an alter while running, stops, alters to the end of the WAL, resumes and reads what arrives (17 to 23).
7. The connector status metric in Prometheus and the unchanged images (24, 25).

## Tests and checks in detail

There is no `mvn verify`; the example has no Java. The checks read effects:

- The registry's tag list for `edp/connect` holds the tag the `KafkaConnect` pushes, and the registry was empty before. The Connect pod's image starts with the registry address, and the REST API lists `PostgresConnector`.
- Both connectors are `RUNNING` with one `RUNNING` task each. The resources hold `${secrets:edp/edp-pg-app:password}` and not the password itself.
- `pg_replication_slots` lists `edp_inventory` and `edp_order_outbox`, both active. Each publication holds only its own tables.
- `O-3501` leaves one record on `edp.orders.placed` keyed `O-3501` with the header `eventType: OrderPlaced`. The outbox table is empty and the order row exists. A second `O-3501` is refused with 409 and the topic still holds one record after a few seconds.
- The snapshot is five `r` records. An update of `SKU-3` to 79 arrives as a `u` record and as the document `SKU-3` with `quantityOnHand` 79.
- Listing offsets writes `offsets.json` to the ConfigMap `inventory-cdc-offsets`, with an `lsn`, and the annotation disappears.
- An alter while the connector runs is refused: since the annotation was set, the operator log holds `Altering offsets of connector inventory-cdc` followed by the warning `Connector is not in STOPPED state` (it writes no Kubernetes event), the annotation still reads `alter`, and the offsets read from Connect are unchanged. `verify.sh` removes the annotation before the next step.
- With `spec.state: stopped` the REST status is `STOPPED` with no task. `SKU-4` is set to 401 while it is stopped. An alter to the current WAL position, from the ConfigMap `inventory-cdc-offsets-alter`, is read back from Connect as that position. After `spec.state: running`, the update of `SKU-5` to 502 arrives and the update of `SKU-4` to 401 never does.
- `kafka_connect_connector_metrics_status{connector="inventory-cdc",status="running"}` is 1 in Prometheus.
- The image IDs on the node equal the ones built from the chapter 08 and 09 Containerfiles.

## Experiments

Each run starts from fresh namespaces.

| Switch | Effect | Failing checks | Measured result |
|---|---|---|---|
| `PLUGIN_SHA=wrong ./verify.sh` (demo code) | The checksum does not match the archive, the build fails, no image is pushed and Connect never starts | 4 to 9, 11, 13 to 24 (6/25 pass) | Connect is `NotReady` and the build pod `Failed`; the run takes about 200 s |

Checks 10 and 12 pass because the order is stored and the outbox row is removed in the same transaction whether or not Connect runs, and 25 passes because the images do not depend on Connect. The `KafkaConnect` condition reads `NotReady`, `The Kafka Connect build failed`; the build pod is `Failed` and its log holds `sha512sum: WARNING: 1 computed checksum did NOT match` and `Error: building at STEP`. No image reaches the registry.

## Measurements

| What | Value |
|---|---|
| `KafkaConnect` Ready after the apply, build included | 82 to 99 s (the build pod itself ran about 25 s in a cluster spike; it is gone once Connect is Ready) |
| Annotation removed after a list | 1 to 3 s |
| Alter while stopped, applied | 0 to 3 s |
| Namespace teardown with Connect and both connectors | 42 to 48 s |

## Versions and licences

The Connect image starts from Strimzi's published Kafka image, not UBI 10, because Strimzi needs its own image layout. The plugin version and checksum are in `connect/kafkaconnect.yaml`. The registry, Debezium and Strimzi components are Apache-2.0 and need no account or key.
