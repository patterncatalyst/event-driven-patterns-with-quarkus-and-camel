---
title: "Security context and pod security"
order: 31
part: running-on-kubernetes-with-strimzi
permalink: /docs/31-security-context-and-pod-security/
description: "A cluster admits any pod unless a rule says otherwise. Labelling a namespace with the restricted Pod Security Standard makes the API server refuse pods that can escalate privileges or run as root. The applications carry the four required fields, Strimzi gets the restricted pod security provider, CloudNativePG is admitted as published, and otel-lgtm stays in a namespace of its own."
duration: "50 minutes"
---

The cluster of chapter 28 runs Kafka, PostgreSQL and two services, and every one of them started without a word about security. Kubernetes admits any pod that is well formed. Whether a container may run as root, gain privileges through a setuid binary or keep Linux capabilities it never uses is decided by the image and by whatever the pod spec leaves out.

A process that an attacker takes over inherits all of it. A namespace label turns the rule on, and the API server then refuses a pod that does not meet it, whoever or whatever created it.

{% include excalidraw.html file="31-admission" alt="A namespace labelled enforce restricted sends every pod to Pod Security Admission; a pod with the four fields is admitted and one without is forbidden with the fields named; a Deployment without them is accepted, its ReplicaSet fails to create the pod, and the old pod keeps running" caption="Figure 31.1: The check runs when a pod is created, so a Deployment is accepted and its pods are refused" %}

## Pattern: Restricted pods by default

### The problem

Kubernetes ships three Pod Security Standards. Privileged is unrestricted, Baseline blocks the known escalations, and Restricted follows current hardening practice: a non-root user, no privilege escalation, every capability dropped and a seccomp profile. Nothing applies one until a namespace asks.

The obvious fix is to tell each team to write a good security context. It fails in the way every convention does: one Deployment forgets, and nothing notices until an audit. A second problem is that most pods in a cluster are not written by the team. An operator creates the Kafka brokers and the PostgreSQL instances, and the operator decides what their security context is.

### The solution

**Label every namespace that runs workloads with the restricted level, and give each workload the security context that level requires from the one place that owns it.**

The label is the enforcement. The security context stays with whoever owns the pod spec: the application's Deployment, the operator's provider, or the image as published. A workload that cannot meet the level is a visible exception with its own namespace, not a silent one.

### How Kubernetes, Strimzi and CloudNativePG model it

Pod Security Admission is built into the API server. Its labels name a mode, a level and a policy version.

```yaml
metadata:
  name: edp
  labels:
    pod-security.kubernetes.io/enforce: restricted
    pod-security.kubernetes.io/enforce-version: v1.37
    pod-security.kubernetes.io/warn: restricted
    pod-security.kubernetes.io/warn-version: v1.37
    pod-security.kubernetes.io/audit: restricted
    pod-security.kubernetes.io/audit-version: v1.37
```

`enforce` rejects a violating pod, `warn` sends the violations back to whoever applied the manifest and `audit` records them. Pinning the version keeps the rule from moving when the cluster is upgraded; `latest` follows the cluster. The page on enforcing standards with namespace labels shows the same set. The overlay labels `edp` and `edp-kafka`, the two namespaces that hold Kafka, PostgreSQL and the applications.

Strimzi and CloudNativePG are operators, so the pods they create have to pass too. Strimzi takes a pod security provider and offers two, Baseline and Restricted, and Baseline is the default. CloudNativePG documents that a namespace enforcing `restricted` or `baseline` rejects any pod that violates the policy, including pods the operator creates, and sets a context that fits.

{% include excalidraw.html file="31-who-sets-what" alt="Four workloads, the single place that sets each one's security context, and the result: the applications from their own Deployment, Kafka from the Strimzi operator's restricted provider, PostgreSQL from the CloudNativePG operator as published, and otel-lgtm from its image in an unlabelled namespace" caption="Figure 31.2: Each workload gets its security context from exactly one place" %}

## The application's security context

A pod that fails the level is forbidden with every missing field named. Applying the two services of chapter 2 into the labelled namespace without a security context gives a Deployment that is accepted and a ReplicaSet that cannot create its pod.

