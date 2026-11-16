---
title: "Strimzi and KRaft"
order: 28
part: running-on-kubernetes-with-strimzi
permalink: /docs/28-strimzi-and-kraft/
description: "A Kafka broker on Kubernetes needs a stable node id, its own volume, addresses that resolve and a controller quorum. Strimzi runs Kafka from a Kafka resource and two node pools, one for controllers and one for brokers, and the order and notification services run against it with only their addresses changed."
duration: "60 minutes"
---

The compose stack of the earlier examples starts one Kafka container, and its address is `kafka:9094` because compose names the container. On Kubernetes a broker is a pod that can be deleted at any moment. It has to come back with the same node id, find its own data and be reachable by a name that does not change when the pod does. Something has to hold those rules.

These examples run the same patterns on minikube, with Strimzi running Kafka, CloudNativePG running PostgreSQL and the Grafana LGTM image. The platform below runs the event notification, event-carried state transfer and ordering services on it, one set after another, with no change to their code or images.

## Setting up the cluster

One minikube profile named `edp` serves every Kubernetes example. Every version is an exact pin, and the table below is the only place in the chapters that lists them.

| Component | Version | Note |
|---|---|---|
| minikube | v1.39.0 | Docker driver, containerd runtime |
| Kubernetes | v1.37.2 | CloudNativePG 1.30 lists 1.37 as tested, not supported; nothing here failed on it |
| kubectl | v1.37.2 | Its built-in kustomize; no Helm |
| Strimzi | 1.3.1 | Cluster operator from the release manifest |
| Apache Kafka | 4.3.2 | KRaft, the same version as the compose broker |
| CloudNativePG | 1.30.1 | Operator from the release manifest |
| PostgreSQL operand | 18.6-202611090812-standard-trixie | Same minor as compose |
| grafana/otel-lgtm | 0.41.0 | One Deployment |

Install minikube and kubectl at those versions on your `PATH`; the scripts compare them with the pins and stop on a mismatch. Then:

```bash
examples/_k8s/cluster.sh up
```

The script refuses to run while a compose stack, another minikube profile or OpenShift Local is running. It creates the profile with 6 CPUs and 10 GiB of memory, publishes the ports below on `127.0.0.1` only, lets the node pull over plain HTTP from one in-cluster registry address, downloads the two release manifests and checks their sha256, and installs the CloudNativePG operator. Nothing is forwarded or tunnelled: a service is reached through a published NodePort or with `kubectl exec`.

| Port | Service |
|---|---|
| 30080 and 30082 | The two application ports of whichever set is deployed |
| 30300, 30320, 30100, 30909 | Grafana, Tempo, Loki, Prometheus |

`examples/_k8s/README.md` holds the rest: the namespaces, the fresh-state rule and how to add an example. Run `cluster.sh down` when you are done; the scripts run one profile at a time.

## Pattern: Operator-managed Kafka

### The problem

Four things make a Kafka cluster more than a container. Each broker has a node id that must survive a restart, because the cluster's metadata and its partitions refer to it. Each broker owns a volume, and the volume has to follow the node, not the pod. Clients need addresses that resolve inside the cluster. And in KRaft mode, which has no ZooKeeper, a quorum of controllers keeps the cluster metadata, and brokers fetch it from them.

A set of Deployments and Services can start the containers. It cannot decide the order in which to restart brokers, compute the listener configuration from the replica count, or give a replaced pod its old identity. That knowledge belongs to something that watches the cluster and acts on it.

### The solution

**Describe the cluster as resources and let an operator create and keep the pods, volumes, Services and configuration that realise it.**

This is the operator pattern: a controller for a custom resource, here the Strimzi cluster operator for `Kafka` and `KafkaNodePool`. You declare what you want; the operator reconciles toward it and reports in `status`.

{% include excalidraw.html file="28-operator-reconcile" alt="A Kafka resource and two KafkaNodePool resources are applied with kubectl; the Strimzi operator reads them and creates two StrimziPodSets, the broker and controller pods, a PersistentVolumeClaim per node and the bootstrap Service; it writes node ids and the Kafka version back to status" caption="Figure 28.1: The operator turns three resources into pods, volumes and a bootstrap address" %}

### How Strimzi models it

KRaft and node pools are the default since Strimzi 0.48, and every resource uses `apiVersion: kafka.strimzi.io/v1` (Strimzi changelog). The `Kafka` resource holds what is common to the cluster: version, listeners and broker configuration.

```yaml
apiVersion: kafka.strimzi.io/v1
kind: Kafka
metadata:
  name: edp
spec:
  kafka:
    version: 4.3.2
    metadataVersion: "4.3"
    listeners:
      - {name: plain, port: 9092, type: internal, tls: false}
    config:
      auto.create.topics.enable: true
      num.partitions: 3
      default.replication.factor: 1
      offsets.topic.replication.factor: 1
```

