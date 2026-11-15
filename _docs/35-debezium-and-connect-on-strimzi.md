---
title: "Debezium and Connect on Strimzi"
order: 35
part: running-on-kubernetes-with-strimzi
permalink: /docs/35-debezium-and-connect-on-strimzi/
description: "On compose a connector is a JSON file somebody PUTs to a REST API, its plugin is whatever the running image holds and an offset change is a curl. On Strimzi the Connect image is built from a resource, each connector is a resource and an offset operation is an annotation on it."
duration: "60 minutes"
---

The connectors of chapters 8 to 10 are JSON files. A script PUTs each one to the Connect REST API, the plugin comes from whatever image the compose file names, and a change to an offset is a `curl` that someone has to remember to repeat in the next environment. Nothing in the repository says which connectors should exist, and nothing notices when one is missing.

On Kubernetes the operator that runs Kafka can run Connect too, and it can hold the three things that the REST API leaves outside the repository: the worker image, the connectors and their offsets.

{% include excalidraw.html file="35-build-and-run" alt="A KafkaConnect resource with a build section starts a build pod that downloads the plugin archive and checks its sha512, pushes the image to a registry with a fixed ClusterIP inside the cluster, and the Connect pod pulls it by digest; a checksum that does not match fails the build and no Connect pod starts" caption="Figure 35.1: The Connect image is built from the resource and stays in the cluster" %}

## Pattern: Connectors as declared resources

### The problem

A Connect worker image has to contain the connector plugins, and the image people reach for first, `quay.io/debezium/connect` in chapters 8 to 10, is built and run by Debezium, not by the operator. The operator runs its own image layout, so the `KafkaConnect` image is built from Strimzi's Kafka image plus the plugin. A connector is then a second artifact, a JSON document sent over HTTP to a running worker. The repository holds the file and the cluster holds the effect, and the two drift apart the first time someone edits a connector through the API.

Offsets add a third. Chapter 10 changed them with `PATCH` and `DELETE` on the REST API. A change made that way leaves no trace in the repository and no record of who made it.

### The solution

**Declare the Connect image, every connector and every offset operation as Kubernetes resources, and let the operator reconcile the worker toward them.**

The image becomes a section of the `KafkaConnect`, the connector a `KafkaConnector`, and an offset operation an annotation on that connector that names a ConfigMap. The REST API stays, read-only: the checks read connector status and offsets through it and write nothing.

### How Strimzi models it

A `KafkaConnect` with the annotation `strimzi.io/use-connector-resources: "true"` takes its connectors from `KafkaConnector` resources that carry the label `strimzi.io/cluster` (Strimzi, Deploying and Managing). Its `build` section lists the plugin and the registry that receives the image. Strimzi runs no registry of its own, so one has to be named.

```yaml
apiVersion: kafka.strimzi.io/v1
kind: KafkaConnect
metadata:
  name: edp-connect
  annotations:
    strimzi.io/use-connector-resources: "true"
spec:
  bootstrapServers: edp-kafka-bootstrap.edp-kafka.svc:9092
  groupId: edp-connect
  offsetStorageTopic: edp-connect-offsets
  configStorageTopic: edp-connect-configs
  statusStorageTopic: edp-connect-status
  config:
    offset.flush.interval.ms: 1000
    config.providers: secrets
    config.providers.secrets.class: io.strimzi.kafka.KubernetesSecretConfigProvider
  build:
    output:
      type: docker
      image: 10.96.100.50:5000/edp/connect:35
      additionalPushOptions: ["--tls-verify=false"]
    plugins:
      - name: debezium-postgres
        artifacts:
          - type: tgz
            url: https://repo1.maven.org/maven2/io/debezium/debezium-connector-postgres/<version>/debezium-connector-postgres-<version>-plugin.tar.gz
            sha512sum: <the checksum of that archive>
```

The group id and the three storage topics sit at the top of `spec`, not under `config`. The offsets topic is the one chapter 10 read on compose, `edp-connect-offsets`, now created in the Kafka cluster of chapter 28.

## Building the Connect image

