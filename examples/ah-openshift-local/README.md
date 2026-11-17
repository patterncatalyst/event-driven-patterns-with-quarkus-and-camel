# Appendix H: OpenShift Local

The outbox `order-service` runs on OpenShift Local (CRC) with Strimzi and CloudNativePG, as a demonstration of the same code on an on-premises distribution. Two operators are installed from OpenShift's catalogs at pinned versions, the unchanged `order-service` image is built with buildah and copied into the cluster's registry with skopeo, and an order placed through an edge-TLS Route reaches `edp.orders.placed` through Debezium.

It is shell scripts and kustomize manifests: no Java, no pom, no Helm and no S2I. The application is the outbox `order-service`, built from its own Containerfile (`08-transactional-outbox/order-service/Containerfile`) with no change. The Kafka and PostgreSQL manifests are the ones in `examples/_k8s`, with the namespace set by kustomize.

## Prerequisites

- OpenShift Local running, with the context `crc-admin`. The scripts never start, stop or configure CRC: `crc setup` and `crc start` are yours. `crc-check.sh` refuses to continue otherwise.
- `oc`, `buildah`, `skopeo`, `podman` (for `rmi`), `jq`, `curl` and `docker` (only for the stack guard) on the host. Docker Engine is not used for anything else.
- No Docker Compose project and no minikube node running: `scripts/stack-guard.sh --crc` refuses both.
- Network access for the Debezium plugin (Maven Central) and for the UBI 10 and Maven downloads of the image build.

## Run

```bash
cd examples/ah-openshift-local
./install.sh
./build-and-copy.sh
./deploy.sh
./verify.sh
./teardown.sh
```

Each script starts from nothing and refuses to continue over leftovers: `install.sh` stops when the namespace `edp` or `edp-operators` exists. `verify.sh` places the order `O-4301`, so it runs once per install.

| Script | Does |
|---|---|
| `crc-check.sh` | Refuses unless CRC is Running and the context is `crc-admin`; sourced by the others |
| `install.sh` | Namespaces, OperatorGroup, the two Subscriptions (Manual, `startingCSV`, only the named InstallPlan approved), then Kafka, the PostgreSQL `Cluster` and `KafkaConnect` with an `ImageStream` build; waits for each |
| `build-and-copy.sh` | `buildah build`, then `skopeo copy --preserve-digests` to `edp/order-service:08`; writes `.state/order-service.digest` |
| `deploy.sh` | `order-service` by digest, then the `order-outbox` connector |
| `verify.sh` | The checks below |
| `teardown.sh` | Removes everything of the example, prints a leftover inventory, fails if anything remains |

## Layout

| Path | Purpose |
|---|---|
| `pins.env` | The namespaces, the package, channel, catalog and CSV of each operator, the image names, the order id. The Debezium plugin pin is in `examples/_k8s/versions.env` |
| `lib.sh` | Check helpers, the OLM install function, the ingress CA fetch, the digest readers |
| `operators/` | The two namespaces and the OperatorGroup. The Subscriptions are written by `install_operator` from `pins.env` |
| `platform/` | Kustomize over `examples/_k8s/kafka` and `examples/_k8s/postgres`, plus the `ImageStream`, the Role that lets Connect read one secret and the `KafkaConnect` |
| `deploy/` | `order-service` Deployment, Service and Route. `deploy.sh` applies it through an overlay under `.state/` that sets the image digest |
| `connector/` | The `order-outbox` `KafkaConnector` |
| `.state/` | Written at run time and ignored by git: the ingress CA (public), the digest, the build log |

`oc kustomize platform`, `deploy` and `connector` render offline. Every `oc` call names its namespace with `-n` or is cluster-scoped; applies are `oc apply --server-side`.

## Pins

| Component | Pin |
|---|---|
| Strimzi operator | `strimzi-kafka-operator`, catalog `community-operators`, channel `strimzi-1.x`, CSV `strimzi-cluster-operator.v1.3.1` |
| CloudNativePG operator | `cloudnative-pg`, catalog `certified-operators`, channel `stable-v1`, CSV `cloudnative-pg.v1.30.1` |
| Kafka | 4.3.2, KRaft, one controller and one broker |
| PostgreSQL | 18.6 (`ghcr.io/cloudnative-pg/postgresql`, tag in `examples/_k8s/postgres/cluster.yaml`) |
| Debezium PostgreSQL plugin | 3.7.1.Final, sha512 in `platform/kafkaconnect.yaml` and `examples/_k8s/versions.env`; `install.sh` stops when they differ |
| CRC | 2.64.0, OpenShift 4.22.14, held: a newer CRC needs a new binary and bundle |

CloudNativePG is in the certified catalog on this cluster, published by EDB with the upstream images; no fee or key applies. AMQ Streams is Red Hat's supported downstream of Strimzi, sold as a subscription, and is not installed.

