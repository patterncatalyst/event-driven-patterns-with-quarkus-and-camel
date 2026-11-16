---
title: "Server-side apply"
order: 30
permalink: /examples/30-server-side-apply/
description: "The `order-service` and `notification-service` deployed by one field manager and patched by a second, the CloudNativePG install applied both ways, and a foreign annotation on a Strimzi Service, with every owner read back from managedFields."
---

[`examples/30-server-side-apply/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/30-server-side-apply) is a set of manifests over `examples/_k8s`: the `order-service` and `notification-service` pair under the field manager `edp-apps`, a partial Deployment for a second manager `edp-platform`, and two annotation files for a third, `edp-foreign`. It accompanies [Server-side apply]({{ '/docs/30-server-side-apply/' | relative_url }}).

The applications are the unchanged `order-service` and `notification-service` images, loaded into minikube. There is no pom and no Java.

```bash
examples/_k8s/cluster.sh up
cd examples/30-server-side-apply
./verify.sh
```

{% include excalidraw.html file="30-field-managers" alt="Two writers, edp-apps and edp-platform, apply to the Deployment order-service; the API server records the owner of every field in managedFields" caption="Two managers write one Deployment and the server records who owns which field" %}

## What to observe

1. **An install that only the server-side form accepts.** A client-side apply of the CloudNativePG manifest fails with `Too long` on two CRDs.
2. **A conflict that names its owner.** `edp-platform` is refused on `limits.memory` until it forces, and `managedFields` shows the ownership move.
3. **A foreign field beside the operator's.** An annotation on the Strimzi bootstrap Service survives reconciliations, and a forced change of `strimzi.io/discovery` is restored.

## Infrastructure

The minikube profile `edp` from `examples/_k8s`: Strimzi and Kafka, CloudNativePG, otel-lgtm and the `order-service` and `notification-service` pair.
