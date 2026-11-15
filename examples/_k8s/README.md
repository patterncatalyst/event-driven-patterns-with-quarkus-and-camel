# Part 6 substrate: one minikube profile for examples 28 to 31

Examples 28 to 31 are manifest-only overlays over the directories here. They deploy the applications of examples 02, 03 and 04 unchanged, on Kafka run by Strimzi, PostgreSQL run by CloudNativePG and the same otel-lgtm image the compose stack uses.

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

CloudNativePG 1.30.1 lists Kubernetes 1.37 as "Tested, but not supported". Its operator and cluster ran without problems on it here, so the pin stays.

## cluster.sh

```bash
examples/_k8s/cluster.sh up      # profile, loopback ports, vendor manifests, CloudNativePG operator
examples/_k8s/cluster.sh status  # profile, ports, namespaces
examples/_k8s/cluster.sh down    # minikube delete -p edp
```

`up` is idempotent. It refuses to run while a compose project, another minikube profile or CRC is running (`scripts/stack-guard.sh --minikube edp`). When the profile does not exist yet, it checks with `ss -ltnH` that every NodePort below is free on the host, then creates the profile with 6 CPUs, 10 GiB of memory and a 30 GB disk, and asserts with `docker port edp` that every published port is bound to 127.0.0.1 only. It downloads the Strimzi and CloudNativePG release manifests next to their kustomizations (`strimzi/strimzi-cluster-operator-1.3.1.yaml`, `platform/cnpg/cnpg-1.30.1.yaml`; both gitignored), refuses a file whose sha256 differs from `versions.env`, applies the CloudNativePG platform with `--server-side` and waits for the operator.

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

Every `verify.sh` starts with `k8s_fresh`. It deletes the KafkaTopics first, then the Kafka and node pool resources, then the namespaces `edp`, `edp-kafka` and `edp-lgtm`, each with a timeout that fails loudly; a stuck finalizer stops the run and is never stripped. It then asserts that no namespace is Terminating and no volume is still bound, applies the example with `kubectl apply --server-side --field-manager=edp-verify -k .` and waits in tiers: Strimzi operator, Kafka, CloudNativePG cluster and databases, otel-lgtm, then every Deployment in `edp`. `k8s_teardown` in the EXIT trap deletes the namespaces again unless `KEEP=1`. What stays per profile: CRDs, cluster-scoped RBAC, the CloudNativePG operator in `cnpg-system` and the node's image cache.

## Namespaces

| Namespace | Holds |
|---|---|
| `cnpg-system` | CloudNativePG operator (platform, applied by `cluster.sh up`) |
| `edp-kafka` | Strimzi operator, Kafka `edp`, node pools, KafkaTopics |
| `edp` | CloudNativePG `Cluster edp-pg`, the applications |
| `edp-lgtm` | otel-lgtm |

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

The three app sets use the same Service names and NodePorts, so they run one after another (`deploy_set`, `remove_set`), and each has its own database because the three Flyway histories collide in one.

## Adding an example

1. Create `examples/NN-slug/` with a `kustomization.yaml` listing the bases above, a `verify.sh`, a `README.md` and an `images.txt` (`<NN-slug> <service>` per line) naming the images to build.
2. Start `verify.sh` with `set -euo pipefail; cd "$(dirname "${BASH_SOURCE[0]}")"; source ../_k8s/lib.sh`, then `trap k8s_teardown EXIT` and `k8s_fresh .`.
3. Use `check`, `precondition`, `poll`, `expect` and end with `summary`.

Images are built from the compose build context (`examples/`, the Containerfile of the original example), tagged `localhost/edp/<NN>-<service>:local` and loaded with `minikube image load` only when the node's image ID differs from the host's. Pods use `imagePullPolicy: Never`.
