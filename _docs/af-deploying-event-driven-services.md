---
title: "Appendix F: Deploying event-driven services on minikube"
order: 41
marker: "F"
label: "Appendix F"
part: appendices
permalink: /docs/af-deploying-event-driven-services/
description: "A deployment stops consumers that own partitions. A stop and restart delays records, a rolling update repeats some, a breaking schema change loses them silently, and a blue-green cut-over needs the old group's offsets. Each is run on minikube under a producer that numbers every record."
duration: "60 minutes"
---

A consumer in a group owns partitions. Stopping it hands them to another member or to nobody, a record it read and had not committed can be read again, and a record it could not store may be skipped, leaving only an error line in the log. Whether a change loses records, repeats them or only delays them depends on how the pods are replaced and on whether the new code reads what the old code wrote.

The strategies below run on a minikube cluster with two existing images, unchanged: a notification-service, whose group, protocol and client id come from the environment, and an order-service that answers `POST /orders`. A producer Job sends numbered records (`O-41000` and up) at a fixed rate, so a record that is missing, repeated or read by two groups is found by its id.

## Stop and restart

The Deployment strategy `Recreate` kills every existing pod before it creates new ones (Kubernetes, Deployments). For a consumer group it has the fewest moving parts: one membership ends, another begins, and Kafka keeps the records in between.

{% include excalidraw.html file="af-stop-before" alt="A producer Job writes to edp.orders.placed, whose three partitions are each read by one pod of the group, and every record becomes one row in PostgreSQL" caption="Figure F.1: A stop and restart starts from a group that is keeping up" %}

Nothing is lost, because the topic holds what the producer sent while no consumer ran. The stop costs time: the backlog grows for as long as the new pods need to start.

{% include excalidraw.html file="af-stop-gap" alt="During the gap the producer keeps sending, the topic's backlog grows, no pod exists and no row is written" caption="Figure F.2: The topic keeps the records while no consumer runs" %}

New pods rejoin the group, read from the committed offset and drain the backlog faster than it grew. The offset trails the last processed record, because the Kafka client commits on a timer, the Camel Kafka default. A record processed after the last commit can be processed again, and the `attempts` column of the notification table reads 2 for it. How many repeat depends on timing and can be none. An idempotent consumer makes the repeat harmless.

{% include excalidraw.html file="af-stop-after" alt="New pods resume each partition from the committed offset; every record has a row and records after the last commit have attempts 2" caption="Figure F.3: Nothing is lost; records after the last commit may be processed twice" %}

Choose a stop and restart when the old and the new version must not run together, or when a gap of one pod start is acceptable.

## Rolling update of a consumer group

A rolling update replaces pods in steps. With `maxSurge: 1` and `maxUnavailable: 0` the Deployment starts a new pod, waits for it to be ready, then removes an old one, so the Deployment never has fewer than three available pods.

```yaml
strategy:
  type: RollingUpdate
  rollingUpdate: {maxSurge: 1, maxUnavailable: 0}
```

{% include excalidraw.html file="af-roll-before" alt="Three pods of revision 1 each own one partition of edp.orders.placed under group notify-roll, with a RollingUpdate strategy of maxSurge 1 and maxUnavailable 0" caption="Figure F.4: A rolling update starts from three serving members" %}

Each start and each stop changes the group's membership, and a membership change is a rebalance. Partitions move between members, and the member that gives one up has committed only as far as its last commit, so the records after that point can go to the new owner again.

{% include excalidraw.html file="af-roll-mid" alt="Two revision 1 pods keep their partitions, a third leaves once a revision 2 pod has joined, and the rebalance moves a partition and delivers uncommitted records again" caption="Figure F.5: Every membership change moves partitions" %}

Under the classic group protocol the members take part in the rebalance. With the consumer protocol of KIP-848 the group coordinator on the broker drives it, declaring an assignment and reconciling each member toward it (Apache Kafka, KIP-848). The notification-service selects the protocol with `GROUP_PROTOCOL`, and the image runs under either one unchanged. Neither protocol guarantees that no record is repeated: a record read and not yet committed when its partition moves is delivered again, so the repeat is bounded by what was processed since the last commit at each move.

{% include excalidraw.html file="af-roll-after" alt="Three pods of revision 2 own the partitions; every record has a row and a record read again has attempts 2" caption="Figure F.6: At least once: nothing missing, a bounded number, possibly none, repeated" %}

A readiness probe decides when the Deployment counts a new pod as available and lets the rollout continue. The pod joins the group when the application starts, whatever the probe reports, and `maxUnavailable: 0` keeps capacity from dropping.

## Rolling update of a service under requests

A service behind a Kubernetes Service has no group to rebalance. The risk is at the edge: a request that reaches a pod while it is being removed.

