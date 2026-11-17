---
title: "Appendix H: OpenShift Local"
order: 43
permalink: /examples/ah-openshift-local/
description: "The outbox order-service, Strimzi and CloudNativePG on OpenShift Local: operators at pinned versions, an image built with buildah and copied with skopeo, an order that reaches Kafka through a verified Route, and a teardown that leaves nothing behind."
---

[`examples/ah-openshift-local/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/ah-openshift-local) installs two operators from OpenShift's catalogs, builds the unchanged `order-service` image with buildah, copies it into the cluster's registry with skopeo and deploys it by digest behind an edge-TLS Route. It accompanies [Appendix H]({{ '/docs/ah-openshift-local/' | relative_url }}).

It is shell scripts and kustomize manifests, with no Java and no pom. The application is the outbox `order-service`, built from its own Containerfile. The Kafka and PostgreSQL manifests are the minikube ones with a different namespace.

```bash
cd examples/ah-openshift-local
./install.sh && ./build-and-copy.sh && ./deploy.sh
./verify.sh
./teardown.sh
```

{% include excalidraw.html file="ah-build-and-copy" alt="A rootless buildah build of the unchanged Containerfile gives an image with digest D in containers-storage; skopeo copy with preserve-digests sends it through the registry route with verified TLS to the ImageStream order-service:08, and the Deployment references the internal registry at D" caption="The digest of the local build is the digest in the cluster" %}

## What to observe

1. **Pinned operators.** Each Subscription waits for an approval, and only the plan for the named version is approved.
2. **One digest.** The image built on the host and the image in the cluster's `ImageStream` have the same manifest digest, and the Deployment references it.
3. **Admission.** Every pod runs under `restricted-v2` with a user from the project's range, and a privileged Deployment is refused.
4. **Verified TLS.** The Route answers with a certificate checked against the cluster's ingress CA, and no step turns verification off.
5. **A clean teardown.** The inventory afterwards lists no namespace, CRD, ClusterRole, volume or image of the example.

## Infrastructure

An OpenShift Local instance that is running, with the context `crc-admin`; `oc`, `buildah`, `skopeo`, `jq` and `curl` on the host. No Docker Compose project and no minikube node may be running.
