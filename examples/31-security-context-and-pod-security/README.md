# 31 Security context and pod security

The namespaces `edp` and `edp-kafka` enforce the restricted Pod Security Standard, and every pod in them still runs. This example labels them, gives the two example 02 applications an explicit security context, runs Strimzi with its restricted pod security provider and uses CloudNativePG as published. `cnpg-system` and `edp-lgtm` are checked with server dry-run labels and are never relabelled.

It is manifests only: a kustomize overlay over `examples/_k8s` and a `verify.sh`. There is no Java; the applications are the example 02 images, built from their own Containerfiles and loaded into the node.

## What runs

| Namespace | Pods | Pod Security labels |
|---|---|---|
| `edp-kafka` | Strimzi operator, Kafka `edp` (one controller, one broker) | enforce, warn and audit `restricted`, version `v1.37` |
| `edp` | CloudNativePG instance `edp-pg-1`, `order-service`, `notification-service` | enforce, warn and audit `restricted`, version `v1.37` |
| `edp-lgtm` | otel-lgtm | none: it is the exception |
| `cnpg-system` | CloudNativePG operator | none from this example; checked with a dry-run label |

`cnpg-system` is the platform tier that `cluster.sh up` applies once per profile, so the example does not label it. A relabel would have to be undone in teardown; the server dry-run label gives the same answer without changing it.

## Layout

| Path | Purpose |
|---|---|
| `kustomization.yaml` | The restricted setup: platform, Strimzi with the restricted provider, the example 02 apps, the app security component |
| `platform/` | Namespaces with the Pod Security labels, PostgreSQL and LGTM from `examples/_k8s` |
| `components/app-security/` | The four security context fields, `readOnlyRootFilesystem: true` and an emptyDir on `/tmp`, for Deployments in `edp` only |
| `demo/app-none/` | Demo code: the apps without a security context |
| `demo/strimzi-baseline/` | Demo code: Strimzi with its default pod security provider |
| `fixtures/` | A pod and a Deployment with no security context, used only with `--dry-run=server` |
| `tools/restricted.py` | Reads `kubectl get pods -o json` and checks each pod and container against the restricted fields |
| `images.txt` | The two images `verify.sh` builds and loads (a copy sits in each demo directory) |

## Run

```bash
cd examples/31-security-context-and-pod-security
./verify.sh
KEEP=1 ./verify.sh   # leave the namespaces in place
```

`verify.sh` starts from fresh namespaces, so nothing from an earlier run is reused. It ends with `26/26 checks passed`.

| Setting | Default | Effect |
|---|---|---|
| `APP_SECURITY` | `restricted` | Demo code. `none` applies the apps without a security context (the negative witness) |
| `STRIMZI_PSP` | `restricted` | Demo code. `baseline` keeps Strimzi's default pod security provider (the negative witness) |

Only one witness runs at a time. A witness that blocks Kafka ends with the namespace teardown, not a repair: a Kafka cluster whose pod was refused recovers slowly (about eight minutes in the spike for a refused broker pod).

## What verify.sh does

1. Checks that `edp` and `edp-kafka` enforce `restricted` at `v1.37` (precondition), that the CloudNativePG cluster is Ready (precondition), and that both also warn and audit.
2. Checks that `edp-lgtm` has no enforce label, and that re-evaluating `edp`, `edp-kafka` and `cnpg-system` with a server dry-run label reports no violating pod.
3. Asks the API server to create a pod and a Deployment with no security context (dry run): the pod is refused, the Deployment is accepted with a warning.
4. Checks Kafka: Ready, operator, broker and controller Running and restricted-compliant, broker uid, the operator's provider setting and no PodSecurity refusal in its log.
5. Checks CloudNativePG: the instance pod and the operator pod compliant, the instance uid, and the `cnpg-system` dry-run label.
6. Checks otel-lgtm: the dry-run label on `edp-lgtm` warns that the existing pod violates `restricted`, and the pod runs.
7. Checks the applications: Available, `id -u` is 185, the fields are set, the root filesystem is read-only except `/tmp`, the ReplicaSets created their pods, every pod in `edp` is compliant, an order is accepted and its notification arrives.

The order id is `O-3101`.

## Tests and checks in detail

There is no `mvn verify`: the example has no Java. `tools/restricted.py` fails on any pod that is not Running, on a container that allows privilege escalation, does not drop `ALL` capabilities, may run as root or has no `RuntimeDefault` or `Localhost` seccomp profile (on the pod or the container), and on a namespace with fewer pods than expected, so an empty namespace cannot pass.

`kubectl label --dry-run=server` re-evaluates the existing pods against the new policy. `edp` and `edp-kafka` already carry the label, so `verify.sh` changes their enforce version to `latest`; `cnpg-system` and `edp-lgtm` get `restricted`. The `edp-lgtm` check is the control: it must warn, which shows the other three clean results are not an artefact of the dry run. The version value changes from `v1.37` to `latest` in that dry run, so the API server evaluates the existing pods again.

## Experiments

Each run starts from fresh namespaces. The failing sets were measured in the live window and are frozen here.

| Switch | Effect | Failing checks | Measured result |
|---|---|---|---|
| `APP_SECURITY=none ./verify.sh` (demo code) | The applications carry no security context | Checks 19 to 26 (18/26), as predicted | The ReplicaSets report `FailedCreate` and name the four fields; no application pod exists |
| `STRIMZI_PSP=baseline ./verify.sh` (demo code) | Strimzi uses its default provider | Checks 8 to 12 and 26 (20/26) | The operator log holds `violates PodSecurity "restricted:v1.37"` for the Kafka pods and Kafka never becomes Ready. Check 25 passes: the order is accepted without Kafka, and only the notification (26) fails |

## Measurements

With `APP_SECURITY=none` the `FailedCreate` event is there 1 s after the namespaces are ready. With `STRIMZI_PSP=baseline` the application checks other than the notification pass, because `order-service` accepts the order with Kafka absent.