The operator starts a build pod when the resource has a `build` section. The pod downloads each artifact, compares it with `sha512sum`, assembles an image `FROM` the Strimzi Kafka image and pushes it to `output.image`. The build uses Buildah. Kaniko, the earlier builder, was replaced after its repository was archived (Strimzi proposal 114, changelog). A `tgz` artifact is unpacked into the plugin path, and Strimzi does not scan what it downloads (Strimzi docs), so the checksum is the only integrity check the build has. It is optional in the schema and mandatory in this repository.

The registry is a Deployment of the CNCF Distribution image in `edp-kafka`, on an emptyDir volume, behind a Service with a fixed ClusterIP inside the service range. The push is plain HTTP, so Buildah is told not to verify TLS. The pull is the node's: Strimzi references the new image by digest, and containerd on the node does not pull over HTTP from an address it has not been told to trust. The profile setup of the examples tells it, for that one address. An external registry is not needed, nothing leaves the machine, and the image disappears with the namespace, so every run builds it again.

The build pod needs no extra privilege in a namespace with no Pod Security labels. Strimzi's proposal for the Buildah build says that it does not work under the restricted profile, which is one reason these namespaces carry no labels. Chapter 31 puts the other workloads under that profile.

A build is the slow part. The build pod runs for TBD(spike: 25 s) and the `KafkaConnect` is Ready after TBD(spike: 90 s), pull included.

A checksum that does not match the archive stops the build: no image reaches the registry and no Connect pod starts. The `PLUGIN_SHA=wrong` switch of the example runs it, and the message the operator reports is TBD.

## Two connectors on CloudNativePG

The two connectors are the ones of chapters 8 and 10. Two lines change in each: the host, now the read-write Service of the CloudNativePG cluster, and the credentials, now references that the config provider resolves inside the Connect pod.

```yaml
apiVersion: kafka.strimzi.io/v1
kind: KafkaConnector
metadata:
  name: order-outbox
  labels:
    strimzi.io/cluster: edp-connect
spec:
  class: io.debezium.connector.postgresql.PostgresConnector
  tasksMax: 1
  config:
    database.hostname: edp-pg-rw.edp.svc          # was: postgres
    database.user: ${secrets:edp/edp-pg-app:username}      # was: edp
    database.password: ${secrets:edp/edp-pg-app:password}  # was: edp
    # slot, publication, EventRouter and converters as in chapter 8
```

`KubernetesSecretConfigProvider` reads the secret CloudNativePG created for the application owner. It needs permission, so a Role in `edp` allows `get` on that one secret and a RoleBinding gives it to the ServiceAccount of the Connect worker. The resource holds a reference and the password never appears in it.

Debezium needs two things from PostgreSQL. The role must have the `REPLICATION` attribute, and `wal_level` must be `logical` (Debezium docs). CloudNativePG sets `wal_level` to `logical` by default (CloudNativePG docs). The role is declared on the `Cluster`:

```yaml
spec:
  managed:
    roles:
      - name: edp
        ensure: present
        login: true
        replication: true
        passwordSecret: {name: edp-pg-app}
```

CloudNativePG reconciles the attribute onto the role, so no SQL runs by hand, and the declaration has to be part of the whole `Cluster` manifest. Applied as a fragment by a second manager, it fails the admission webhook with `spec.storage.size: Invalid value: "": Size not configured`. The default `pg_hba` of CloudNativePG ends with a rule that admits any user with a password, so nothing in `pg_hba` changes.

Two measured failures belong to this combination.

The captured table must be owned by the connector's role. With `publication.autocreate.mode: filtered` the connector creates a publication for the tables in `table.include.list`, and PostgreSQL allows that only to an owner (Debezium docs). A table created through `kubectl exec psql` belongs to the `postgres` superuser, and the task fails with `No table filters found for filtered publication`. The example creates the table with `SET ROLE edp`. After the owner is fixed, the annotation `strimzi.io/restart-task: "0"` restarts the failed task and it runs again in TBD(spike: 3 s). The annotation `strimzi.io/restart: "true"`, which restarts the connector, did not clear the failed task.

