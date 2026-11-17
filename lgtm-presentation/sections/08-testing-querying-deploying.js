// Section: Testing, querying and deploying.
"use strict";

module.exports = function (lib) {
  const { divider, bulletSlide, figSlide } = lib;

  divider(
    "Testing, querying and deploying",
    "Proving the guards, asking the stream, changing the running system",
    `The patterns so far describe how events move. These patterns cover what surrounds them in practice. How do you test code that fails by omission, with a notification that never left or a second one that did? How do you ask a stream a question without a ksqlDB-style service? What happens to records when a consumer group is redeployed? And two smaller patterns close it out: a resequencer for events that span partitions, and a cache that is written only from committed events. Each part is backed by an example that runs against real brokers, and each states what it costs.`,
  );

  bulletSlide("TESTING", "Event-driven tests that bite", [
    ["Real brokers:", " Dev Services starts real Kafka and PostgreSQL, so a test meets the real behaviour of what it guards."],
    ["Bite cycle:", " prove a guard by removing it from an altered copy of the route. Exactly the one test that names the guard must fail."],
    ["Drain, not first match:", " a first match passes on a route that notifies twice. Only a read that waits out the window can assert absence."],
    ["Oracles:", " compute the expected partition with Kafka's own hash, and write the arrival order down. Do not wait for the application to produce it."],
    ["Limit:", " 20 rounds without a failure is not proof of no race. Compare against a run with the guard switched off."],
  ], `A synchronous service fails loudly with an exception or a status code. An event-driven one fails by omission: the notification that never left, the second one that did, the record keyed by the wrong field. A test that sends a message and waits for an answer passes on all of these. The route here has three guards: a repeated event id is dropped, a cancelled order gets no notification, and the notification is keyed by the order id. Each is a line a refactoring can delete while the tests stay green. Mutation testing, proposed by DeMillo, Lipton and Sayward, is the idea behind the bite cycle: the three hand-written patches are the mutants. The harness costs one Maven run per patch, so it runs from the verify script and not on every save.`);

  figSlide("TESTING", "Prove a guard by breaking it", "aa-bite-cycle",
    "notification-service route: an altered copy per guard and the one test that must fail",
    `The cycle has four steps. Copy the module, remove one guard from the copy, run the broker-backed tests against the copy, and read the reports. The set of failed tests must equal the one test that names the guard. A copy that fails two tests means a test is coupled to something it does not name. A copy that fails none means the guard is unprotected. The altered copies live under the build directory and the source is never touched. Write a bite for the guards a wrong release would hurt most: deduplication, the opt-out, the key and the retry policy. Camel route tests with AdviceWith answer a different and quicker question, what the route does to one exchange, with the consumer replaced by a direct endpoint and the producer by a mock.`);

  figSlide("COMPOSE TO KUBERNETES", "The same config on two targets", "ab-same-config-two-targets",
    "Kafka and PostgreSQL settings captured from Docker Compose and from Kubernetes, then compared",
    `Moving a service from compose to Kubernetes changes its addresses, and the settings underneath move too, with no report when one is lost. Reading two files side by side finds the lines that were copied. It does not find what an operator adds or changes, because the operator's defaults are in neither file. CloudNativePG defaults the write-ahead log level to logical, so its Cluster needs no line for it while compose has to ask. The complete view is the running system's own answer. The same fixed list of keys is read from the broker and from PostgreSQL on both targets, and the comparison lists what differs. Four settings differ here, each with a stated cause, and any other difference fails the comparison. A capture with a missing key fails too, because an empty file equals an empty file.`);

  bulletSlide("QUERYING STREAMS", "Querying streams without ksqlDB", [
    ["Question:", " deliveries per carrier per minute of event time, correct when a record arrives a few seconds after its minute ended."],
    ["Kafka Streams:", " ask the application. State lives in a window store in the service and a REST call reads it. A new question is a new endpoint."],
    ["Flink SQL:", " a job writes closed windows to a topic. A second system to run, and no question the job does not already compute."],
    ["RisingWave:", " materialized views read with psql. A new question is one more query, with state in the database."],
    ["Licences:", " ksqlDB and Materialize are not open source, so neither is used."],
  ], `Operations wants the number of deliveries per carrier per minute, and wants it right when a record turns up late. A consumer that increments a counter counts by arrival time, so a paused or replayed consumer puts a delivery in the wrong minute. Three open source engines answer the question from one topic. Each takes event time from the delivery timestamp and each closes a window by a lateness bound. The bound is the grace period in Kafka Streams and the delay in the watermark expression in Flink and RisingWave. All three get the same value, ten seconds. The engines agree only when an out-of-order record is inside the lateness of every one of them. None of them tells the reader about a record it dropped.`);

  figSlide("QUERYING STREAMS", "One topic, three ways to ask", "ac-three-ways",
    "shipping-service deliveries topic read by Kafka Streams, Flink SQL and RisingWave",
    `Where the state lives decides how a question is asked. In Kafka Streams the state belongs to the application, and with several instances a client has to be sent to the instance that holds the carrier it asks about. In Flink the answer leaves as records, and a window leaves once, when the watermark passes its end, so the output holds closed windows only. In RisingWave the answer is a table, open windows are visible, and an emit-on-window-close clause hides them. On late records the test scenario shows the cost of mismatched bounds. A record 7 seconds behind the newest event time is counted by all three. A record for a closed window is skipped by Kafka Streams with a warning and ignored by the other two. With a grace of zero in Kafka Streams, window 1 counts 3 where the others count 4.`);

  figSlide("RESEQUENCER", "Stream or batch resequencing", "ae-stream-vs-batch",
    "Camel Resequencer: stream form on a global sequence, batch form sorted by order id",
    `A per-order buffer in PostgreSQL restores order when an order's events share a key. When several producers and topics carry one order, only a sequence that spans all of them can. A resequencer across partitions holds each event in memory until an ordering rule releases it, so the order it restores is exactly as wide as the rule. Hohpe and Woolf define the Resequencer, and Camel implements a stream form and a batch form. The stream form needs one gapless global sequence. With per-order numbers, events with equal numbers collide and are dropped as duplicates with nothing logged. The batch form sorts within a batch, which makes disorder rarer and does not remove it. Held events live only in the process: offsets are committed on a timer, so records held at a stop are neither released nor read again. The stream form holds 1000 events by default, and then blocks the consumer.`);

  figSlide("DEPLOYING", "A rolling update, mid-roll", "af-roll-mid",
    "notification-service Deployment: maxSurge 1, maxUnavailable 0, partitions moving between members",
    `A consumer in a group owns partitions, and whether a change loses records, repeats them or only delays them depends on how pods are replaced. With a surge of one and no unavailable pods, the Deployment starts a new pod, waits for readiness, then removes an old one. Each start and each stop is a membership change and so a rebalance. The member that gives up a partition has committed only as far as its last commit, so records after that point can be read again by the new owner. The consumer protocol moves the driving of rebalances to the broker, and the image runs under either protocol unchanged. Every strategy here delivers at least once, so a consumer that cannot tolerate a repeat needs an idempotent consumer first. A breaking schema change is a separate risk: the insert fails, the offset is committed, lag stays zero and the row does not exist.`);

  figSlide("DEPLOYING", "Blue-green consumer cutover", "af-bg-cutover",
    "notify-blue and notify-green consumer groups: offsets exported from one and imported into the other",
    `Blue-green for consumers works on the unit of the consumer group. Blue is live under one group. Green is a second Deployment of the next version under its own group, at zero replicas. A new group has no offsets, so green would replay the topic or skip what blue had not read. The cutover gives green blue's position in four steps: scale blue to zero and wait until its group is empty, export its offsets, import them into green's group, scale green up. Nothing is missed, because green starts where blue's commits ended. Records can be read twice, bounded by what blue processed in its last commit interval. Rollback runs the same four steps backwards. The costs are two groups, a copy step and a stop between them. A compatible change does the same work with a rolling update and no copy.`);

  figSlide("WRITE-FORWARD CACHE", "A cache written only from events", "ag-write-forward-path",
    "order-service, Debezium, Kafka and a Camel route applying orders to Redis behind a version guard",
    `PostgreSQL and Redis share no transaction, so a request that writes both can die between the writes. A write-forward cache is written only from the events of committed changes, so it can fall behind its source but never contain something the source did not commit. The request writes the order and an outbox row that carries the full state in one transaction. Debezium publishes the row, and one Camel route applies it to Redis, comparing a version with a watch and a transaction because Redis has no conditional upsert. The staleness is the delay of the path: measured from the answer to the first read that finds the order in Redis, the median was about 490 milliseconds, close to the connector's default poll interval of 500. The costs are an outbox, a connector, a slot, a consumer and a second store, and a delay the reader sees. Where a reader must see its own write, read the source.`);

  figSlide("PRIVATE REGISTRY", "Build on the host, copy into a registry", "ah-build-and-copy",
    "buildah build, skopeo copy by digest into a private registry on an on-premises Kubernetes distribution",
    `The same images that run on compose and on minikube also run on an on-premises Kubernetes distribution, and nothing in the code changes. What differs is how the image arrives. A source build inside the cluster would produce a different image, so the unchanged Containerfile is built on the host with buildah, rootless and with no daemon, and copied into the registry the cluster already runs with skopeo. On a first copy into an empty registry a plain copy changes the digest, because skopeo compresses layers that buildah stored uncompressed. Preserving digests keeps the manifest as it is, or fails, and the Deployment then names the image by digest. TLS is verified against the cluster's published CA on every call, and the registry token is short-lived and held in one shell variable for one call.`);
};