{% include excalidraw.html file="af-http-roll" alt="A client posts orders to a Service that routes to a ready pod and a pod being removed; a request in flight on the removed pod is reset and retried, each 201 leaves one event on edp.orders.placed and a failed request leaves none" caption="Figure F.7: A removed pod can drop requests in flight; the client retries" %}

The order-service answers `POST /orders` and publishes one event per accepted order. During a rolling update with a readiness probe, requests in flight on a pod that is removed can fail at the connection level, as a reset or a timeout, and the client has to retry them. No request was answered with a 5xx status. The publish happens before the response, so a request that fails publishes nothing, and each 201 left exactly one event, never none and never two. A retry of an order that was accepted is refused with 409 on its id, so a retry cannot publish a second event.

Quarkus can drain running requests: `quarkus.shutdown.timeout` waits for them, and `quarkus.shutdown.delay` keeps serving while the readiness probe reports down, but only when `quarkus.shutdown.delay-enabled` is true, a build-time property that defaults to false (Quarkus, all configuration). The Deployment sets none of them, because the image stays as built. The guarantee does not depend on them: it rests on the client's retry and on the id that makes the retry safe.

## A breaking schema change

The notification-service reads `orderId`. A producer that renames it to `orderRef` ships a new version of the contract, and the consumer of the old version cannot know.

{% include excalidraw.html file="af-schema-breaking" alt="A producer sends orderRef instead of orderId; the topic accepts it, the v1 consumer's insert fails because order_id is null, the offset is committed anyway so group lag is zero, no row exists and the only trace is an error line in the log" caption="Figure F.8: A breaking change is a silent loss" %}

The record is read, the insert fails on the missing value, Camel logs `Failed delivery` and the route moves on. The offset is committed, the lag is zero and the row does not exist. A dashboard on lag stays green. Plain JSON on a topic has no gate between producer and consumer: the broker accepts whatever the producer sends.

An additive change takes two compatible steps and a third that removes, the expand and contract sequence that Danilo Sato describes as ParallelChange. First the producer expands: it sends both `orderId` and `orderRef`. The old consumer reads the field it knows and ignores the other, and consumers of the new version read `orderRef`. Every record is stored.

{% include excalidraw.html file="af-schema-expand" alt="The producer sends orderId and orderRef on one topic; the v1 consumer reads orderId, the v2 consumer reads orderRef, and both store every record" caption="Figure F.9: An additive change loses nothing" %}

Then each consumer is deployed to read `orderRef`, one of the strategies above. The producer contracts last, when it stops sending `orderId`, and only once no consumer still reads it.

{% include excalidraw.html file="af-schema-contract" alt="Three steps in order: expand by sending both fields, migrate every consumer to the new field, contract by no longer sending the old field" caption="Figure F.10: The change that removes a field comes last" %}

When a change cannot be made compatible, the new shape gets a new topic. A migration consumer reads the old topic, converts each record and writes it to the new one. Producers move to the new topic, consumers follow, and the old topic is retired when its retention has passed.

{% include excalidraw.html file="af-schema-new-topic" alt="A migration consumer reads the old v1 topic and writes a new v2 topic; the v2 producer writes the new topic, v2 consumers read it, and the v1 producer stops after the cut" caption="Figure F.11: An incompatible change gets its own topic" %}

It costs a topic, a consumer and a window with two sources of truth, and it is drawn, not run, because the notification-service reads fixed topics.

## Blue-green consumers: cut-over and rollback

Blue-green runs the new version beside the old one and moves the load in one step. For consumers the unit is the consumer group. Blue is the live Deployment under group `notify-blue`. Green is a second Deployment of the next version under its own group, `notify-green`, at zero replicas.

{% include excalidraw.html file="af-bg-blue-live" alt="Blue runs three replicas under group notify-blue with committed offsets; green has zero replicas, a different group and no offsets" caption="Figure F.12: One consumer group per colour" %}

A new group has no offsets, so green would replay the topic or skip what blue had not yet read. The cut-over gives green blue's position instead. Kafka resets the offsets of a group only while its consumers are inactive, and accepts a CSV file of values (Apache Kafka, basic operations). The target group can be new: it needs no members and no earlier offsets.

```bash
kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group notify-blue \
  --reset-offsets --to-current --all-topics --dry-run --export > offsets.csv
kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group notify-green \
  --reset-offsets --from-file offsets.csv --execute
```

The export has one `topic,partition,offset` line per partition and no header. The cut-over is four steps: scale blue to zero and wait for its group to be Empty, which its members reach by leaving the group on shutdown; export; import; scale green up.

{% include excalidraw.html file="af-bg-cutover" alt="Four steps in order: scale blue to 0, export blue's offsets, import them into green's group, scale green to 3; records after blue's last commit are processed again by green" caption="Figure F.13: Green starts where blue's commits ended" %}