Two Debezium connectors in one Connect cluster must not share a `topic.prefix`. Both of chapters 8 and 10 use `edp`. In one worker the second connector logs `Unable to register metrics as an old set with the same name` every five seconds and never takes its snapshot, because the connector's metrics name contains the prefix. The inventory connector therefore uses `edp-inventory`, with both `RegexRouter` patterns following it. The topics it writes keep their names, `edp.inventory.item-changes` and `edp.inventory.signals`.

The applications are the images of chapters 8 and 9, unchanged. An order placed through `order-service` is stored with its outbox row in one transaction (chapter 8), and `order-outbox` publishes it to `edp.orders.placed` keyed by order id with the `eventType` header, leaving the outbox table empty. An update of a row in `inventory.inventory_item` reaches `edp.inventory.item-changes` as a change envelope, and the Camel route of chapter 9 turns it into the document on `edp.inventory.items`.

The Connect pod serves its metrics on the port of the Strimzi Metrics Reporter, with `metricsConfig` set as for the brokers in chapter 32. The families start with `kafka_connect_`, and `kafka_connect_connector_metrics_status_info` carries the connector name and its state as labels. The `allowList` in the resource limits what is exposed to those families and the JVM's.

## Connector offsets in the resource

Chapter 10 listed offsets with `GET`, altered them with `PATCH` while the connector was stopped and read the consequences: an alter forward skips changes, and an alter backward does not rewind. Strimzi wraps the same three calls in the resource (Strimzi blog, Managing Connector Offsets; KIP-875).

The `KafkaConnector` names two ConfigMaps, one the operator writes and one it reads:

```yaml
spec:
  state: running
  listOffsets:
    toConfigMap:
      name: inventory-cdc-offsets
  alterOffsets:
    fromConfigMap:
      name: inventory-cdc-offsets-alter
```

An annotation starts the operation, and the operator removes it when the operation completes. `strimzi.io/connector-offsets=list` writes the offsets to the key `offsets.json` of the first ConfigMap, in the body the REST API returns:

```bash
kubectl -n edp-kafka annotate kafkaconnector inventory-cdc strimzi.io/connector-offsets=list
kubectl -n edp-kafka get configmap inventory-cdc-offsets -o jsonpath='{.data.offsets\.json}'
```

```json
{"offsets":[{"partition":{"server":"edp-inventory"},"offset":{"lsn":83886944,"lsn_proc":83886944,"messageType":"UPDATE"}}]}
```

The annotation is gone TBD(spike: 0 to 2 s) after a list. A list right after the connector starts can return `{"offsets":[]}`, because Connect stores offsets on a timer; the example sets `offset.flush.interval.ms` to one second, as chapter 10 did on compose.

{% include excalidraw.html file="35-offset-change-by-resource" alt="A running connector is stopped by setting state stopped; while stopped the annotation list writes offsets.json to a ConfigMap and the annotation alter reads new offsets from a ConfigMap, and the operator removes the annotation each time; state running resumes it; an alter while running leaves a Warning event, keeps the annotation and changes nothing" caption="Figure 35.2: An offset operation is an annotation on the resource, and it needs the connector stopped" %}

An alter is refused while the connector runs. The operator records a Warning event on the resource, `Connector is not in STOPPED state`, and leaves the annotation on the resource with the offsets unchanged. The annotation stays, and it fires later against the stopped connector with whatever the ConfigMap holds then, so the stale one is removed before the next step.

```bash
kubectl -n edp-kafka patch kafkaconnector inventory-cdc --type=merge -p '{"spec":{"state":"stopped"}}'
kubectl -n edp-kafka create configmap inventory-cdc-offsets-alter \
  --from-literal=offsets.json='{"offsets":[{"partition":{"server":"edp-inventory"},"offset":{"lsn":<wal end>,"lsn_proc":<wal end>,"lsn_commit":<wal end>}}]}'
kubectl -n edp-kafka annotate kafkaconnector inventory-cdc strimzi.io/connector-offsets=alter
kubectl -n edp-kafka patch kafkaconnector inventory-cdc --type=merge -p '{"spec":{"state":"running"}}'
```

