---
title: "Appendix B: Compose to Kubernetes"
order: 37
permalink: /examples/ab-compose-to-kubernetes/
description: "The effective settings of Kafka and PostgreSQL read from a fresh compose stack and from fresh Kubernetes namespaces, compared against a list of expected differences."
---

[`examples/ab-compose-to-kubernetes/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/ab-compose-to-kubernetes) has no Java. It starts the Kafka and PostgreSQL of `examples/_infra` on compose, then the Kafka resource and the CloudNativePG Cluster of `examples/_k8s` on minikube, reads 13 broker settings and 5 PostgreSQL settings from each, and compares them. It accompanies [Appendix B]({{ '/docs/ab-compose-to-kubernetes/' | relative_url }}).

It uses `docker compose`, minikube, Strimzi, CloudNativePG and `kubectl`, with the files of `_infra` and `_k8s` as they are.

```bash
cd examples/ab-compose-to-kubernetes
./verify.sh compose
./verify.sh k8s
./verify.sh compare
```

{% include excalidraw.html file="ab-capture-and-compare" alt="verify.sh compose and verify.sh k8s each write a capture of broker and PostgreSQL settings; verify.sh compare reads both and reports each setting as equal, as an allowed difference or as a failure" caption="Two captures, one comparison" %}

## What to observe

1. **Files agree with the running systems.** Every setting a compose file, the Kafka resource or the postgres command line sets is what the system reports; every setting left unset is the documented default.
2. **An empty capture fails.** A node id that is not a broker prints nothing; the capture check rejects it.
3. **Four listed differences.** The `share` protocol, the share state topic's two settings and `max_replication_slots` differ, and the comparison names them.
4. **Any other difference fails.** With `DRIFT=partitions`, the compose broker reports 6 partitions and the comparison fails on it.

## Infrastructure

The compose half runs Kafka and PostgreSQL only. The Kubernetes half runs the Strimzi operator, one Kafka cluster with two node pools and one CloudNativePG Cluster on the `edp` profile; no application and no LGTM.
