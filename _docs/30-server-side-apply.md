---
title: "Server-side apply"
order: 30
part: running-on-kubernetes-with-strimzi
permalink: /docs/30-server-side-apply/
description: "Two tools write the same Deployment and a client-side apply silently keeps the last one. Server-side apply records an owner for every field, refuses a second writer until it forces the change, and installs manifests whose CRDs are too large for an annotation. Strimzi applies its own resources the same way, so a foreign annotation stays and an owned field returns."
duration: "50 minutes"
---

The application manifests set a memory limit of 384Mi on `order-service`. A platform team raises it to 512Mi with a patch, and the next deployment of the application manifests puts it back to 384Mi. Nothing reports an error. The pod restarts twice and the last writer wins.

With `kubectl apply` as most people first learn it, the cluster has no idea who wrote which field. The same gap shows when a manifest is too big to apply at all: the install of CloudNativePG, the PostgreSQL operator of chapter 28, fails before it creates anything.

{% include excalidraw.html file="30-field-managers" alt="Two writers, edp-apps with the app manifests and edp-platform with a memory patch, apply to the Deployment order-service; the API server records the owner of every field in managedFields, and limits.memory has one owner at a time" caption="Figure 30.1: The API server records an owner for every field a manager applies" %}

## Pattern: Field ownership

### The problem

`kubectl apply` in its client-side form compares three things on the client: the manifest, the live object and a copy of the last manifest, stored in the annotation `kubectl.kubernetes.io/last-applied-configuration`. That copy is how it knows which fields to delete. It has two consequences.

The annotation holds the whole manifest, and the API server limits all annotations on one object to 256 KiB, 262144 bytes. A CustomResourceDefinition with a large schema carries its whole content into its own annotation and passes the limit. The install of CloudNativePG fails on `clusters.postgresql.cnpg.io` and on `poolers.postgresql.cnpg.io`:

```text
CustomResourceDefinition.apiextensions.k8s.io "clusters.postgresql.cnpg.io" is invalid:
metadata.annotations: Too long: may not be more than 262144 bytes
```

The other consequence is that the server cannot tell two writers apart. A second tool that sets a field the first one owns overwrites it, and the first tool overwrites it back on its next run. The problem is not the tools. A field has no owner, so nothing can refuse a write or explain a change.

### The solution

**Apply server-side under a named field manager: the API server records the owner of each field, refuses a write from another manager that would change an owned field, and lets that manager force the change and take the field.**

The apply is a request to the API server, not a patch computed on the client. The manifest is the set of fields the manager wants to own, and the server merges it with the other managers' sets. Fields that two managers set to the same value are shared. Fields set to different values are a conflict, and a conflict is an error until the applier forces it. A field the manager sent earlier and leaves out now is removed, unless another manager also owns it (Kubernetes, Server-Side Apply). Server-side apply is a stable feature (KEP-555).

### How Kubernetes and Strimzi model it

Ownership is stored in `metadata.managedFields`: one entry per manager and operation, with the owned paths. `kubectl get --show-managed-fields` prints it. Chapter 28's Strimzi operator is one more manager. In current releases it applies its `Service`, `ServiceAccount`, `ConfigMap`, `Ingress` and `PersistentVolumeClaim` resources server-side and the setting cannot be turned off (Strimzi changelog). The Kafka cluster of chapter 28 and the topics of chapter 29 are Strimzi resources, so their owned fields behave as the next sections show.

## Installing the operators

The manifests of both operators are single files with their CRDs. A client-side apply of the CloudNativePG manifest stops at the two CRDs named above, and a server-side apply of the same manifest passes, because no manifest is copied into an annotation.

```bash
kubectl apply --dry-run=server -f cnpg.yaml                  # Too long
kubectl apply --server-side --dry-run=server -f cnpg.yaml    # accepted
```

The Strimzi CRDs are smaller. The largest, `kafkas.kafka.strimzi.io`, stays under the limit, so a client-side apply of the Strimzi install passes. That is a property of the schemas, not a reason to prefer one form. CloudNativePG documents the server-side form for its install (CloudNativePG, Installation and upgrades). Every apply in Part 6 uses `--server-side`.

## Two writers on one Deployment

The application manifests are applied under the manager `edp-apps`:

```bash
kubectl apply --server-side --field-manager=edp-apps -k apps
```

A second manager, `edp-platform`, sends a partial Deployment with only the field it cares about:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata: {name: order-service, namespace: edp}
spec:
  template:
    spec:
      containers:
        - name: app
          resources: {limits: {memory: 512Mi}}