Nothing goes missing, because green starts where blue's commits ended. Records can be read twice: blue processed records after its last commit, green starts at that commit, and both groups store them. The overlap is bounded by what blue processed in its last commit interval, and it can be zero.

Rollback is the same four steps the other way: stop green, copy green's offsets to blue's group, start blue. Blue's group exists and holds older offsets, and the import replaces them, so blue resumes after what green processed. Without the copy a new group replays the topic and an old group resumes at its old position, reading everything since the cut-over again.

{% include excalidraw.html file="af-bg-rollback" alt="Green is live and blue is at zero replicas with older offsets; the rollback scales green to 0, exports green's offsets, imports them into blue's existing group and scales blue to 3" caption="Figure F.14: Rollback is a cut-over in the other direction" %}

## Build, run, observe

```bash
examples/_k8s/cluster.sh up
cd examples/af-deploying-event-driven-services
./verify.sh
```

`verify.sh` deploys the two images unchanged and replaces them under a producer at a fixed rate: a stop and restart, a rolling update, a rolling update under HTTP requests, a breaking and a compatible schema change, a cut-over and a rollback. Watch the rows per id and the group offsets. It ends with `30/30 checks passed`.

Try one change and rerun:

- `GROUP_PROTOCOL=classic ./verify.sh` (demo code): the rolling update runs on the classic protocol, and the group is no longer a consumer-protocol group.
- `OFFSET_COPY=off ./verify.sh` (demo code): green starts at the beginning of the topic and blue resumes from its old offsets, so the groups process the same ids again.

## When not to use it

Every strategy here delivers at least once. A consumer that cannot tolerate a repeat needs an idempotent consumer or Kafka transactions first, and no deployment strategy replaces either.

Blue-green consumers pay for two groups, a copy step and a stop between them. When the old and the new version can read the same topic together, which is true of every compatible change, a rolling update does the same work with no copy. When a consumer is stateless and a delay of one pod start is acceptable, the stop and restart is the least to operate.

A client of a rolling service has to expect failed requests, because a connection open to a pod that is removed can be reset. It retries, and the service needs a way to tell a retry from a new request.

> **🤖 Explore with your agent**
> "In examples/af-deploying-event-driven-services, run verify.sh with OFFSET_COPY=off, then query notifications.notification for the groups notify-blue and notify-green in the id range O-51000 to O-54999. List the ids both groups hold and say why green re-read them. Then read group_offsets_copy in examples/_k8s/lib.sh and state what it exports from the source group, what it imports into the target, and why the source group must have no members."
> Check: with OFFSET_COPY=off the two groups share every id blue had processed before the cut-over and no id is missing; with the copy they share only the records after blue's last commit, at most what blue wrote in its last commit interval.

## References

- Kubernetes, [Deployments](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/) (the `Recreate` and `RollingUpdate` strategies, `maxSurge` and `maxUnavailable`)
- Apache Kafka, [KIP-848: The Next Generation of the Consumer Rebalance Protocol](https://cwiki.apache.org/confluence/display/KAFKA/KIP-848%3A+The+Next+Generation+of+the+Consumer+Rebalance+Protocol) (the group coordinator drives assignment)
- Apache Kafka, [Basic Kafka operations](https://kafka.apache.org/43/operations/basic-kafka-operations/) (resetting consumer group offsets: inactive consumers, `--from-file`, `--execute`)
- Quarkus, [All configuration](https://quarkus.io/version/3.40/guides/all-config) (`quarkus.shutdown.timeout`, `quarkus.shutdown.delay`)
- Adam Bellemare, *Building Event-Driven Microservices*, 2nd ed., O'Reilly, 2025, chapter 21: Deploying Event-Driven Microservices
- Danilo Sato, [ParallelChange](https://martinfowler.com/bliki/ParallelChange.html), martinfowler.com, 2014: expand, migrate and contract
- [Event notification]({{ '/docs/02-event-notification/' | relative_url }}): the order-service
- [Optimizing subscribers]({{ '/docs/06-optimizing-subscribers/' | relative_url }}): the notification-service and its consumer settings
- [Idempotent consumer and inbox]({{ '/docs/11-idempotent-consumer-and-inbox/' | relative_url }}): making a repeat harmless
- [Exactly-once with Kafka transactions]({{ '/docs/12-exactly-once-with-kafka-transactions/' | relative_url }}): transactions
- [Schema evolution and upcasting]({{ '/docs/18-schema-evolution-and-upcasting/' | relative_url }}): schema evolution
- [Strimzi and KRaft]({{ '/docs/28-strimzi-and-kraft/' | relative_url }}): the cluster
