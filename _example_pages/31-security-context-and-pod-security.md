---
title: "Security context and pod security"
order: 31
permalink: /examples/31-security-context-and-pod-security/
description: "Two namespaces that enforce the restricted Pod Security Standard, with the chapter 02 applications carrying an explicit security context, Strimzi on its restricted provider, CloudNativePG as published and otel-lgtm outside the rule."
---

[`examples/31-security-context-and-pod-security/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/31-security-context-and-pod-security) labels `edp` and `edp-kafka` for the restricted level and runs Kafka, PostgreSQL and the two chapter 02 services under it. It accompanies [Security context and pod security]({{ '/docs/31-security-context-and-pod-security/' | relative_url }}).

It is a kustomize overlay over the shared Part 6 cluster setup and a `verify.sh`, with no Java. The services are the chapter 02 images.

```bash
cd examples/31-security-context-and-pod-security
./verify.sh
```

{% include excalidraw.html file="31-who-sets-what" alt="Four workloads, the single place that sets each one's security context, and the result: the applications from their own Deployment, Kafka from the Strimzi operator's restricted provider, PostgreSQL from the CloudNativePG operator as published, and otel-lgtm from its image in an unlabelled namespace" caption="Each workload gets its security context from exactly one place" %}

## What to observe

1. **Labels that enforce.** `edp` and `edp-kafka` enforce, warn and audit at restricted, and the API server refuses a pod with no security context.
2. **Applications that run as 185.** The four fields and a read-only root filesystem with an emptyDir on `/tmp`; `id -u` reports 185.
3. **Kafka under the restricted provider.** The operator, broker and controller pods are Running and compliant; with the default provider the Kafka pods are refused.
4. **PostgreSQL as published.** The instance pod runs as uid 26 and the operator namespace passes a dry-run label.
5. **An exception with a record.** A dry-run label on `edp-lgtm` warns that its pod violates the level, so the namespace stays unlabelled.

## Infrastructure

Strimzi, Kafka, CloudNativePG and otel-lgtm from `examples/_k8s` on the `edp` minikube profile, plus the two services on NodePorts 30080 and 30082.
