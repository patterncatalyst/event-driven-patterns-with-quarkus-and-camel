# Part 6 substrate: one minikube profile for examples 28 to 35

Examples 28 to 35 are manifest-only overlays over the directories here. They deploy the applications of examples 02, 03 and 04 unchanged, on Kafka run by Strimzi, PostgreSQL run by CloudNativePG and the same otel-lgtm image the compose stack uses.

## Versions

Every pin lives in `versions.env`; `tools.sh` checks the binaries on `PATH` against it and downloads nothing.

| Component | Version |
|---|---|
| minikube | v1.39.0 (docker driver, containerd runtime) |
| Kubernetes | v1.37.2 |
| kubectl | v1.37.2 (its built-in kustomize; no Helm, no standalone kustomize) |
| Strimzi | 1.3.1, Kafka 4.3.2 in KRaft mode |
| CloudNativePG | 1.30.1, operand `18.6-202611090812-standard-trixie` |
| otel-lgtm | `grafana/otel-lgtm:0.41.0` |
| kube-state-metrics | v2.20.0 (index digest pinned), with our own `CustomResourceStateMetrics` configuration |
| In-cluster registry | CNCF Distribution `registry:3.1.2` (index digest pinned) |
| Buildah image (Strimzi KafkaConnect builds) | `quay.io/strimzi/buildah:1.3.1`, set by the 1.3.1 operator, recorded in `versions.env` |
| Debezium PostgreSQL plugin | `3.7.1.Final` archive from Maven Central, sha512 in `versions.env` |

CloudNativePG 1.30.1 lists Kubernetes 1.37 as "Tested, but not supported". Its operator and cluster ran without problems on it here, so the pin stays.

## cluster.sh

```bash
examples/_k8s/cluster.sh up      # profile, loopback ports, vendor manifests, CloudNativePG operator
examples/_k8s/cluster.sh status  # profile, ports, namespaces
examples/_k8s/cluster.sh down    # minikube delete -p edp
```

`up` is idempotent. It refuses to run while a compose project, another minikube profile or CRC is running (`scripts/stack-guard.sh --minikube edp`). When the profile does not exist yet, it checks with `ss -ltnH` that every NodePort below is free on the host, then creates the profile with 6 CPUs, 10 GiB of memory, a 30 GB disk and `--insecure-registry=10.96.100.50:5000`, and asserts with `docker port edp` that every published port is bound to 127.0.0.1 only. It downloads the Strimzi and CloudNativePG release manifests next to their kustomizations (`strimzi/strimzi-cluster-operator-1.3.1.yaml`, `platform/cnpg/cnpg-1.30.1.yaml`; both gitignored), refuses a file whose sha256 differs from `versions.env`, applies the CloudNativePG platform with `--server-side` and waits for the operator.

The in-cluster registry (`registry/`) speaks plain HTTP at a fixed ClusterIP, `10.96.100.50:5000`. `minikube start --insecure-registry=10.96.100.50:5000` makes minikube write `/etc/containerd/certs.d/10.96.100.50:5000/hosts.toml` in the node, so containerd pulls from it over HTTP. A CIDR as the flag value (`10.96.0.0/12`) does not work: containerd looks up the registry host and port, so the file lands under a directory named after the CIDR and never matches. `cluster.sh up` checks for the host-and-port file on every run and, when a profile was created without the flag, writes the same `hosts.toml` into the node. Both are settings of the profile, not of the host.

Nothing is forwarded, by any command. Services are reached through the NodePorts published at profile creation, or with `kubectl exec`.

## NodePorts

All on 127.0.0.1.

| Port | Service |
|---|---|
| 30080 | order-service of sets 02 and 03; shipping-service of set 04 |
| 30082 | notification-service of sets 02 and 03; order of set 04 |
| 30084 | third app port, free for an example |
| 30300 | Grafana |
| 30320 | Tempo |
| 30100 | Loki |
| 30909 | Prometheus |

## Fresh state

Every `verify.sh` starts with `k8s_fresh`. It deletes the KafkaConnectors and KafkaRebalances first, then KafkaConnect and KafkaMirrorMaker2 and waits until their pods and the build pod are gone (a worker holding a replication connection to PostgreSQL kept the CNPG instance pod terminating for 195 s in the spike), then the KafkaTopics, every Kafka resource and the node pools, then the Deployments and Jobs in `edp`, then the CloudNativePG `Database` resources and the `Cluster`, waiting until its instance pod is gone, then the namespaces `edp`, `edp-kafka` and `edp-lgtm`, each with a timeout that fails loudly; a stuck finalizer stops the run and is never stripped. It then asserts that no namespace is Terminating and no volume is still bound, applies the example with `kubectl apply --server-side --field-manager=edp-verify -k .` and waits in tiers: Strimzi operator, every Kafka resource (`kafka_ready <name>`), every KafkaConnect and KafkaMirrorMaker2 (420 s, because a build runs first), CloudNativePG cluster and databases, otel-lgtm, kube-state-metrics and the registry if present, then every Deployment in `edp` and the `APP_PORTS` NodePorts (default `30080 30082`). In-run changes to a Kafka resource use `kubectl patch --type=merge`. `k8s_teardown` in the EXIT trap deletes the namespaces again unless `KEEP=1`. What stays per profile: CRDs, cluster-scoped RBAC, the CloudNativePG operator in `cnpg-system` and the node's image cache.

## Namespaces