```text
Error creating: pods "order-service-..." is forbidden: violates PodSecurity "restricted:v1.37":
allowPrivilegeEscalation != false (container "app" must set securityContext.allowPrivilegeEscalation=false),
unrestricted capabilities (container "app" must set securityContext.capabilities.drop=["ALL"]),
runAsNonRoot != true (pod or container "app" must set securityContext.runAsNonRoot=true),
seccompProfile (pod or container "app" must set securityContext.seccompProfile.type to "RuntimeDefault" or "Localhost")
```

The Deployment reports `ReplicaFailure` and the old pod keeps serving. That is the case worth testing before a label goes on a namespace that already holds pods: labelling it prints a warning for each running pod that would fail and evicts nothing, and the refusal arrives with the next pod creation, which is usually a rollout at a bad hour. Applying the unsecured manifest with server-side apply, as in chapter 30, prints `would violate PodSecurity` as well.

The four fields, plus a read-only root filesystem, go in one patch that every Deployment in `edp` receives.

```yaml
- op: add
  path: /spec/template/spec/securityContext
  value:
    runAsNonRoot: true
    seccompProfile: {type: RuntimeDefault}
- op: add
  path: /spec/template/spec/containers/0/securityContext
  value:
    allowPrivilegeEscalation: false
    capabilities: {drop: [ALL]}
    readOnlyRootFilesystem: true
- op: add
  path: /spec/template/spec/containers/0/volumeMounts
  value: [{name: tmp, mountPath: /tmp}]
- op: add
  path: /spec/template/spec/volumes
  value: [{name: tmp, emptyDir: {}}]
```

`runAsNonRoot` can only be verified when the image names a numeric user, and the UBI OpenJDK images do: they run as user 185. The pods report `185` for `id -u`, with no `runAsUser` in the spec.

`readOnlyRootFilesystem` is not part of the Restricted standard, and it is the field that most often breaks an application. An `emptyDir` on `/tmp` gives the process its one writable path. Writing under `/deployments` then fails with `Read-only file system`, writing under `/tmp` works, and both applications start with no change to their source or their images.

## Strimzi pods

The operator's own pod passes without help: its install files carry a restricted container context. The pods it creates do not. With the default provider the broker and the controller are forbidden, and Kafka never becomes Ready.

```text
pods "edp-broker-0" is forbidden: violates PodSecurity "restricted:v1.37": allowPrivilegeEscalation != false
(container "kafka" must set securityContext.allowPrivilegeEscalation=false), unrestricted capabilities
(container "kafka" must set securityContext.capabilities.drop=["ALL"]), runAsNonRoot != true ...
```

The refusal shows up in the operator's log, and Kafka stays not Ready; there is no failed rollout to watch. One environment variable on the operator Deployment selects the provider that matches the label.

```yaml
- name: STRIMZI_POD_SECURITY_PROVIDER_CLASS
  value: restricted
```

The Restricted provider disallows privilege escalation, requires a non-root user, sets seccomp to `RuntimeDefault`, and drops every capability except `NET_BIND_SERVICE`. The broker and controller pods then carry the four fields and run as user 1001. A security context set in the pod template of a node pool overrides the provider, which makes a template the place for a single pod's exception, visible in the resource.

Running the default provider under the label shows the refusal. A run that blocks Kafka ends with the namespace teardown and no repair: the operator waits for a broker that cannot exist, and a Kafka cluster whose pod was refused recovers slowly.

## CloudNativePG pods

The PostgreSQL pods need nothing from the example. The CloudNativePG operator pod runs as a non-root user with a read-only root filesystem and the `RuntimeDefault` seccomp profile, and the instance pod does too. The instance runs as the `postgres` user, uid 26, which is the user of the operand image. The `initdb` job pod that bootstraps the cluster is admitted in a restricted `edp` as well, and the cluster comes up Ready.

The CloudNativePG documentation says that no component requires root, and the pods bear it out. The operator namespace `cnpg-system` is the platform tier of chapter 28, applied once per cluster and shared by every example, so it stays as it is. A server dry-run label asks the API server what labelling it would do.

```bash
kubectl label --dry-run=server --overwrite ns cnpg-system pod-security.kubernetes.io/enforce=restricted
```

No warning comes back, which means its pods already satisfy the level, and nothing is changed. The dry run is the check; the label is not left behind.