The replication factors are 1 because the cluster has one broker. That is a choice for a laptop. Auto-creation stays on, as in the compose stack.

The operator creates a Service named `edp-kafka-bootstrap` for the `plain` listener. That name is the address every client in the cluster uses, and it does not change when a broker pod is replaced.

## Controllers, brokers and node pools

A node pool is a group of Kafka nodes with the same roles, resources and storage ("Node Pools represent different groups of Kafka nodes"; Strimzi blog). A node's role is broker, controller or both. This cluster has two pools of one node each, and their only difference is the role.

```yaml
apiVersion: kafka.strimzi.io/v1
kind: KafkaNodePool
metadata:
  name: broker
  labels:
    strimzi.io/cluster: edp
spec:
  replicas: 1
  roles: [broker]
  storage:
    type: jbod
    volumes:
      - {id: 0, type: persistent-claim, size: 2Gi, deleteClaim: true, kraftMetadata: shared}
  resources:
    limits: {memory: 1Gi}
  jvmOptions: {-Xms: 512m, -Xmx: 512m}
```

The pool named `controller` is the same with `roles: [controller]`. The label `strimzi.io/cluster` ties a pool to its Kafka resource. `deleteClaim: true` removes the volume when the pool goes, which suits a lab: a fresh namespace starts empty.

Kubernetes reports what the operator made. Each pool's `status.nodeIds` holds its node id, the pods are named `edp-broker-<id>` and `edp-controller-<id>`, and each pod is owned by a `StrimziPodSet`, not by a ReplicaSet or StatefulSet. The quorum is visible with Kafka's own tool, run inside the broker pod:

```bash
kubectl -n edp-kafka exec edp-broker-0 -- /opt/kafka/bin/kafka-metadata-quorum.sh \
  --bootstrap-server localhost:9092 describe --status
```

It prints the controller as `LeaderId` and as the only entry in `CurrentVoters`, and the broker as the only entry in `CurrentObservers`. A broker is an observer: it fetches the metadata log from the controller and does not vote on it.

{% include excalidraw.html file="28-kraft-roles" alt="The controller node is the leader and the only voter of the metadata quorum; the broker node observes it and fetches metadata; applications connect to the broker through the bootstrap Service" caption="Figure 28.2: The controller holds the quorum, the broker observes it, and clients talk only to the broker" %}

Dedicated controllers are what Strimzi's documentation suggests for production, and dual-role nodes for development and testing. A majority of the controllers must be alive for the cluster to stay available, and surviving N failures takes 2N + 1 controllers (Apache Kafka, KRaft operations), so a real cluster runs at least three. One controller and one broker are the smallest cluster that shows the two roles apart. Setting `roles: [controller, broker]` on a single pool gives the dual-role form, which saves a pod and a gigabyte and hides the distinction.

Replacing a broker pod shows what the operator holds. The pod is deleted, a new one with a new UID takes its name, mounts the same volume claim and keeps its node id, and the end offsets of the topics are unchanged. The identity and the data belong to the node, not to the pod.

## The same services on new addresses

The event notification, event-carried state transfer and ordering services are not rebuilt. Their Containerfiles are the ones the compose stack used, and the images are built from them and loaded into the node. What changes is where each service finds its neighbours, and those addresses are already environment variables.

```yaml
# compose.yaml of the event notification example
CAMEL_COMPONENT_KAFKA_BROKERS: kafka:9094
QUARKUS_DATASOURCE_JDBC_URL: jdbc:postgresql://postgres:5432/edp
QUARKUS_DATASOURCE_PASSWORD: edp
QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT: http://lgtm:4318
```

```yaml
# Deployment, same service
- {name: CAMEL_COMPONENT_KAFKA_BROKERS, value: "edp-kafka-bootstrap.edp-kafka.svc:9092"}
- {name: QUARKUS_DATASOURCE_JDBC_URL, value: "jdbc:postgresql://edp-pg-rw.edp.svc:5432/edp"}
- name: QUARKUS_DATASOURCE_PASSWORD
  valueFrom: {secretKeyRef: {name: edp-pg-app, key: password}}
- {name: QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT, value: "http://lgtm.edp-lgtm.svc:4318"}
```

The variable names are the same and only the values move. The broker address is the bootstrap Service. The database address is the read-write Service that CloudNativePG creates for its cluster, and the password comes from the Secret the operator generates, not from a literal in the manifest. This is the payoff of the rule that Camel's broker setting and the Quarkus datasource and OpenTelemetry settings are read from the environment: the move to Kubernetes is a change of values.

{% include excalidraw.html file="28-compose-to-minikube" alt="Three columns map the compose address of Kafka, PostgreSQL and the LGTM collector to the Kubernetes address: kafka:9094 to edp-kafka-bootstrap.edp-kafka.svc:9092, postgres:5432 to edp-pg-rw.edp.svc:5432, lgtm:4318 to lgtm.edp-lgtm.svc:4318" caption="Figure 28.3: Three addresses change; the names of the variables do not" %}