## Tests and checks in detail

There is no `mvn verify`. `verify.sh` reads effects and ends with `20/20 checks passed`:

1. OpenShift Local is running and the context is `crc-admin`.
2. The Strimzi CSV is `Succeeded` and is the `installedCSV` of its Subscription.
3. The same for CloudNativePG.
4. Both Subscriptions are `Manual`, and the only approved InstallPlans install the two pinned CSVs.
5. Kafka `edp` is Ready on 4.3.2.
6. The `Cluster` is Ready, `wal_level` is `logical` and the role `edp` has `REPLICATION` (read over TCP as the application role; the password goes through stdin).
7. `KafkaConnect` is Ready, its pod runs an image from the `ImageStream` `edp-connect`, and `/connector-plugins` lists `PostgresConnector`.
8. The connector and its task are `RUNNING` in the Connect REST API, read through `oc exec`. The resource's status is not used.
9. The manifest digest in the `ImageStream` equals the one of the local buildah image, read with `skopeo inspect containers-storage:`.
10. The Deployment references the image by that digest and the running pod reports the same digest.
11. Every running pod in `edp` and `edp-operators` carries `openshift.io/scc: restricted-v2`. Build pods belong to the platform and are skipped.
12. `order-service` runs as a user inside the project's `openshift.io/sa.scc.uid-range` and not as 185, the user of the image.
13. Negative witness: a Deployment with `privileged: true` is not admitted; the ReplicaSet reports `unable to validate against any security context constraint`. The Deployment is deleted at once.
14. The Route answers `/q/health/ready` with 200 through `curl --cacert` with the ingress CA. No `-k`, no `--resolve`.
15. Plain HTTP to the Route is redirected to HTTPS.
16. Negative witness: `curl` without `--cacert` fails with exit code 60, the certificate check.
17. `POST /orders` for `O-4301` through the Route returns 201.
18. One record on `edp.orders.placed`, keyed `O-4301`, with the header `eventType:OrderPlaced`.
19. The order row exists and the outbox table is empty.
20. A second `O-4301` returns 409 and the topic still holds one record after eight seconds.

The connector is declared by `connector/order-outbox.yaml` and the image the pod runs is addressed by digest, so check 9 and check 10 together show that the bytes running are the bytes built on the host.

## Teardown

`teardown.sh` deletes, waiting for each: connector, application, `KafkaConnect`, Kafka and its node pools, the `Cluster`, `ImageStream`s, build configs and builds, Subscriptions, CSVs, InstallPlans, the OperatorGroup, the 21 CRDs of the two operators, the 8 ClusterRoles that OLM leaves (`cnpg-*-editor-role`, `cnpg-*-viewer-role`, `strimzi-kafka-broker`, `strimzi-kafka-client`), both namespaces and the `Retain` volumes claimed from `edp`. Then `oc adm prune images --keep-younger-than=0` with the registry URL and the CA removes the image objects and registry blobs. The prune is cluster-wide, so a dry run lists the candidates first and the script stops unless every one is an image of `edp`. Last, `podman rmi` removes the local image and `.state/` is deleted.

The inventory prints one line per group (namespaces, Subscriptions and CSVs, CRDs, ClusterRoles, volumes, image objects, the local image) and `teardown.sh` exits non-zero if any is not empty. It never removes a finalizer: when a delete does not finish, the script stops and names the resource. The base images `ubi10/openjdk-25` and `-runtime` stay in rootless containers-storage, where buildah keeps them. The scripts remove the volume objects and do not inspect the node's disk behind them. `docker` is not touched, and the volume `edp-ollama-models` is not involved.

## Measurements

Wall times of one clean sequence on an empty cluster. The build downloads Maven artefacts; the UBI 10 base images were already in rootless storage:

| Step | Wall time |
|---|---|
| `install.sh` | 138 s (both operators Succeeded 53 s after the first Subscription; Kafka, the `Cluster` and Connect, build included, Ready 84 s after the apply) |
| `build-and-copy.sh` | 54 s (build 50 s, copy 2 s) |
| `deploy.sh` | 12 s |
| `verify.sh` | 45 s |
| `teardown.sh` | 44 s |

## Limits

- The token of the `builder` service account is a command-line argument of `skopeo` for the length of one copy, as `--dest-registry-token` requires; on a shared host another local user could read it from the process list. It is valid for 15 minutes and is neither written to a file nor printed.
- `verify.sh` is not idempotent: the order `O-4301` can be placed once per install.
- OpenTelemetry is off on this cluster. There is no LGTM here.
- The build pod of `KafkaConnect` runs under the platform's `privileged` constraint; that is how OpenShift builds run, not a setting of this example.