## What stays outside

Not every image can meet the level. `grafana/otel-lgtm` is the whole observability backend in one image, run as published with no security context. The same dry-run label that cleared `cnpg-system` answers for it.

```text
Warning: existing pods in namespace "edp-lgtm" violate the new PodSecurity enforce level "restricted:latest"
Warning: lgtm-...: allowPrivilegeEscalation != false, unrestricted capabilities, runAsNonRoot != true, seccompProfile
namespace/edp-lgtm labeled (server dry run)
```

So `edp-lgtm` stays unlabelled. It has a namespace of its own for exactly this reason: a namespace label cannot exempt one pod, so the exception gets its own namespace. The warning above is the record of the exception, and the dry run repeats it whenever the image changes.

## Build, run, observe

```bash
cd examples/31-security-context-and-pod-security
./verify.sh
```

The script labels the two namespaces, runs Strimzi, CloudNativePG and the applications under them, reads each pod's security context and effective user back from the API server, asks it to create a pod without a security context, and ends with `26/26 checks passed`.

Try one change and rerun:

- `APP_SECURITY=none ./verify.sh` (demo code): the applications carry no security context, their ReplicaSets report `FailedCreate` naming the four fields, and no application pod exists.
- `STRIMZI_PSP=baseline ./verify.sh` (demo code): Strimzi keeps its default provider, the operator log shows the Kafka pods refused, and Kafka never becomes Ready.
- `kubectl --context edp label --dry-run=server ns edp-lgtm pod-security.kubernetes.io/enforce=restricted` after `KEEP=1 ./verify.sh`: the warning above, with the namespace unchanged.

## When not to use it

A cluster for one person's laptop experiments gains little from a rule that refuses their own pods, and Baseline is the lower step that still blocks the known escalations. A workload that needs a host path, a privileged container or a Linux capability cannot live under Restricted at all, and it belongs in a namespace labelled for what it needs, with the reason written beside the label.

The level checks pod specs and nothing else. It does not scan images, limit network paths or constrain what a non-root process may do with the permissions it has. A restricted pod with a database password in its environment is restricted and exposed.

The label is only as good as the version it names. `latest` moves with the cluster, so an upgrade can refuse pods that ran the day before, and a pinned version moves only when someone changes it.

> **🤖 Explore with your agent**
> "In examples/31-security-context-and-pod-security, run verify.sh with APP_SECURITY=none and read the FailedCreate event on the order-service ReplicaSet. List the four fields it names, then find where components/app-security sets them and why the Deployment is accepted while its pod is not. Run the same with STRIMZI_PSP=baseline and find the refusal in the Strimzi operator log. Say which fields the Restricted provider adds to the Kafka pods."
> Check: with APP_SECURITY=none no pod of order-service exists and the event names allowPrivilegeEscalation, capabilities, runAsNonRoot and seccompProfile; with STRIMZI_PSP=baseline the operator log holds violates PodSecurity for edp-broker-0 and Kafka is never Ready.

## References

- Kubernetes, [Pod Security Standards](https://kubernetes.io/docs/concepts/security/pod-security-standards/), [Pod Security Admission](https://kubernetes.io/docs/concepts/security/pod-security-admission/) and [Enforce Pod Security Standards with Namespace Labels](https://kubernetes.io/docs/tasks/configure-pod-container/enforce-standards-namespace-labels/) (the modes, the version pin and the dry-run label)
- Strimzi, [Deploying and Managing](https://strimzi.io/docs/operators/latest/deploying.html), the pod security provider section (the Baseline and Restricted providers, `STRIMZI_POD_SECURITY_PROVIDER_CLASS`, template precedence), and [Writing custom pod security providers](https://strimzi.io/blog/2023/04/05/writing-custom-pod-security-providers/)
- CloudNativePG, [Security](https://github.com/cloudnative-pg/cloudnative-pg/blob/v1.30.1/docs/src/security.md) (the pod security context and Pod Security Admission)
- Red Hat, [UBI OpenJDK images](https://catalog.redhat.com/software/containers/ubi10/openjdk-25/) (the 185 user)
- Chapter 28 for the cluster and its namespaces, chapter 30 for server-side apply