The alter takes TBD(spike: 2 s) and Connect reads back the new position. The outcome is chapter 10's, now through the resource: a row changed while the connector was stopped sits before the new position and is skipped, and the next row changed after the resume arrives. The stop, the new position and the resume are three edits that a pull request can review.

The Connect REST status of a stopped connector shows no tasks, while the status of the resource can trail it by one poll. A connector created with `state: stopped` starts in that state (Strimzi changelog).

## Build, run, observe

```bash
examples/_k8s/cluster.sh up
cd examples/35-debezium-and-connect-on-strimzi
./verify.sh
```

The first run builds the Connect image inside the cluster. The script declares both connectors, places an order and changes inventory rows, then lists, refuses, alters and resumes the inventory connector's offsets, watching the topics and the status of the resource; it ends with `25/25 checks passed`.

Try one change and rerun:

- `PLUGIN_SHA=wrong ./verify.sh` (demo code): the build fails and every check that needs Connect fails.
- `KEEP=1 ./verify.sh`, then `kubectl --context edp -n edp-kafka get configmap inventory-cdc-offsets -o yaml` shows the offset the last list wrote.

## When not to use it

A connector tried once on a laptop needs no resource. The REST API and a JSON file are faster, and chapter 10 uses them for that.

The registry here is an emptyDir behind a fixed address, and it is gone with its namespace. It rebuilds the image on every run, which is the point of a test and a cost in a cluster that restarts its Connect pods often; a registry with persistent storage and a trusted certificate takes its place there, and `--tls-verify=false` goes away.

The offset operations on a resource are as destructive as the REST calls. Do not alter an offset to repair a consumer: the change goes into the connector for every reader of the topic, and a skipped batch is a gap in every one of them (chapter 10).

A plugin that Strimzi downloads is only as trustworthy as its checksum. The checksum comes from the same place as the archive unless it is copied in from a source that is not, and a pull request that changes both has to be read.

> **🤖 Explore with your agent**
> "In examples/35-debezium-and-connect-on-strimzi, read connect/kafkaconnect.yaml and connectors/inventory-cdc.yaml, run ./verify.sh with KEEP=1, then set the KafkaConnector inventory-cdc to state stopped, update SKU-1 in inventory.inventory_item, alter its offset to the current WAL position through the ConfigMap inventory-cdc-offsets-alter, resume it, and read edp.inventory.item-changes. Then annotate it with the alter while it is running. Say which edits belong in the repository and what the operator leaves behind after the refused alter."
> Check: the update made while stopped never reaches edp.inventory.item-changes, the next update does, and the refused alter leaves a Warning event and the annotation on the resource.

## References

- Strimzi, [Deploying and Managing Strimzi](https://strimzi.io/docs/operators/latest/deploying.html): Kafka Connect, build, `KafkaConnector` resources, connector offsets, loading configuration from external sources; and [Configuring Strimzi](https://strimzi.io/docs/operators/latest/configuring.html) for the `KafkaConnect` and `KafkaConnector` schemas
- Strimzi blog, Kate Stanley, [Managing Connector Offsets](https://strimzi.io/blog/2024/11/04/managing-connector-offsets/), 2024
- Strimzi, [proposal 114](https://github.com/strimzi/proposals/blob/main/114-use-buildah-instead-of-kaniko.md), Buildah instead of Kaniko, and the [changelog](https://github.com/strimzi/strimzi-kafka-operator/blob/main/CHANGELOG.md)
- Apache Kafka, KIP-875: [First-class offsets support in Kafka Connect](https://cwiki.apache.org/confluence/display/KAFKA/KIP-875%3A+First-class+offsets+support+in+Kafka+Connect)
- Debezium documentation, [Debezium connector for PostgreSQL](https://debezium.io/documentation/reference/stable/connectors/postgresql.html) and [Outbox Event Router](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html)
- CloudNativePG documentation, [PostgreSQL configuration](https://cloudnative-pg.io/docs/current/postgresql_conf/) and [Declarative role management](https://cloudnative-pg.io/docs/current/declarative_role_management/)
- Chapter 8 for the outbox, chapter 9 for the log tail and the inventory service, chapter 10 for the offsets on compose, chapter 28 for the cluster, chapter 32 for monitoring the Connect pod
