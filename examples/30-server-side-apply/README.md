# 30 Server-side apply

Two tools write one Deployment, and a client-side apply keeps the last one. This example applies the chapter 02 services under the field manager `edp-apps`, patches one field as `edp-platform`, applies the CloudNativePG install both ways, and adds a foreign annotation to a Service that the Strimzi operator applies. Every owner is read back from `managedFields`.

Manifests only: overlays over `examples/_k8s`, no pom and no Java. The applications are the unchanged chapter 02 images (`images.txt`).

## Layout

| Path | Purpose |
|---|---|
| `kustomization.yaml` | The platform bases from `_k8s` plus `apps`; applied by `k8s_fresh` under `edp-apps` |
| `apps/` | The chapter 02 Deployments and Services (writer `edp-apps`) |
| `platform/order-service-memory.yaml` | A partial Deployment setting `limits.memory: 512Mi` (writer `edp-platform`) |
| `changes/label/` | `apps` plus one label, applied by `edp-apps`; applying `apps` again removes it |
| `changes/memory/` | `apps` with `400Mi`, used only by `kubectl diff` |
| `foreign/annotation.yaml`, `foreign/discovery.yaml` | Annotations on `edp-kafka-bootstrap` sent by `edp-foreign` |
| `tools/owners.py` | Prints the managers that own a path in `managedFields` (`python3 -I`) |

## Run

```bash
examples/_k8s/cluster.sh up
./verify.sh          # fresh namespaces, then the checks
KEEP=1 ./verify.sh   # leave the namespaces in place
```

`verify.sh` ends with `25/25 checks passed`. It deletes and recreates the namespaces `edp`, `edp-kafka` and `edp-lgtm` first. Order id: `O-3001`.

## What verify.sh does

1. Deploys the pair as `edp-apps` and requires both services ready and a limit of 384Mi.
2. Dry-runs the CloudNativePG manifest client-side (`Too long` on `clusters.postgresql.cnpg.io` and `poolers.postgresql.cnpg.io`) and server-side (accepted), and the Strimzi manifest client-side (no `Too long`).
3. Reads `managedFields`, shows a `kubectl diff --server-side` of a changed limit without applying it, adds a label as `edp-apps` and removes it by applying without it.
4. Applies `platform` as `edp-platform`: a conflict naming `edp-apps` and the field, the limit unchanged; with `--force-conflicts` 512Mi and ownership moved; `apps` again conflicts naming `edp-platform`; with force 384Mi and ownership back.
5. Places `O-3001` and polls the notification.
6. Reads the owner of `strimzi.io/discovery` on `edp-kafka-bootstrap`, adds `example.com/owner` as `edp-foreign`, triggers two Kafka reconciliations and checks it survives, conflicts on `strimzi.io/discovery` without force, forces it, and waits for the operator to restore it.

Manager names are taken from `managedFields` output.

## Tests and checks in detail

No unit tests: there is no Java. The checks read effects from the API server.

1. Both services answer on `/q/health/ready` (precondition).
2. `order-service` starts with `limits.memory` 384Mi (precondition).
3. Client-side dry run of the CloudNativePG install: `Too long` on `clusters.postgresql.cnpg.io`.
4. The same on `poolers.postgresql.cnpg.io`.
5. Server-side dry run of the same file succeeds.
6. Client-side dry run of the Strimzi install reports no `Too long`.
7. `edp-apps Apply` owns `limits.memory`.
8. `kubectl diff --server-side` shows `384Mi` to `400Mi` once each and the live value is still 384Mi.
9. A label added by `edp-apps` is present.
10. The label is gone after `edp-apps` applies without it.
11. `edp-platform` conflicts naming `edp-apps` and the field path.
12. The limit is still 384Mi.
13. Forced, the limit is 512Mi.
14. `edp-platform Apply` owns the limit and `edp-apps Apply` does not.
15. `edp-apps` conflicts naming `edp-platform`.
16. Forced, the limit is 384Mi.
17. `edp-apps Apply` owns the limit and `edp-platform Apply` does not.
18. `O-3001` is notified with status `PLACED`.
19. `strimzi-kafka-operator Apply` owns `strimzi.io/discovery`.
20. `edp-foreign` adds `example.com/owner` without a conflict and owns it.
21. The annotation is still there after two reconciliations (counted from the operator log).
22. Changing `strimzi.io/discovery` conflicts naming `strimzi-kafka-operator`.
23. Forced, the annotation holds the foreign value.
24. After the next reconciliation the value is the operator's again.
25. `strimzi-kafka-operator Apply` owns it and `edp-foreign Apply` does not.

## Experiments

| Switch | Effect | Failing checks (measured) |
|---|---|---|
| `APPLY=client ./verify.sh` (demo code) | `edp-platform` and the `edp-apps` re-apply use client-side `kubectl apply`: no conflict, ownership moves to `kubectl-client-side-apply` | Checks 11, 12, 14, 15 and 17 (20/25), as predicted; the other 20 pass |

Other ideas: `kubectl --context edp -n edp get deploy order-service --show-managed-fields -o yaml` after a step; apply `changes/memory` as `edp-platform`.

## Measurements

- Operator restores `strimzi.io/discovery`: 1 to 2 s (two runs) after a triggered reconciliation.
- The conflict text in the chapter is the output of a run on Kubernetes 1.37.
- The server-side dry run of the CloudNativePG install (check 5) uses the field manager `edp-cluster`, the one that installed it; under another manager it conflicts on `OPERATOR_NAMESPACE` of the operator Deployment.