| Namespace | Holds |
|---|---|
| `cnpg-system` | CloudNativePG operator (platform, applied by `cluster.sh up`) |
| `edp-kafka` | Strimzi operator, Kafka `edp`, node pools, KafkaTopics; with the r12 bases also Connect, MirrorMaker 2, a second Kafka cluster, Cruise Control resources and the registry |
| `edp` | CloudNativePG `Cluster edp-pg`, the applications |
| `edp-lgtm` | otel-lgtm; with `monitoring` also kube-state-metrics |

## Bases an example lists in `resources:`

Paths are from `examples/NN-slug/`.

| Directory | Content |
|---|---|
| `../_k8s/namespaces` | the three namespaces |
| `../_k8s/strimzi` | Strimzi operator, subjects patched to `edp-kafka` |
| `../_k8s/strimzi-restricted` | the same with `STRIMZI_POD_SECURITY_PROVIDER_CLASS=restricted` (list one of the two) |
| `../_k8s/kafka` | Kafka `edp` plus node pools `controller` and `broker`, auto-create on |
| `../_k8s/postgres` | `Cluster edp-pg` (database `edp`) and `Database` resources `edp03`, `edp04` |
| `../_k8s/lgtm` | otel-lgtm Deployment and Service |
| `../_k8s/apps/02`, `apps/03`, `apps/04` | the applications; 04 includes the topics Job |
| `../_k8s/registry` | CNCF Distribution registry in `edp-kafka`, emptyDir, Service with the fixed ClusterIP `10.96.100.50:5000` (build output for `KafkaConnect`) |

Two more are kustomize Components, listed under `components:` in the overlay (a Component patches the resources the overlay already holds):

| Directory | Content |
|---|---|
| `../_k8s/kafka-metrics` | `metricsConfig: strimziMetricsReporter` on Kafka `edp` with `allowList: ["kafka_.*", "jvm_.*"]` (the default list has no controller or raft families); brokers and controllers serve `/metrics` on port 9404 |
| `../_k8s/monitoring` | a second collector configuration for otel-lgtm through `OTELCOL_EXTRA_ARGS` (Prometheus receiver with `kubernetes_sd` over the pods in `edp-kafka` that expose the `tcp-prometheus` port, the operator, kube-state-metrics, and the `kafka_metrics` lag receiver), a ServiceAccount and Role so lgtm may list pods in `edp-kafka`, kube-state-metrics with our own configuration for the Strimzi resource conditions (`edp_kafka_condition`, `edp_kafkanodepool_replicas`, `edp_kafkatopic_condition`, `edp_kafkarebalance_condition`, `edp_kafkaconnector_condition`, `edp_kafkaconnect_condition`, `edp_kafkamirrormaker2_condition`), and the Grafana dashboard `edp-strimzi` provisioned from `/prov/dashboards` |

Every object in `monitoring` names its namespace, so an overlay that lists it must not set `namespace:` at its top level. An example that adds its own dashboard mounts one more ConfigMap with its own provider file under `/otel-lgtm/grafana/conf/provisioning/dashboards/`, as chapter 26 does on compose. These examples set no Pod Security labels (unlabelled namespaces), because the Buildah build pod and kube-state-metrics are not checked against `restricted`.

The three app sets use the same Service names and NodePorts, so they run one after another (`deploy_set`, `remove_set`), and each has its own database because the three Flyway histories collide in one.

## Adding an example

1. Create `examples/NN-slug/` with a `kustomization.yaml` listing the bases above, a `verify.sh`, a `README.md` and an `images.txt` (`<NN-slug> <service>` per line) naming the images to build.
2. Start `verify.sh` with `set -euo pipefail; cd "$(dirname "${BASH_SOURCE[0]}")"; source ../_k8s/lib.sh`, then `trap k8s_teardown EXIT` and `k8s_fresh .`.
3. Use `check`, `precondition`, `poll`, `expect` and end with `summary`.

Images are built from the compose build context (`examples/`, the Containerfile of the original example), tagged `localhost/edp/<NN>-<service>:local` and loaded with `minikube image load` only when the node's image ID differs from the host's. Pods use `imagePullPolicy: Never`.

## Helpers added for chapters 32 to 35

`lib.sh` keeps every r11 function name and signature.

| Helper | Use |
|---|---|
| `kafka_exec_on <pod> <tool> <args...>`, `kafka_raw_on <pod> ...` | a Kafka CLI tool in any broker pod (the second cluster's, a scaled-up broker); `kafka_exec` and `kafka_raw` are the same on `edp-broker-0` |
| `kafka_ready <name>` | the Kafka resource's generation is observed and it is Ready |
| `connect_get <path> [pod]` | read-only `curl` against a Connect worker through `kubectl exec`, for status and offsets; changes go through the resources |
| `group_empty <group>` | the consumer group has no active member (Empty or Dead) |
| `group_offsets_copy <from> <to>` | copies committed offsets between two inactive groups through `kafka-consumer-groups.sh --export` and `--from-file`; refuses while `<from>` has members |
| `prom_query <promql>` | prints the value of a one-series result, or the series count; fails on no data (`tools/prom.py`, which also takes a third argument `sum` or `count`) |
| `grafana_dashboard <uid>` | the provisioned dashboard is served (HTTP 200) |
| `psql_exec <db> -` | SQL from standard input (`exec -i`) |
| `load_image <slug> -` | an example with one image from `examples/<slug>/Containerfile`; `images.txt` carries `<slug> - <name>` and the image is `localhost/edp/<NN>-<name>:local` |
| `APP_PORTS` | NodePorts `_wait_apps` waits on |