```

`containers` is a list keyed by `name`, so the server merges the `app` container instead of replacing the list. The apply fails and changes nothing:

```text
error: Apply failed with 1 conflict: conflict with "edp-apps":
.spec.template.spec.containers[name="app"].resources.limits.memory
```

{% include excalidraw.html file="30-conflict-and-force" alt="edp-apps applies limits.memory 384Mi and owns it; edp-platform applies 512Mi and conflicts, nothing changes; with force-conflicts the value is 512Mi and edp-platform is the owner; edp-apps applies again, conflicts with edp-platform and takes the field back by forcing" caption="Figure 30.2: A conflict refuses the write, and forcing it moves the field to the writer that forced" %}

The message names the manager and the field. With `--force-conflicts` the value becomes 512Mi, and `managedFields` shows `edp-platform` as the owner of `limits.memory` and `edp-apps` as the owner of everything else. The next apply of the application manifests conflicts the other way, with `edp-platform` named, and takes the field back only when it forces. Forcing is a decision with a name on it. Two teams that both set the same value avoid the conflict altogether, because shared fields do not conflict.

The client-side form has no such step. A `kubectl apply -f` of the same value over a field the server-side applier owns reports `configured`, prints a warning about the missing last-applied annotation and takes the field under the manager `kubectl-client-side-apply`. The next server-side apply of `edp-apps` then conflicts with that manager. Mixing the two forms on one object is the way to lose track of owners.

`kubectl diff --server-side --field-manager=edp-apps -k <dir>` asks the server what the apply would do and prints it as a unified diff. A changed limit shows as one line removed and one added, and nothing is written. Run it before an apply of a shared object, and it shows whether another manager's value is about to change.

A label that `edp-apps` sent once and then leaves out is removed on the next apply, because `edp-apps` is its only owner. The same label also owned by a second manager stays. That rule replaces the last-applied annotation: the server knows from `managedFields` which fields a manager sent before.

## Strimzi's own fields

The operator applies the Kafka bootstrap `Service` under the manager `strimzi-kafka-operator`, and `managedFields` lists it as an `Apply` owner of the annotation `strimzi.io/discovery`. Another manager adds an annotation next to it without any conflict, and the annotation survives the operator's reconciliations:

```bash
kubectl apply --server-side --field-manager=edp-foreign -f annotation.yaml
kubectl -n edp-kafka annotate kafka edp r=1 --overwrite     # a reconcile
```

A change to the operator's own annotation is another matter. Without force it fails with a conflict naming `strimzi-kafka-operator`. With force it succeeds, and the operator puts its value back at the next reconciliation, 1 to 2 s after the reconcile was triggered. The operator first applies without force and, on a conflict, logs it and retries with force (Strimzi blog, Server-Side Apply in Strimzi). A tool that adds fields to Strimzi's resources is therefore safe, and a tool that edits Strimzi's fields loses.

{% include excalidraw.html file="30-strimzi-ssa" alt="The Strimzi operator applies the bootstrap Service each reconciliation and owns strimzi.io/discovery; edp-foreign adds example.com/owner without a conflict; the foreign annotation survives the reconciliations and a forced change of strimzi.io/discovery is restored" caption="Figure 30.3: The operator owns its fields and leaves everyone else's alone" %}

The edit that belongs to the operator goes into the resource it reads. A service type, a listener or a label that Strimzi should set comes from the `Kafka` or `KafkaNodePool` resource, not from a patch on the `Service` it creates. A `KafkaTopic` of chapter 29 is the same case: the resource is the source of truth and a change made elsewhere is reverted.

## Build, run, observe

```bash
examples/_k8s/cluster.sh up
cd examples/30-server-side-apply
./verify.sh
```

The script deploys the services of chapter 2 under `edp-apps`, applies the CloudNativePG install both ways, lets `edp-platform` conflict and force, and adds a foreign annotation to the Strimzi `Service`, reading every owner back from `managedFields`; it ends with `25/25 checks passed`.

Try one change and rerun:

- `APPLY=client ./verify.sh` (demo code): both writers apply client-side, the conflicts never appear and the ownership checks fail.
- `KEEP=1 ./verify.sh`, then `kubectl --context edp -n edp get deploy order-service --show-managed-fields -o yaml` shows each manager and its fields.
- `kubectl --context edp diff --server-side --field-manager=edp-apps -k changes/memory` prints the one-line change without applying it.

## When not to use it

A manifest applied once by one person to a throwaway cluster needs no owner. `kubectl create` does the job, and a name for the manager adds nothing.

Server-side apply is not a coordination tool. It reports a conflict between managers that apply the same field with different values. It does not say which value is right, and `--force-conflicts` in a pipeline that runs without a person turns the report off. Settle who owns a field before the second manager exists, and keep the force for a decision.

Objects created by a client-side apply or by `kubectl edit` carry managers of their own. The first server-side apply takes over their fields without a conflict under the default manager name, and a different name opts out of that transfer (Kubernetes, Server-Side Apply).

> **🤖 Explore with your agent**
> "In examples/30-server-side-apply, apply apps as edp-apps, then apply platform as edp-platform without and with --force-conflicts, and read the owners of limits.memory on Deployment order-service from managedFields each time. Then add an annotation to the Service edp-kafka-bootstrap as edp-foreign and change strimzi.io/discovery with and without force. Say which writer owns each field after every step and which apply should never be forced."
> Check: the first edp-platform apply fails with a conflict naming edp-apps and leaves 384Mi, the forced apply gives 512Mi owned by edp-platform, and the operator owns strimzi.io/discovery again after its next reconciliation.

## References

- Kubernetes, [Server-Side Apply](https://kubernetes.io/docs/reference/using-api/server-side-apply/) and [kubectl apply](https://kubernetes.io/docs/reference/kubectl/generated/kubectl_apply/)
- Kubernetes, [Declarative management of objects](https://kubernetes.io/docs/tasks/manage-kubernetes-objects/declarative-config/) (the last-applied annotation) and [Annotations](https://kubernetes.io/docs/concepts/overview/working-with-objects/annotations/) (the 256 KiB limit)
- Kubernetes Enhancement Proposal [555, Server-side apply](https://github.com/kubernetes/enhancements/tree/master/keps/sig-api-machinery/555-server-side-apply)
- Strimzi blog, [Server-Side Apply in Strimzi](https://strimzi.io/blog/2026/01/26/server-side-apply-in-strimzi/), 2026, and the [changelog](https://github.com/strimzi/strimzi-kafka-operator/blob/main/CHANGELOG.md)
- CloudNativePG, [Installation and upgrades](https://cloudnative-pg.io/docs/current/installation_upgrade/)
- Chapter 28 for the Strimzi cluster and the CloudNativePG database, chapter 29 for the topics Strimzi manages