Three details come from running them side by side. The three sets use the same Service names and NodePorts, so they run one after another: deploy a set, use it, delete it, deploy the next. Each set has its own database in the same PostgreSQL cluster, because the schemas of the three examples would otherwise collide on their Flyway history. And the ordering example needs two topics with three partitions, which a Job creates with the same `kafka-topics.sh` commands compose ran.

```bash
kafka-topics.sh --bootstrap-server edp-kafka-bootstrap.edp-kafka.svc:9092 \
  --create --if-not-exists --topic edp.shipping.shipments --partitions 3 --replication-factor 1
```

The Job runs the `kafka` image Strimzi publishes, which contains the same tools as the broker.

An address that is not rewritten does not fall back to anything. With the compose address left on `notification-service`, the pod cannot resolve `kafka`, Camel stops while starting its context with "No resolvable bootstrap urls", the pod restarts without ever becoming ready, and an order placed through `order-service` is accepted and never notified. On a fresh namespace nothing else serves the traffic, so the failure is total and visible at once.

> **🤖 Explore with your agent**
> "In examples/28-strimzi-and-kraft, apply the platform with kubectl, then read status.nodeIds of the KafkaNodePools controller and broker, and run kafka-metadata-quorum.sh describe --status inside edp-broker-0. Say which node leads, which one votes and which one observes, delete the broker pod, and show what is the same afterwards."
> Check: the controller node is the LeaderId and the only voter, the broker node is the only observer, and the replacement broker pod has a new UID, the same node id and volume claim, and the same end offsets on edp.orders.placed.

## Build, run, observe

```bash
examples/_k8s/cluster.sh up
cd examples/28-strimzi-and-kraft
./verify.sh
```

`verify.sh` recreates the namespaces, builds and loads six images on the first run, applies Kafka as two node pools with PostgreSQL and LGTM, runs the three sets of services in turn, replaces the broker pod, and ends with `39/39 checks passed`.

Try one change and rerun:

- `KAFKA_BROKERS=compose ./verify.sh` (demo code): `notification-service` keeps the compose address and never becomes ready.
- `NODE_POOLS=dual ./verify.sh` (demo code): one dual-role node replaces the two pools, and the checks that name the controller and the observer fail.
- `KEEP=1 ./verify.sh`, then `kubectl -n edp-kafka get pods,pvc -w`, delete the broker pod, and watch it return on the same claim.

## When not to use it

When one broker for development is all you need and Docker is already there, compose starts faster and has fewer parts. The operator pays off when something other than you replaces, upgrades or scales the pods.

When a cluster cannot run a controller and its custom resource definitions, for example a namespace-restricted team cluster, the operator is the wrong shape. Strimzi installs cluster-wide definitions, and its documentation notes that, depending on the cluster setup, installation typically requires cluster admin privileges.

The cluster built here is not fault tolerant. By the 2N + 1 rule one controller survives no failure, three survive one, and five survive two (Apache Kafka, KRaft operations).

## References

- Strimzi, [Deploying and Managing Strimzi](https://strimzi.io/docs/operators/latest/deploying.html): using Kafka in KRaft mode, node pools and roles
- Strimzi, [Configuring Strimzi](https://strimzi.io/docs/operators/latest/configuring.html): `Kafka`, `KafkaNodePool` and listeners
- Strimzi blog, 2023: [Kafka Node Pools: Introduction](https://strimzi.io/blog/2023/08/14/kafka-node-pools-introduction/) and [Node Pools and KRaft](https://strimzi.io/blog/2023/09/11/kafka-node-pools-supporting-kraft/)
- Strimzi, [CHANGELOG](https://github.com/strimzi/strimzi-kafka-operator/blob/main/CHANGELOG.md): KRaft and node pools by default, the `v1` API
- Apache Kafka, [KRaft operations](https://kafka.apache.org/43/operations/kraft/): `kafka-metadata-quorum.sh`
- minikube, [start](https://minikube.sigs.k8s.io/docs/commands/start/) (`--ports`) and [image load](https://minikube.sigs.k8s.io/docs/commands/image/)
- CloudNativePG, [Installation and upgrades](https://cloudnative-pg.io/docs/current/installation_upgrade/) and [Bootstrap](https://cloudnative-pg.io/docs/current/bootstrap/)
- [Prerequisites and stacks]({{ '/docs/00-prerequisites-and-stacks/' | relative_url }})
- [Event notification]({{ '/docs/02-event-notification/' | relative_url }})
- [Event-carried state transfer and claim check]({{ '/docs/03-event-carried-state-transfer/' | relative_url }})
- [Ordering and partitioning]({{ '/docs/04-ordering-and-partitioning/' | relative_url }})
