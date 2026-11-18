# Event-Driven-Patterns-with-Quarkus-and-Camel

Source: Event-Driven-Patterns-with-Quarkus-and-Camel.pptx · 123 slides

## [001] PATTERN CATALYST · Event-Driven Patterns with Quarkus and Camel

Kafka, Debezium, Strimzi and the patterns that keep services in step

> Event-driven patterns on one stack: Quarkus services and Apache Camel routes on Kafka, run first on Docker Compose and then on Kubernetes with Strimzi. Readers are Java developers, Camel integrators, platform engineers and architects who already know what a topic is. The code comes from tested examples, and every example uses one order-to-delivery domain with order, inventory, payment, shipping and notification services.

## [002] AGENDA · Eight sections and a decision table

p. 2

Section | What it covers

Opening | The stack, the running example, events against row updates

Events between services | Notification, state transfer, claim check, keys, resequencing, tuning, share groups

Getting events out reliably | Outbox, log tailing, offsets, inbox, exactly-once, dead letters

Event sourcing | Event store, snapshots, CQRS, replay, schema evolution

Processing and coordinating streams | Enrich, join, aggregate, sagas, decisions on events

Observing event flows | Traces, metrics and logs across asynchronous hops

Running on Kubernetes with Strimzi | KRaft, topics as resources, admission, monitoring, scaling, mirroring, Connect

Testing, querying and deploying | Tests, stream queries, resequencing across partitions, rolling deploys, write-forward cache

Choosing a pattern | One table from problem to pattern

> The sections follow one order-to-delivery system from the first event to a running Kubernetes cluster. For a 90-minute path, take the opening, events between services, getting events out reliably and event sourcing in full. Compress processing and coordinating streams to its headline slides on enrichment, windows and sagas. Compress running on Kubernetes with Strimzi to the figures, and testing, querying and deploying to the two deployment figures. The first five sections run on Docker Compose and the Kubernetes section on minikube with the Strimzi operator. Finish on the decision table.

## [003] AUDIENCE AND STACK · Who this is for, and what it runs on

p. 3

Component | Version | Role

Quarkus platform | 3.40.1.2 (LTS) | Services; includes Camel Quarkus 3.40.0

Apache Camel | 4.22.2 (LTS) | Routes and integration patterns

Apache Kafka | 4.3.2, KRaft | The log every pattern publishes to and reads from

PostgreSQL | 18.6 | State, outbox tables, event store, on the CloudNativePG image

Apicurio Registry | 3.3.4 | Schemas for the evolution examples

Debezium Connect | 3.7.1.Final | PostgreSQL connector and the outbox router

Grafana LGTM | 0.41.0 | Loki, Grafana, Tempo and Prometheus behind one OTLP endpoint

Strimzi | 1.3.1 | Kafka operator for the Kubernetes runs

> The audience is Java and Quarkus developers, Camel integrators, platform engineers and architects who already know what a topic and a consumer group are. The stack is Quarkus services with Camel routes on Kafka, run first on Docker Compose and then on Kubernetes with Strimzi. Every version is an exact pin, the newest stable release that works with the rest, and nothing needs a licence or a subscription. Kafka is 4.3.2 in both run targets because that is the newest version the latest Strimzi supports. Kafka runs in KRaft mode, so there is no ZooKeeper anywhere.

## [004] RUNNING EXAMPLE · One shop, five services

p. 4

Order, inventory, payment and shipping joined by topics; notification reads every topic

> Every example is a piece of one business: an online shop that sells and ships physical goods. Five services each own one step. Order owns the order and its lifecycle, inventory owns stock, payment owns payments, shipping owns shipments and the carrier choice, and notification tells the customer. They share no tables and react to events on Kafka. Each knows only the topics it reads and the topics it writes. Nine events carry the flow, and topic names follow edp, a domain and an event, with dots and hyphens only. Two patterns call the order service: event notification fetches the current order, and a claim check fetches a stored attachment.

## [005] RUNNING EXAMPLE · The order and shipment lifecycles

p. 5

An order moves one step at a time; a shipment follows its own path

> An order moves one step at a time: PLACED, PAID, SHIPPED, DELIVERED. It can become CANCELLED only from PLACED or PAID, and DELIVERED and CANCELLED are final. A shipment moves PENDING, SCHEDULED, IN_TRANSIT, DELIVERED. Two fictional carriers deliver the parcels, FASTLINE by road and AIRCO by air. The failure paths are part of the model. Out of stock cancels the order and tells the customer. A declined payment releases the reserved stock and cancels the order. A late delivery has to raise an alert. The same event delivered twice must not be applied twice, and a service that is down finds its events waiting in Kafka when it returns.

## [006] THE STACK · One stack for every example

p. 6

curl posts to the service; Kafka, PostgreSQL, Connect and LGTM around it

> Every example runs on the same local stack: a Kafka broker, PostgreSQL with logical decoding enabled, Apicurio Registry, Kafka Connect with Debezium, and a Grafana LGTM container that receives traces, metrics and logs. An example adds its own Quarkus services on a shared network. Kafka is published on 9092 for the host and reached as kafka:9094 from containers, PostgreSQL on 5432, Apicurio on 8081, Connect on 8083 and Grafana on 3000. Prometheus is on 19090 because Cockpit, often enabled on Fedora and RHEL, holds 9090. Run one stack at a time: the services publish fixed host ports, so a second copy fails to bind them.

## [007] THE STACK · One endpoint, three backends

p. 7

Traces, metrics and logs leave the service over OTLP and land in Tempo, Prometheus and Loki

> Traces, metrics and logs reach the LGTM container through OpenTelemetry. A service exports all three signals over OTLP with http/protobuf to one endpoint, and the container routes them: traces to Tempo, metrics to Prometheus and logs to Loki. Camel's OpenTelemetry extension creates a span per route and processor. The Quarkus Micrometer and OpenTelemetry extension sends the meters over the same path, so no Prometheus scrape is configured, and the log records reach Loki with no extra setting. Grafana at port 3000 needs no login. In Explore, pick Tempo for traces, Prometheus for metrics or Loki for logs and filter on the service name.

## [008] EVENTS, COMMANDS, DOCUMENTS · One command, two storage outcomes

p. 8

ChangeOrderStatus applied to an orders row and to the order_event table

> Three message types and one storage decision. An Event Message announces that something happened, in the past tense, and the sender neither knows nor cares who reads it. A Command Message tells one receiver to perform a named action, and the receiver may refuse it. A Document Message carries a complete piece of data and prescribes nothing. The storage decision is separate: the same command either overwrites the status in an orders row, which keeps the current state and loses the old one, or appends a new version to the order_event table, which keeps every status and can rebuild the state by replay. Use a row when the current state is the only question, and events when the past is part of the requirement.

## [009] SECTION · Events between services

What an event carries, which partition it lands in, and how fast it moves

> These are the choices a team makes the moment one service tells another that something happened. The first is the size of the event: only an identifier, the whole state, or a reference to a stored attachment. The second is order: Kafka orders records only within a partition, so the record key decides which events can be trusted to arrive in sequence. The third is speed: how a publisher batches, how a subscriber fetches and polls, and how a share group lets any worker take any record.

## [010] EVENT NOTIFICATION · Event notification

p. 10

- Event notification announces that something happened and names the thing, leaving each consumer to ask the source for the details it needs.
- Problem. A service that calls every interested party must know them all and wait for each. A consumer told only that something happened cannot act.
- Solution. order-service publishes OrderPlaced with the order id after the commit. notification-service fetches the order over HTTP.
- Cost. Each consumer adds one request per event, reads current state instead of state at event time, and cannot finish while the source is down.
- Fit. Use it when the details change and the latest value is what matters.

> Order placement matters to inventory, to notification and to anyone who wants a metric. If order-service called each of them, it would have to know who they are and change whenever a new party appears. With an event notification it says that order O-1001 was placed and is done. Fowler separates this from event-carried state transfer, event sourcing and CQRS, which share the name event-driven. The producer holds no list of consumers, but every consumer still depends on order-service for the details. A notification built after the order was cancelled says CANCELLED, which is right for a customer and wrong for an auditor who wants the order as it was when placed.

## [011] EVENT NOTIFICATION · The event names the order

p. 11

order-service publishes after the commit; notification-service fetches the order and stores a Notification

> The order is stored in one transaction, and the event is fired from inside it as a CDI event. A transactional observer receives it only after the commit succeeded and hands it to a Camel route that publishes it keyed by the order id. Publishing after the commit matters to the consumer, which fetches the order the moment it reads the record: a record published before the commit could be read before the row exists. notification-service unmarshals the event, calls order-service with the Camel http component and stores the customer, the amount and the status as order-service reported them at that moment. A single trace covers the request, the publish, the consume and the fetch across both services, because the trace context travels in the Kafka headers and the HTTP request.

## [012] EVENT NOTIFICATION · A source that is down holds the record

p. 12

The record stays uncommitted and is polled again until the fetch succeeds

> The cost of the fetch shows when order-service is stopped. The consumer route sets breakOnFirstError to true, so a record whose route fails is not skipped: the consumer stops at that record and polls it again. After two redeliveries one second apart the exchange fails, and the next poll returns the same record, so the notification waits instead of being lost. Without that option the offset moves past the failed record and the notification never appears. The same behaviour has a limit: a record bound to fail every time, such as an order that will never exist, stalls its partition. A fetch that fails for a permanent reason needs its own handling, and publishing after the commit still leaves a window in which the process can stop between the commit and the publish.

## [013] STATE TRANSFER AND CLAIM CHECK · State transfer and claim check

p. 13

- Event-carried state transfer puts the state a consumer needs into the event, so the consumer keeps its own copy and never calls the source.
- Problem. Notification couples availability: a consumer cannot answer while the source is down, and each use costs a request.
- Cost. The copy is as fresh as the last event applied, and the producer cannot rename a field consumers read.
- Claim check stores the large part of a message in a persistent store and passes a reference, so only consumers that need the content fetch it.
- Result. A 100 KB attachment travels as a claim and the event stays under 1 KB; the attachment request fails while the source is down.

> Put the whole order in the event and notification-service answers customer questions from its own table, even with order-service stopped. The copy needs no HTTP client, retry or circuit breaker because it makes no call. One guard matters: an event that would move the status backwards is ignored, so a late OrderPlaced cannot undo a cancellation that arrived first, because the two topics have no order between them. Carrying state also grows the event. Kafka bounds the size, with a producer limit of 1048576 bytes by default, so a large attachment goes into PostgreSQL and the event carries a claim. The cost is a second store with its own lifecycle, and these claims never expire. Do not copy state that must be current, such as a stock level that decides something right now.

## [014] STATE TRANSFER AND CLAIM CHECK · What the consumer does after the event

p. 14

Notification carries an id and the consumer fetches; state transfer carries the Order and the consumer reads its copy

> Event notification carries only the order id, so the consumer calls the source and receives current state. Event-carried state transfer carries the whole Order and its status, so the consumer upserts its own order_copy table and answers from it. The first keeps one owner and always returns current data, but it couples availability and sends the source one request per event per consumer. The second removes the call and the coupling to availability, and replaces them with a dependency on the event's fields and a copy that is only as fresh as the last event applied. Choose notification when one owner must answer with current data. Choose state transfer when consumers read most of the state and it is small.

## [015] STATE TRANSFER AND CLAIM CHECK · Store, publish the claim, fetch on demand

p. 15

order-service stores the attachment in orders.claim_check; the event carries only the claim id and size

> The attachment is stored in the order service's claim_check table in the same transaction as the order, and the event carries a claim with an id and a size in place of the content. Any holder of the claim id reads the content from the order service, and notification-service fetches it only for the attachment endpoint, never for the order fields. Camel has a Claim Check EIP, but it stashes content for the same route, so it cannot carry content from one service to another; between services the store has to be shared, here PostgreSQL. Sending the same attachment inline makes the producer refuse the event with a RecordTooLargeException, and the order has already committed. Do not use a claim check for small payloads: it adds a second store, a second call and a lifecycle.

## [016] PARTITIONED ORDERING · Keys and partitions

p. 16

- Partitioned ordering gives every event of an entity the same record key, so the entity lives in one partition, the only place Kafka guarantees order.
- Problem. Replicas read partitions at different speeds, so an order's events in different partitions are applied in whatever order the replicas reach them.
- Key. The default partitioner hashes the key bytes with murmur2 modulo the partition count, so one key always lands in one partition.
- Wrong keys. The event type splits one order across two partitions. The carrier uses at most two of three partitions and leaves one idle.
- Limit. Order holds per entity only, and the partition count caps parallelism.

> The shipping service publishes ShipmentScheduled, ShipmentInTransit and ShipmentDelivered, and the order service turns each into a status with the last write winning. If Delivered is applied before Scheduled, the order ends SHIPPED and no error is raised. Kafka's documentation states the guarantee as order per partition and none across partitions. Keyed by order id, all three events of one order go to one partition in increasing offsets. Keyed by event type, one replica owns Scheduled and another owns the other two, so pausing the first makes the status regress from DELIVERED to SHIPPED. Keyed by carrier, nothing reorders, but only two keys exist, so one partition has nothing to do.

## [017] PARTITIONED ORDERING · The key picks the partition

p. 17

Order id, event type and carrier as keys over three partitions

> Three key strategies run against the same three-partition topic. The order id keeps an order's events together and spreads different orders across the partitions. The event type puts ShipmentScheduled in one partition and the other two events in another, for every order, so one order spans two partitions and two replicas. The carrier keeps each order in one partition but has only two values, so at most two of the three partitions receive records. In the stalled-replica run, the order id key ends DELIVERED, the event type key ends SHIPPED, and the carrier key reorders nothing but leaves a replica idle. Keying by entity id also leaves the partition count as the limit on parallelism, and it does not order events across entities.

## [018] PARTITIONED ORDERING · The key is a header on the record

p. 18

[chip] JAVA

```java
@Override
public void configure() {
    from("platform-http:/shipments/{orderId}?httpMethodRestrict=POST")
            .routeId("shipment-publish")
            .bean("shipmentPlanner", "plan")
            .split(body()).aggregationStrategy(new GroupedBodyAggregationStrategy())
                .setProperty("event", body())
                .setHeader(KafkaConstants.KEY, method("partitionKeyChooser", "keyFor"))
                .marshal().json()
                .to("kafka:" + Topics.SHIPPING_SHIPMENTS + "?synchronous=true&recordMetadata=true")
                .bean("receipts", "of")
            .end()
            .marshal().json()
            .setHeader(Exchange.CONTENT_TYPE, constant("application/json"));
}
```

shipping-service · ShipmentPublishRoute.java

> The route splits the plan into the three shipment events, sets the Kafka key header from a strategy bean, marshals each event to JSON and sends it with the Kafka producer, waiting for the broker's answer. The strategy is chosen by configuration, so switching from the order id to the event type changes one environment variable and no code. Waiting for the answer with synchronous true and recordMetadata true puts the partition and offset of every record in the response, which is how a run shows where each event landed. A route that sets the key from the order id needs no buffer at all.

## [019] RESEQUENCER · Resequencer

p. 19

- A Resequencer is a stateful filter that holds out-of-sequence messages and releases them in order (Hohpe and Woolf).
- Problem. When several producers or topics describe one order, no single key puts its events in one partition, so Kafka cannot keep them in sequence.
- Solution. Each event carries a sequence number. Replicas hold early events in a table and apply from the next expected number under a row lock.
- Result. With the resequencer on, the stalled-replica run ends DELIVERED instead of SHIPPED, in lifecycle order.
- Cost. One row per early event and one lock per event. A missing event holds that order's following events until someone repairs it.

> Keying by entity id is the first answer because it needs no buffer. A resequencer is for the cases where the key cannot change, and it pays with held rows and with events that wait. Every shipment event already carries its number within the order, and both order-service replicas share the database, so the database is where an order's events wait. One transactional method inserts the order's row if it is missing, locks it, ignores an event already applied or held, stores the event, and applies every event from the next expected number. An event is either held or applied, never both. If order only matters for the final state, a version number checked at the write can reject an older event with no buffer at all.

## [020] RESEQUENCER · Hold in the database, apply in order

p. 20

Events 2 and 3 wait in the pending table until event 1 arrives; then 1, 2 and 3 are applied and deleted

> Replica B receives events 2 and 3 first. It finds that number 1 is missing and stores both in the pending table, which has a unique constraint on order id and sequence. Nothing is applied. When replica A receives event 1, one transaction under the order's row lock applies 1, 2 and 3 in order and empties the table. Two replicas handling an order's first event cannot both insert its row, because the insert does nothing on a conflict, and the lock then serialises them, so no event is applied twice or out of turn. Events arriving as 3, 2, 1 are applied as 1, 2, 3, and a duplicate is ignored. A gap in one order does not hold another order's events, because the lock and the counter belong to one order.

## [021] BATCHING PUBLISHERS · Batching publishers

p. 21

- The producer batches records into one open batch per partition and sends every ready batch in one request, trading a little latency for fewer, larger requests.
- Problem. A flash sale of 20,000 orders costs the broker one request per record unless sends overlap and batches fill.
- Settings. Four settings matter: linger.ms (5 ms by default), batch.size (16384 bytes by default), compression, and acks. acks=all and idempotence are the defaults.
- Overlap. A caller that waits for each acknowledgement never fills a batch. Batches fill only when several sends are in the producer at once.
- Measured. The default route moved 4.9 times the unbatched throughput (median of five rounds, range 2.1 to 6.0). The tuned route (20 ms, 128 KiB, lz4) moved 0.4 times the unbatched throughput, because every record waits out the linger.

> Send does not write to the network. The producer appends the record to an open batch and returns, and a background thread sends batches that are full or have waited long enough. The linger default was 0 and is now 5 milliseconds, raised by KIP-1030. The numbers are ratios to the unbatched route in the same run, medians of five rounds. 200 callers each waited for an acknowledgement, so a longer linger only added latency. Compression is the part that pays, with lz4 cutting bytes per record to 0.38 of none and zstd to 0.19. Tune when records per request is close to one while sends are concurrent, or when bytes are the cost, and raise the linger only as far as the latency budget allows.

## [022] BATCHING PUBLISHERS · One open batch per partition

p. 22

Records from many callers fill one batch per partition; a request carries every ready batch

> Records from many callers go into one open batch for each partition. A batch is ready when it holds batch.size bytes or has waited linger.ms, whichever comes first, and one request to a broker carries the ready batches for every partition that broker leads. With three partitions on one broker, the unbatched control cannot send more than three records per request, while the default and tuned routes send many. A single caller that waits for each acknowledgement sends about one record per request, however long the linger. In Camel the settings are endpoint options on the Kafka component, so the route reads like the settings it applies. A Micrometer client factory binds the producer's own metrics, so records per request is a Prometheus query.

## [023] CONSUMER CONCURRENCY · Consumer concurrency

p. 23

- consumersCount starts that many consumer threads in one Camel route. Each is a group member, so the group divides the partitions among them.
- Problem. After an outage a backlog must drain, each record costs work, and the thread must not leave the group while it works.
- Ceiling. Nine partitions means nine useful consumers. The default RangeAssignor gave all nine to three members of a twelve-thread group.
- Measured. Drain time against one thread: 0.33 for three threads, and 0.12 for twelve under RoundRobinAssignor.
- Eviction. The time to process max.poll.records must stay inside max.poll.interval.ms, or the member is removed and its batch is delivered again.

> Three topics of three partitions give nine partitions, so nine is the most consumers that can read at once. Extra members are not an error, but they cost memory and a place in every rebalance and add no throughput. The assignor decides how even the split is: RangeAssignor deals each topic separately and starts from the first member every time, so three members got everything. Draining 3000 records at 5 milliseconds each, three threads took a third of the time of one, and RoundRobinAssignor with twelve threads took about an eighth. The same measurements show a fetch.min.bytes of 65536 cutting fetch requests from about 2,400 to 60 on a trickle, at the cost of up to 500 milliseconds of delay per record.

## [024] CONSUMER CONCURRENCY · Parallelism stops at the partition count

p. 24

Nine partitions assigned to the first nine of twelve consumers; the last three are idle

> Under RoundRobinAssignor the nine partitions are each assigned to one of the first nine consumers, and the last three own nothing. The partition count is the ceiling on how many members of one consumer group read at once, so beyond it a group needs more partitions. The classic rebalance protocol with an eager assignor makes every member give up all of its partitions and rejoin, and a replica that started a moment ago interrupts all the others: the first replica gave up all nine. With the consumer protocol from KIP-848 it gave up fewer than half, four or five in recorded runs. On the classic protocol, CooperativeStickyAssignor gave up four. When the work per record is the problem, make it cheaper or move it out of the poll loop.

## [025] SHARE GROUPS · Share groups

p. 25

- Competing consumers are several workers taking jobs from one channel, each job going to whichever worker is free. Kafka share groups implement it with per-record acknowledgement.
- Problem. A consumer group gives each partition to one worker, so one slow job holds back every job behind it, even when other workers are idle.
- Acknowledge. Accept, release or reject each record. A lock lasts 30 seconds by default, and a record is archived after 5 deliveries by default.
- Cost. No ordering within a partition. Delivery is at least once, so a job that must not run twice needs an idempotent receiver.
- Camel. The Camel Kafka component has no share-group option, so the worker is a SmallRye Reactive Messaging method.

> The notification service sends an email for every order placed. One customer's mailbox takes eight seconds, and twelve more jobs sit behind it in the same partition. A share group lets any worker acquire any record, so the slow job holds only the worker that took it. Use a consumer group when order per key matters, and a share group when jobs are independent. The share side costs time on every job, because a worker answers each job before it fetches the next: a partition of eight to ten fast jobs took about 2 to 7 seconds through the share group and under 1 second through the consumer group. The partition with the slow job finished in about the same time either way, but most of its jobs no longer waited.

## [026] SHARE GROUPS · Who waits behind one slow job

p. 26

In a consumer group the jobs behind a slow one wait; in a share group another worker takes them

> In a consumer group, worker-1 owns the partition and delivers its records one after another, so the jobs behind the slow one wait while other workers are idle. Adding workers beyond the partition count does not help, because the extra ones own nothing. In a share group, worker-1 acquires only the slow job, and another worker takes the jobs behind it in the same partition, one at a time. A burst written as one producer batch is acquired by one worker as a whole, so the jobs behind a slow one wait for it on that worker. Setting max.poll.records to one and a small fetch size keeps each fetch short, and a one-second retry cap brings a worker back within about a second of its last acknowledgement.

## [027] SHARE GROUPS · The life of one record

p. 27

Available, acquired and locked, then accepted, released, rejected or released by lock expiry

> A record starts available. A worker acquires it, the broker locks it for that worker and raises its delivery count. The worker then answers with accept, which ends the job, release, which returns it to the group with a higher delivery count, or reject, which archives it. In SmallRye an ack always accepts, and release or reject travel on a nack. If the worker gives no answer before the lock duration ends, the lock expires and the record becomes available again. A worker that is only slow keeps its lock, because the Quarkus connector renews the lock of a job still in progress, and a worker that is stalled loses it. Kafka states no ordering guarantee for share groups, and the delivery count is approximate.

## [028] SECTION · Getting events out reliably

The database and the broker share no transaction

> A service that changes its own state and announces the change writes to two systems that cannot commit together, and every failure between the two writes leaves them disagreeing. The transactional outbox and log tailing carry changes out of PostgreSQL through its write-ahead log. Offset management decides where the reader resumes. The idempotent consumer, Kafka transactions and the dead letter channel decide what a receiver does with a duplicate, a batch that crashes midway and a record that can never be processed.

## [029] TRANSACTIONAL OUTBOX · Transactional outbox

p. 29

- A transactional outbox stores the event as a row in the same database transaction as the state change, and a separate relay publishes the row to the broker.
- Problem. PostgreSQL and Kafka share no transaction. A crash between save and send leaves an order with no event; reversing the writes leaves an event with no order.
- Relay. Debezium reads the outbox insert from the write-ahead log, and its EventRouter publishes the record keyed by the aggregate id.
- Guarantee. At least once from the database to the log. The row id travels as a header so a consumer can drop duplicates.
- Measured. With Kafka stopped, the outbox answers 201 in about 10 ms. The commit-first route returns 500 after 60 seconds.

> order-service stores an order and publishes OrderPlaced. The obvious code saves the row and sends the record, and it works until the process dies between the two lines. Retrying does not help when the process is gone, and Kafka transactions cannot enlist a database, so the atomic unit has to be something the database alone can commit. Chris Richardson names the parts: the sender writes the change and the message in one transaction, the message sits in an outbox table, and a relay sends it. The event exists if and only if the order does. A polling relay queries the table on a schedule. A log tailer reads the inserts from the transaction log, adds no query load and needs no published flag.

## [030] TRANSACTIONAL OUTBOX · The two orders of a dual write

p. 30

Commit then publish loses the event on a crash; publish then commit announces an order that never committed

> Commit first and a crash between the two lines leaves an order that exists with no event, so inventory never reserves the stock. Publish first and a failed commit leaves an event announcing an order that does not exist, and a published record cannot be taken back. The comparison code is two Camel routes, one per ordering, with a crash switch that halts the JVM right after the commit, which is what a power cut or an out-of-memory kill looks like to the code. The outbox path runs the same crash and loses nothing.

## [031] TRANSACTIONAL OUTBOX · The log carries the event out

p. 31

OrderService writes the order and the outbox row in one transaction; Debezium reads the insert and routes it to edp.orders.placed

> The write side is the Debezium Outbox extension for Quarkus. Firing an exported event as a CDI event inside a transaction inserts one row into the outbox table, with the aggregate id that becomes the Kafka key, the event type, the payload and a topic column. Flyway owns the table, and remove-after-insert deletes the row in the same transaction, because the insert in the log is all the connector needs. The read side is configuration, not code: the connector captures only the outbox table and the EventRouter publishes each row to the topic named in its topic column, with the payload as a JSON object and the row id as a header. If the connector or Connect is down, orders placed meanwhile arrive once it returns.

## [032] TRANSACTIONAL OUTBOX · One transaction holds both writes

p. 32

[chip] JAVA

```java
/** Stores the order and the event that announces it. They commit together or not at all. */
@Transactional
public void place(Order order) {
    save(order);
    events.fire(OrderPlacedEvent.of(OrderPlaced.of(order), json));
}
```

order-service · OrderService.java

> The method is transactional. It saves the order and fires the event as a CDI event, and the Debezium Outbox extension turns the fired event into an insert on the outbox table inside the same transaction. A duplicate order id fails at commit with a unique violation, the whole transaction rolls back, and the refused request leaves no order row, no outbox row and no event. Nothing in the method talks to Kafka, so the request never waits for the broker. An outbox costs a table, a connector and a replication slot to operate, and a slot that nobody reads keeps the write-ahead log on disk, so a stopped connector is an alarm and not a harmless state.

## [033] LOG TAILING · Transaction log tailing

p. 33

- Transaction log tailing reads the database's own transaction log and publishes each committed change as an event, without the application taking part.
- Problem. A service needs changes to data it does not own and cannot modify. Polling misses deletes and intermediate updates, and triggers add work to the legacy transactions.
- Mechanism. Logical decoding turns the write-ahead log into row changes. A publication names the tables. A replication slot remembers the last confirmed position.
- Cost. Events describe rows, not business facts, so consumers are coupled to column names the table's owner can change.
- Risk. A slot that stops advancing makes PostgreSQL keep its log until the disk fills.

> The warehouse system keeps its stock in a table another team owns. Nothing in it publishes anything, and a job that selects rows changed since the last run never sees a delete and sees only the last of several updates between two runs. PostgreSQL writes every change to its write-ahead log before the table, to recover after a crash. Debezium is the client that reads the slot and turns each change into an envelope with an operation, the row before and after, and the log position. It starts with a snapshot, published as r events, and then streams. Every slot needs an owner who watches it, a heartbeat for a quiet table in a busy database, and a decision for when the reader is gone for good.

## [034] LOG TAILING · The log holds every change, the slot holds a place

p. 34

Writes land in the write-ahead log; the publication filters three tables; the slot records how far the connector has confirmed

> Writes to the captured table, to a busy table the connector does not capture, and to the heartbeat table all land in the write-ahead log. The publication filters three tables for the connector, and the replication slot records how far it has confirmed. When the captured table is quiet and another table is busy, the log grows with changes the connector never receives, the slot stays where it was, and so does the log behind it. A heartbeat action query that writes a table in the publication brings a change back through the slot, so the connector confirms a position and the retained log falls. Inside the service, Camel reads the connector's change topic, maps the envelope to a domain document and publishes it keyed by SKU, with one tombstone for a delete.

## [035] CONNECTOR OFFSETS · Offsets and replication slots

p. 35

- Connector offsets are a source connector's position, stored by Kafka Connect in an offsets topic. The offsets API lists them at any time. Altering or resetting them needs the connector stopped.
- Problem. After a bad batch or a day of maintenance, where does the connector start reading, and who decides?
- The slot wins. PostgreSQL streams from the greater of the offset and the slot's confirmed position. Altering forward skips changes. Altering backward does not rewind.
- Reset. With snapshot.mode=initial a reset reads the current rows as snapshot events, not the history of how they got there.
- Lost slot. The connector stays RUNNING with no task, and the retry loop lasts about 30 minutes. Recover with stop, drop the slot, delete the offsets, resume.

> A source connector has no consumer group. Connect writes its position on a timer, 60 seconds by default, and a Connect process that dies between flushes restarts from the last stored position, so a consumer of the topic has to tolerate duplicates anyway. Alter is the tool for skipping a bad batch, and a reset is the tool for rebuilding the downstream copy from the table as it is now. Neither can rewind. While a connector is stopped, PostgreSQL keeps every log file from the slot's position onward, including log written by tables the connector does not capture. Setting max_slot_wal_keep_size caps the retention, and its price is a lost slot, so alert on the slot's status and the retained bytes rather than on failed tasks.

## [036] CONNECTOR OFFSETS · The server streams from the greater position

p. 36

An offset ahead of the slot skips the changes between; an offset behind the slot is ignored

> The offset is where the connector asks to start, and the slot is where the server is willing to start. When a client starts streaming from a slot, PostgreSQL begins at the requested position or at the slot's confirmed position, whichever is greater. An offset altered ahead of the slot makes streaming start at the offset, and everything written in between is never published. An offset altered behind the slot is clamped to the slot, so nothing before the confirmed position can be asked for again. It is not free of duplicates, though: the altered offset holds only a log position, so the last transaction delivered before the stop arrives a second time. Do not alter an offset to repair one consumer, because the change goes into the connector for every reader of the topic.

## [037] IDEMPOTENT CONSUMER · Idempotent consumer and inbox

p. 37

- An idempotent consumer records the id of every event it has applied in the same transaction as the effect, and skips an event whose id is already recorded.
- Problem. Kafka commits the position apart from the consumer's writes. Acknowledge first and a crash loses the event; process first and it repeats. Subtracting stock is not idempotent.
- Solution. An inbox table. The insert uses ON CONFLICT DO NOTHING, so zero rows inserted means a duplicate. The id comes from the event, not the transport.
- Order. The transaction commits before the acknowledgement, so a crash between them is harmless.
- Cost. A table, an insert per event and a cleanup job. An effect in another system cannot join the transaction.

> inventory-service reserves stock for each OrderPlaced. The outbox publishes at least once, so a restarted connector can put the same event on the topic twice, and the consumer can commit the stock and die before it tells Kafka. Remembering ids in memory fails at the restart that causes the duplicate, and remembering them in another store has the dual-write problem on the receiving side. The id comes from the outbox row id that travels in a record header, so it survives a relay that republishes. If the decrement throws, the transaction rolls back and the inbox row goes with it, so an id is recorded only for an effect that happened. When the effect is an upsert keyed by the order id, natural idempotence needs no table.

## [038] IDEMPOTENT CONSUMER · One transaction holds the id and the effect

p. 38

First delivery: inbox insert and decrement commit before the acknowledgement. Second delivery: insert returns zero rows

> On the first delivery, the insert into the inbox returns one row, the stock is decremented, and both commit before the acknowledgement. If the JVM halts between the commit and the acknowledgement, the record is delivered again after the restart. The insert then returns zero rows, nothing is decremented, and the record is acknowledged. The replacement consumer has to wait until the broker expires the dead member's session, which is 45 seconds by default, so the channel sets a 10 second session timeout to keep a run short. The same pattern is available as Camel's Idempotent Consumer EIP with a JPA repository, but only inside a transacted route, where the key row and the decrement are again one commit.

## [039] IDEMPOTENT CONSUMER · Insert the id, then apply the effect

p. 39

[chip] JAVA

```java
/**
 * Applies the event once. The inbox row and the decrement are one transaction: a second delivery of the
 * same event id inserts nothing and returns false, and a failed decrement leaves no inbox row.
 */
@Transactional
public boolean reserveOnce(UUID eventId, OrderPlaced event) {
    int inserted = em.createNativeQuery(
            "INSERT INTO inventory.inbox (event_id) VALUES (:id) ON CONFLICT (event_id) DO NOTHING")
            .setParameter("id", eventId)
            .executeUpdate();
    if (inserted == 0) {
        return false;
    }
    reserve(event);
    return true;
}
```

inventory-service · ReservationService.java

> One transactional method answers the question of whether this is the first time. The native insert reports how many rows it wrote, and zero means the event id is already in the inbox, so the method returns false and the caller counts a duplicate. Otherwise the decrement runs in the same transaction. If reserve throws because the SKU is unknown or the stock would go negative, the transaction rolls back and the inbox row goes with it. A plain insert that waits for a unique violation would do the same job by failing the whole transaction, which turns every duplicate into an error. The decrement is one database update, not a value read beforehand.

## [040] IDEMPOTENT CONSUMER · Where the idempotency key lives

p. 40

Approach | Commits with the effect | Use it when

Inbox table | Yes, one transaction | The effect is a database write in a handler method

Camel JPA repository, transacted route | Yes, joins the JTA transaction | The flow is a Camel route and the effect is a database write

Camel JPA repository, not transacted | No, the key commits first | Never for a database effect

Camel JDBC repository under JTA | Unusable: fails on every message | Outside JTA only

Camel Kafka repository | No, never | The effect is outside any database transaction and a rare duplicate is tolerable

Natural idempotence | Not needed | The effect is an upsert or a state set keyed by the order id

> A key stored apart from the effect leaves a gap. Take the transaction off a Camel route that uses the JPA repository and the key commits first. If the reservation then fails, the key is stored and the effect is not, so the redelivered record is skipped and the reservation is lost. The same gap exists for any key store that cannot commit with the effect: a key in a Kafka topic or a cache can never share a transaction with a database. The inbox pays a table and an insert per event, and it protects only effects that can join a database transaction. A consumer that only reads from Kafka and writes to Kafka belongs to Kafka's own transactions.

## [041] EXACTLY-ONCE · Exactly-once read-process-write

p. 41

- A transactional client groups its reads and writes into one transaction, so they all take effect or none of them do.
- Problem. The output record and the consumer position live in different places. Commit the position first and the payment is never written. Write first and a restart writes a second payment.
- Rules. One transactional.id belongs to one producer, and a newer epoch fences the old one. A read_committed reader stops at the first open transaction. Aborted records stay in the log.
- Quarkus. KafkaTransactions sends the outputs and the batch's offsets in one transaction. The channel commits nothing itself.
- Scope. Kafka to Kafka only. A database cannot enlist, and the transaction costs throughput.

> payment-service reads InventoryReserved and writes one PaymentProcessed for each. The producer's idempotence setting removes duplicates caused by its own retries, but a restarted process is a new producer with no memory of what the old one wrote. A producer with a transactional id writes the outputs and sends the input offsets to the same transaction, and the commit applies both. A read_committed consumer receives records in offset order and stops at the first record of an open transaction, so a producer that stalls inside one stalls every reader behind it until the coordinator's timeout aborts it. A record that always fails the batch also stalls the loop, because the batch aborts and is read again. Keep the bad record out before the transaction.

## [042] EXACTLY-ONCE · Output records and input offsets together

p. 42

PaymentProcessor reads a batch; the outputs and the consumer offsets commit in one Kafka transaction

> PaymentProcessor reads a batch from edp.inventory.reserved. The output records and the consumer offsets are written inside one Kafka transaction, and a commit marker on both partitions makes them take effect together. If the process dies in the middle of a burst, the transaction aborts, the consumer position goes back to the last committed offset, and the restart produces every output exactly once for a reader that uses read_committed. The default isolation level, read_uncommitted, still returns the aborted copies. Chaining a Hibernate transaction with a Kafka transaction commits the database first and the Kafka transaction after it, which orders two commits and does not make them atomic. For a database on either side, use the outbox on the way out and the inbox on the way in.

## [043] EXACTLY-ONCE · Batch and offsets in one transaction

p. 43

[chip] JAVA

```java
@Incoming("inventory-reserved")
public Uni<Void> process(Message<ConsumerRecords<String, InventoryReserved>> batch) {
    return transactions.withTransactionAndAck(batch, tx -> {
        for (ConsumerRecord<String, InventoryReserved> record : batch.getPayload()) {
            if (crash.armedFor(record.key())) {
                return crash.halt();
            }
            tx.send(KafkaRecord.of(record.key(), PaymentProcessed.of(record.value())));
        }
        return Uni.createFrom().voidItem();
    });
}
```

payment-service · PaymentProcessor.java

> The method takes a whole batch per poll and hands it to withTransactionAndAck. That call begins the transaction, runs the function, adds the batch's consumer offsets to the transaction, commits and acknowledges the batch. Every output goes out through the transaction. If the function fails, the transaction aborts and the position returns to the last committed offset. The crash switch in the middle of the loop is demonstration code that halts the JVM for one order id; it is not part of the pattern. Batch mode is the recommended form, because the Quarkus guide warns of a significant performance cost when each record is its own transaction. Each running instance needs its own transactional id.

## [044] DEAD LETTER CHANNEL · Failure classes and the dead letter topic

p. 44

- A dead letter channel sends a record the receiver cannot process to a separate topic with its original content, after retrying only the failures that can still succeed.
- Problem. Stopping holds the partition. Retrying helps only if the next attempt can succeed. The unsound option logs the failure and commits the offset.
- Classify. A truncated JSON document never parses, and a quantity of 0 never becomes valid. A locked row is free again in a moment.
- Keep. The original key and bytes. SmallRye adds headers for the exception, the reason, and the source topic, partition and offset.
- Cost. A retry in place holds the partition, and the dead letter topic needs an owner.

> Six records arrive on one partition. One is cut off in the middle of its JSON, one is not JSON at all, one asks for a quantity of 0, one is a good order for a SKU whose row another transaction holds, and two are ordinary. Each needs a different answer. Retrying the first three wastes time while the partition waits, and dead-lettering the locked row throws away an order that would have succeeded. With nothing configured, the SmallRye channel stops at a bad record and reports readiness DOWN, and the default Camel error handler logs the record, commits and carries on, so it is kept nowhere. Skipping a record is a decision about ordering: when one record depends on the record before it, stopping the channel is correct.

## [045] DEAD LETTER CHANNEL · Retry what can succeed, dead-letter the rest

p. 45

An invalid record is tried once and dead-lettered; a locked row is retried; a record still failing after the last retry is dead-lettered

> Failures sort by a single question: can trying again change the outcome. An unparseable or invalid record is tried once and goes to the dead letter topic. A record that fails on a locked row is retried after a delay, and succeeds once the lock is released. A record still failing after the last retry goes to the dead letter topic as well. A lock timeout of two seconds on the row turns the wait into an ordinary failure the retry policy acts on. Without it, the attempt waits until the holder releases the row and succeeds on its first and only attempt, so the policy never runs. A retry in place suits failures that clear in seconds; a dependency that is down for minutes holds the partition for as long.

## [046] DEAD LETTER CHANNEL · Redeliver or dead-letter

p. 46

[chip] JAVA

{% raw %}
```java
@Override
public void configure() {
    if (deadLetterChannel) {
        errorHandler(deadLetterChannel("kafka:" + Topics.ORDERS_PLACED_DLQ
                + "?valueSerializer=" + ByteArraySerializer.class.getName())
                .useOriginalBody().maximumRedeliveries(retryMax).redeliveryDelay(1000));
        // Without useOriginalBody here the dead letter would carry the parsed OrderPlaced and fail to serialize.
        onException(JsonProcessingException.class, IllegalArgumentException.class)
                .maximumRedeliveries(0).useOriginalBody();
    }

    from("kafka:" + Topics.ORDERS_PLACED + "?groupId=inventory-camel&autoOffsetReset=earliest"
            + "&sessionTimeoutMs=10000&maxPollIntervalMs={{max-poll-interval-ms}}"
            + "&breakOnFirstError={{dead-letter.camel.break-on-first-error}}"
            + "&valueDeserializer=" + ByteArrayDeserializer.class.getName())
            .routeId("dead-letter")
            .autoStartup("{{dead-letter.camel.enabled}}")
            .unmarshal().json(JsonLibrary.Jackson, OrderPlaced.class)
            .bean(ReservationService.class, "reserve");
```
{% endraw %}

inventory-service · DeadLetterRoute.java

> The Camel route says the same things in its own terms. The error handler redelivers every failure up to the limit, one second apart, and then writes the original body to the dead letter topic. The onException clause overrides it for the two exceptions that cannot succeed, with no redelivery. useOriginalBody has to be on the clause as well as on the error handler: without it the dead letter channel is handed the unmarshalled object, cannot serialize it as bytes, and the record is lost. What Camel does not do is describe the failure: it writes the original value and no reason, source topic, partition or offset. The SmallRye channel writes those as headers, and reads bytes so the dead letter carries what arrived.

## [047] SECTION · Event sourcing

An order is the stream of its own events

> An event-sourced service keeps no table of current state. The state of an order is the fold of its events, and the only way to change it is to append another one. The parts are the aggregate that decides each command, what happens when two commands race, how a snapshot keeps a long stream cheap to load, how a read model answers the questions a stream cannot, when to replay and when to project, and how old events stay readable after the schema changes.

## [048] EVENT SOURCING · Aggregate: one object decides

p. 48

- An aggregate is one object, reached through its root, that rebuilds its state from its own event stream, decides each command and appends the result at the version it loaded.
- Problem. A cancellation and a shipment that both read PAID are each correct against what they saw, and together leave CANCELLED after SHIPPED.
- Rebuilt per command. No stored copy of the state exists, so none can be stale.
- Decide, then record. A refused command returns no events and writes nothing.
- Version check. The append names the version it was decided against, and a unique (stream_id, version) constraint rejects a stale one.

> Three callers change an order: a REST request cancels it, a payment record marks it paid, and a shipment record ships it. If each one reads the status, checks the move and writes the result, the rules live in three places and drift apart. Two callers acting on the same status at the same moment both pass their own check. The aggregate removes that by being the only place a rule lives. It is rebuilt from the stream for every command, so there is nothing cached to go stale. It decides before it records, and the append carries the version it was decided against, which lets the store refuse it when the stream has moved on. The order service keeps no order table at all, and every status is derived from the stream.

## [049] EVENT SOURCING · One command, one transaction

p. 49

order-service · command cycle: load, rebuild, decide, append, export

> A command arrives and the service loads the stream of that order in version order. The aggregate folds the stored events into a status, an amount and a payment id, then decides the command against that state. The result is a list of events, or a refusal that writes nothing. The events are appended at the loaded version plus one inside the same transaction. Only two of the events are public: OrderPlaced and OrderCancelled each get an outbox row in that transaction, and Debezium carries them to their topics. The other events stay private to the store, so the table's schema is never the contract with other services. If the command fails after the append, the event row and the outbox row roll back together.

## [050] EVENT SOURCING · The append is the concurrency check

p. 50

[chip] JAVA

```java
/** Appends the events as versions expectedVersion + 1 onwards, or throws {@link ConcurrencyConflict}. */
public void append(String streamType, String streamId, int expectedVersion, List<OrderEvent> events) {
    int version = expectedVersion;
    for (OrderEvent event : events) {
        StoredEvent row = new StoredEvent();
        row.eventId = UUID.randomUUID();
        row.streamType = streamType;
        row.streamId = streamId;
        row.version = ++version;
        row.type = event.type();
        row.payload = event;
        row.occurredAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        try {
            persistAndFlush(row);
        } catch (PersistenceException e) {
            if (violatesVersionConstraint(e)) {
                throw new ConcurrencyConflict(streamId, expectedVersion, e);
            }
            throw e;
        }
    }
}
```

order-service · EventStore.java

> The store has two operations, load and append. Append writes each event at the expected version plus one through Panache. The unique constraint on stream id and version is the whole concurrency check: when two commands loaded the same version, one insert succeeds and the other violates the constraint. Hibernate reports that as a constraint violation inside a persistence exception, and the store turns the one for the version constraint into a ConcurrencyConflict, while any other failure passes through unchanged. Greg Young describes the same two operations with an optimistic check against an expected version; keeping the version in the constraint means no second table is needed. Without the constraint nothing detects the race and a history can stop being a legal lifecycle.

## [051] EVENT SOURCING · A lost race: decide again or report

p. 51

- Rule. Retry what does not depend on what the caller saw, and report what does.
- No precondition. A cancellation means "cancel if the rules allow", so the server reloads the stream and decides again, up to three times with SmallRye Fault Tolerance.
- With If-Match. A client that decided from version 2 gets 412 Precondition Failed when the stream has moved, and no command runs.
- Never blind. Appending the decided events at the new head puts CANCELLED after SHIPPED into the history.
- A refusal is an outcome. 409 over REST, logged and acknowledged on a Kafka channel.

> When two commands load the same version, one append wins and the other fails with a conflict, and the loser's transaction writes nothing. What happens next depends on who made the decision. A cancellation sent with no precondition does not depend on the state the server read, so the server loads the stream again and decides again: if a shipment won, the second decision is a refusal, and if a payment won, the cancellation is accepted at the next version. A client that cancelled because of what it saw has not asked for a cancellation at whatever version exists by the time it runs, so it sends its ETag as If-Match and a stale tag is answered with 412. The retry wraps a call to another bean, so every attempt is a new transaction.

## [052] EVENT SOURCING · Decide again, never append blind

p. 52

order-service · a cancellation at version 2 loses to a shipment at version 3

> A cancellation was decided at version 2, and a shipment was appended as version 3 in the meantime. The cancellation's append fails on the version constraint. Decided again from version 3, the cancellation meets a shipped order and is refused with 409, which is a valid outcome. Appended blind at version 4, the same events land after the shipment and the history records an order that shipped and then was cancelled, which the lifecycle forbids. The same service can run the retry in a Camel route instead of a SmallRye channel: a redelivery policy on the conflict exception, with the refusal handled and acknowledged. Both run the same transactional bean, so the stream and the aggregate behave identically.

## [053] EVENT SOURCING · Snapshot: replay only the tail

p. 53

- A snapshot stores the aggregate's state at one version, so a load reads it and replays only the events after it.
- Problem. A load costs the length of the stream. SKU-1503 holds 100,000 events and every reservation reads all of them.
- A cache, not a record. It can be deleted at any time and the events rebuild it. Only the newest is kept, and the aggregate's own code writes it.
- Measured. Streams of 1,100, 10,100 and 100,200 events load in 1.6, 11.6 and 127.7 ms in full, and in 0.50, 0.63 and 0.95 ms with a snapshot.
- Skip it for short streams: 1,100 events load in 1.6 ms without one.

> Most streams stay short, but stock, an account or a counter takes an event for every change, and the oldest events still count in the answer. The obvious fixes each give something up: deleting old events destroys the history, a second table of current quantity is a source of truth nothing marks as derived, and an in-memory cache is lost on restart and does not help a second instance. A snapshot keeps the stream as the record and only controls how many events a load reads. The timings are medians of three runs, each in a new JVM, taken inside the service with 99 events after the snapshot, the longest tail the policy allows. The full load is linear in the stream, between 1.2 and 1.4 microseconds per event.

## [054] EVENT SOURCING · Snapshot plus tail

p. 54

inventory-service · a load reads one snapshot row and the events after it

> A load reads the newest snapshot, if there is one, and then the events after its version. The aggregate is built from the snapshot plus that tail, so the work is bounded by the snapshot interval and not by the stream. The snapshot is written in the same transaction as a command, every N events, with a single upsert whose guard stops an older snapshot from moving the stored version backwards. A smaller N bounds the tail more tightly and writes more snapshots: at N of 10 a load replays at most 9 events and 1,000 reservations write 100 snapshots, while at N of 1,000 a load replays at most 999 events and 1,000 reservations write one. A route on a timer can write snapshots out of band, but then the tail is not bounded.

## [055] EVENT SOURCING · CQRS: a read model beside the stream

p. 55

- CQRS keeps a read model apart from the write model and feeds it from the write model's events, so each side has the shape its questions need.
- Problem. The aggregate answers whether a command may happen. A customer's order list needs facts from three services, for many orders, and no index by customer.
- Three rules. Each column is written by the one event that owns it, each update only moves forward and is safe twice, and each answer says how current it is.
- Cost. The view is eventually consistent, so a customer may list orders and not yet see the one just placed.

> The write model is shaped for one job: load one stream, decide, append. A customer's question is another job. The events table has no index by customer, its payloads are the owner's private format, and it holds none of the payment or shipping facts. Reading another service's store couples every reader to its schema, and asking each service per order makes the list as slow as the slowest of three. So the order service builds a table of its own from the topics the other services already publish. It is disposable, because it can be rebuilt from the events, and no request writes to it. Fowler cautions that for most systems the pattern adds risky complexity, so it belongs on a bounded context and not on a whole system.

## [056] EVENT SOURCING · Commands in, a view out

p. 56

order-service · order_view fed by six topics

> The write side decides commands against streams. The read side is one row per order in a table, with a read-only Panache entity in front of it, answered without touching Kafka. Writing is the projector's job alone: one upsert per event that sets only the columns that event owns, so a payment never touches the shipment. The projector acknowledges a record after its transaction commits, and a crash in between replays the record, which the upsert tolerates, so the view needs no inbox table. Every answer carries asOf, the timestamp of the last event applied, and a timer exports the lag from record timestamp to acknowledgement. A burst of 250 orders, 1,250 records, was applied with a 95th percentile lag of 318 to 334 ms; with each event delayed by 50 ms it was 12,881 ms.

## [057] EVENT SOURCING · Six topics, six partitions, no order

p. 57

order-service · the shipment upsert refuses an event that does not advance the row

> Kafka orders records within one partition, and the view reads six partitions on six topics, so nothing orders ShipmentInTransit before ShipmentDelivered. A last-writer-wins upsert turns that into a wrong answer: an order delivered on Tuesday shows as in transit again. The shipment upsert refuses it in SQL. A small immutable function numbers the lifecycle, and the WHERE clause makes the statement a no-op for an event that does not advance the row, which also makes a redelivered record harmless. The check lives in the statement so that the check and the write are one step, and no other channel can write the row between them. The guard works because the facts are a lifecycle, where further is defined. For a column with no order, such as an address changed twice, the event needs a version from its owner.

## [058] EVENT SOURCING · Replay or project

p. 58

- A projection is a read model built by folding the event history in order, kept current by applying each new event once, and rebuilt from the first event when its logic or question changes.
- Problem. Shipments per carrier per day touch every stream, and nothing indexes them.
- Replay. Fold every aggregate when the question arrives. Correct by construction and no new moving part, but it pays the size of the store on every request.
- Projection. A table, a checkpoint and a timer. Fast to read, one tick behind, and wrong until rebuilt if its fold has a bug.

> Operations asks how many orders each carrier shipped each day. The order service has no table to ask, and a snapshot bounds one aggregate's load without helping a question about all of them. Replay answers it with the same fold the commands use. A projection answers it from a small table kept current by a checkpointed poller. The events remain the only record in both cases, so losing a projection costs time and never data. The Azure Architecture Center calls a materialized view completely disposable for that reason. A projection is worth its extra model only when the question is asked often enough, and a replay is slow enough, to justify a second thing that can be wrong.

## [059] EVENT SOURCING · Same question, two paths

p. 59

order-service · ReplayReport and CarrierDailyProjector

> Both paths return the same lines: a carrier, a UTC day and a count, and a SQL query over the raw events is the oracle for both. The checkpoint matters. Reading everything after the last position seen loses rows without a trace, because a position is assigned at insert and a commit happens after it: a transaction that holds position 41 open while 42 commits would be skipped forever. The batch query therefore reads in transaction order and only under the lowest transaction id still running, so no row can appear behind the checkpoint. The price is that one long transaction holds the projection back, and the report shows the lag in events while it does.

## [060] EVENT SOURCING · What each answer costs

p. 60

 | Replay every aggregate | Projection

Read at 1,000 / 10,000 / 100,000 orders | 15 / 77 / 586 ms | 0.78 / 0.61 / 0.46 ms

Freshness | The committed state at the read | One scheduler tick, about 1 s

A new question | Code only, exact at once | Code plus a rebuild, 1.8 s at 100,000

Storage | None | 27 rows at every size

What goes wrong | Read cost grows with the store | Lag, a bug repeated until rebuilt

> Both paths were timed inside the service on a store loaded to 1,000, 10,000 and 100,000 orders, which is 3,600, 36,000 and 360,000 events, using medians of three runs from fresh containers. The replay grows with the store, at about 1.6 microseconds per event at the largest size, while the projection read scans the same 27 rows at every size and is about 1,300 times faster there. At 1,000 orders the gap is 14 ms and replay is the better choice, because there is nothing to keep correct. Freshness is the projection's price: a run of 50 commands at the one-second default reached the projection with a median of 994 ms. A question that must see the command it just committed belongs to the aggregate.

## [061] EVENT SOURCING · Upcaster: old events, current shape

p. 61

- An upcaster turns an event stored in an older shape into the same event in the current shape, on a copy, when the event is read.
- Problem. The store is append-only, so a new field such as shippingPriority has no place to be fixed in the old rows.
- Chain. One small step per schema version, so a version 1 row passes through every step to the current one. An unknown version is an error.
- On the wire. An Avro registry with BACKWARD_TRANSITIVE refuses a version that a reader built on it could not use on every record ever published.

> Customers can now ask for express delivery, so OrderPlaced gains a shipping priority. Two kinds of reader already hold the old shape: the aggregate loads rows written before the field existed and can never rewrite them, and a consumer replaying the topic from its first record meets records without the field. Rewriting the old rows breaks the rule that made the store trustworthy. Teaching the aggregate every shape spreads the history of the schema through code that should know only the order. An upcaster keeps the stored history as recorded, and a bug in a step is fixed in code with nothing to repair. The default a step supplies is a business decision, so it is written once and the published contract states the same value.

## [062] EVENT SOURCING · Upcast on read, never on write

p. 62

order-service · stored rows pass through the chain before they become records

> A row is loaded with the schema version it was written in. The store copies the payload, applies the steps between that version and the current one, and only then maps the node to the current record, so the aggregate never sees an old shape. Appends always write the current version, which means a stream can hold both shapes for as long as it lives. The step is plain Java in the store, and no Camel type appears in it: Camel belongs to the wire, where the registry checks each new schema version against the older ones. When the chain is skipped, the current record refuses the missing field and the failure names the cause. An upcaster cannot invent a fact: when a change alters what an event means, a new event type is the answer.

## [063] EVENT SOURCING · The version 1 to 2 step

p. 63

[chip] JAVA

```java
/** The schema_version this release writes. */
public static final short CURRENT = 2;

/** Must equal the default of shippingPriority in order-placed.avsc, so both contracts say the same thing. */
public static final String DEFAULT_PRIORITY = "STANDARD";

private static final Map<String, Map<Integer, UnaryOperator<ObjectNode>>> CHAIN = Map.of(
        "OrderPlaced", Map.of(1, node -> node.put("shippingPriority", DEFAULT_PRIORITY)));
```

order-service · Upcasters.java

> The chain is a map from event type and schema version to a transform of the JSON node. For OrderPlaced stored at version 1, the step adds shippingPriority with the value STANDARD, the priority an order had before customers could choose. That constant must equal the default in the Avro contract, so the stored history and the published schema say the same thing. The current version is a constant of the release, and every load applies the steps that lie between the stored version and that constant. The cost is a copy of the payload on each load, and a chain that grows a step per release means every load runs every step. In exchange nothing is ever migrated, and a mistake in a step is repaired by changing code.

## [064] SECTION · Processing and coordinating streams

Enrich, join, window, coordinate and decide

> Once events flow, services need more than delivery. They need data the sender never carried, a correlation between two streams, a result for a slot of time, a way to undo a business transaction that spans databases, and a decision that stays repeatable when a model reads free text. For most of them, a Camel form and a Kafka Streams or workflow form run on the same input.

## [065] STREAMS · Content Enricher

p. 65

- A Content Enricher uses data in the incoming message to fetch the missing details from an external source and attaches them to the message.
- Problem. shipping-service publishes the carrier and tracking number, not the carrier's tracking page. Copying the page into every event freezes an old value into each one.
- Lookup. Ask the owner for each record. Always the current row, but the consumer depends on the owner being up.
- Table join. Keep a copy fed by the owner's events. Works while the owner is down, but only as fresh as its last published change.

> Hohpe and Woolf define the enricher by its source being external to the message. Whether that source is a call or a local copy is a choice the pattern leaves open, and it decides how the enrichment behaves when the world changes. notification-service builds the message the customer reads, so it needs carrier mode and a tracking link without owning that reference data. Putting the page into every event would repeat reference data in every record. Making the receiver keep its own data would give two services one truth to maintain. So the same enrichment is written twice, as a Camel route that calls the owner and as a Kafka Streams topology that joins against a compacted topic.

## [066] STREAMS · Lookup against stream-table join

p. 66

notification-service · one enrichment, two forms

> The lookup form calls shipping-service for each shipment and merges the answer into the message. A failed call is retried three times and then the exchange fails; with break on first error the consumer retries the same record, so the offset stays on it, nothing is lost and the records behind it wait. The join form never calls out. A compacted topic holds one record per carrier, and every instance reads the whole topic into a local store. When the owner is down the join carries on from its copy, with data as old as the last record it read. After a reference changes, a shipment scheduled in between gets the new link from the lookup and the old one from the join, and the two agree once the record is applied, which took 7 to 13 ms once it was on the topic.

## [067] STREAMS · Enrich by calling the owner

p. 67

[chip] JAVA

{% raw %}
```java
@Override
public void configure() {
    errorHandler(defaultErrorHandler().maximumRedeliveries(3).redeliveryDelay(1000));

    from("kafka:" + Topics.SHIPPING_SCHEDULED + "?groupId=notification-enricher-camel&sessionTimeoutMs=10000"
            + "&breakOnFirstError={{enricher.break-on-first-error}}")
            .routeId("shipment-enricher")
            .unmarshal().json(JsonLibrary.Jackson, ShipmentScheduled.class)
            .setHeader(Exchange.HTTP_METHOD, constant("GET"))
            .enrich().simple("{{shipping-service.url}}/carriers/${body.shipment.carrier}")
                    .aggregationStrategy(carrierEnrichment)
            .setHeader(KafkaConstants.KEY, simple("${body.shipment.orderId}"))
            .marshal().json(JsonLibrary.Jackson)
            .to("kafka:" + Topics.SHIPPING_SCHEDULED_ENRICHED_CAMEL);
}
```
{% endraw %}

notification-service · ShipmentEnricherRoute.java

> The route reads ShipmentScheduled, calls shipping-service with the carrier taken from the message, and hands the original message and the answer to an aggregation strategy that merges them. The error handler retries a failed call three times with a one second delay. The consumer option breakOnFirstError is the guard that matters: with it the failed record is read again and the group's offset stays put, and without it the consumer commits past the failure and the record is never enriched, silently and permanently. The Kafka key is set through the header, and a key option on the endpoint would take precedence over it. A burst of 500 shipments made 500 lookups and took 6.4 s from the producer call to the last output.

## [068] STREAMS · Enrich from a local copy

p. 68

[chip] JAVA

```java
StreamsBuilder builder = new StreamsBuilder();
GlobalKTable<String, CarrierReference> carriers = builder.globalTable(Topics.SHIPPING_CARRIERS,
        Consumed.with(Serdes.String(), references),
        Materialized.<String, CarrierReference, KeyValueStore<Bytes, byte[]>>as(CARRIER_STORE)
                .withKeySerde(Serdes.String()).withValueSerde(references));
builder.stream(Topics.SHIPPING_SCHEDULED, Consumed.with(Serdes.String(), scheduled))
        .leftJoin(carriers,
                (orderId, event) -> event.shipment().carrier().name(),
                (event, reference) -> enrichment.merge(Enrichment.KAFKA_STREAMS, event, reference))
        .to(Topics.SHIPPING_SCHEDULED_ENRICHED, Produced.with(Serdes.String(), enriched));
return builder.build();
```

notification-service · ShipmentEnrichmentTopology.java

> The carriers topic is read into a global table, which holds every partition on every instance. The first lambda in the left join says where to look: the join key is a field of the value, not the record key, which is the order id, and that is what a global table is for. A regular table joins on the record key, so joining shipments on a table keyed by carrier would match nothing and would need a re-keying and a repartition topic for two rows. The cost is a full copy of the topic per instance, which suits a small table and not a large one. It is a left join, so a shipment whose carrier has no reference is forwarded with an empty mode and link, where an inner join would drop it silently. The same burst of 500 shipments took 2.5 s.

## [069] STREAMS · Event time or arrival time

p. 69

- A stream-stream join pairs two events that share a key and fall within a time window of each other, and keeps each side in state until the window closes.
- Streams. The window is measured on occurredAt in the payload, so late and replayed records land where they happened.
- Camel Aggregator. The timeout counts the time a group has been quiet on the service's clock, so a replay looks like a burst.
- Unmatched. Streams emits PaymentOverdue only when stream time passes the window. Camel emits it when the timeout expires.

> Shipping may book a carrier only when the stock is reserved and the payment is taken. The two facts come from two services on two topics, keyed by the same order id, and either may arrive first. Hohpe and Woolf call the correlating unit an Aggregator, a stateful filter that stores related messages until a complete set has arrived. Both forms are Aggregators in that sense, and they differ in how they decide a set is complete and which clock they use. A payment within 30 minutes of the reservation is a statement about when things happened, and only event time can keep it. A payment within 10 seconds of reaching this service is a statement about the service, and the timeout is the direct form of it.

## [070] STREAMS · Two clocks, two answers

p. 70

shipping-service · Kafka Streams left join against Camel Aggregator

> Two crafted pairs show the clocks disagreeing in opposite directions. Order O-2003 is reserved at 09:10 and paid at 09:12 in event time, but the records are produced more than the timeout apart: Streams matches it, and the Camel group times out first and reports PaymentOverdue. Order O-2004 is reserved at 09:20 and paid at 10:50, a replayed payment, with both records produced back to back: Streams sees 90 minutes and reports PaymentOverdue, while Camel sees both facts together and reports ReadyToShip. A stream that goes quiet never closes its windows, because stream time advances only with records; Camel's timeout needs only the clock, and an unpaid reservation was reported 10.05 to 11.07 seconds after it arrived in 20 runs with a 10 second timeout. Matching 200 orders took about 2.6 s on Streams and about 3.1 s on Camel.

## [071] STREAMS · Windows with grace

p. 71

- A windowed aggregation groups records by key into windows of event time, keeps each open for a grace period, emits one final result when stream time passes its end plus the grace, and drops a record that arrives after that.
- Problem. Nothing says when a pickup list is complete, and waiting for everything never ends on a stream.
- Grace is latency. The 09:00 slot ends at 09:15 and its list leaves when stream time passes 09:20.
- Late and dropped. A record past the grace is not an error. Only a dropped-records counter shows it, so alert on that.

> Each carrier collects parcels in pickup slots, and the warehouse needs one list per carrier per slot. Arrival time is the wrong clock here, because a paused consumer, a replay or a republish through the outbox would move shipments into the wrong slot, so the slot comes from the time the shipment was scheduled. Stream time is the newest event time seen and advances only when a record arrives, so the wall clock never closes a window. A longer grace waits longer for stragglers and delays every list by the difference. A shorter one sends lists sooner and drops more of them, so the right value is the largest disorder the source produces. A pickup slot is a tumbling window, because windows that advance by less than their size would put every shipment in several lists.

## [072] STREAMS · When a window closes

p. 72

shipping-service · pickup slots, stream time and the grace period

> Seven FASTLINE shipments stamped 09:01 to 09:07 arrive, then one stamped 09:16 opens the next slot and moves stream time to 09:16. A shipment stamped 09:10 arrives next. It is behind stream time, but the 09:00 window stays open until 09:20, so it joins that list. A record stamped 09:21 moves stream time past 09:20 and the 09:00 list leaves with a count of 8. A shipment stamped 09:05 arriving after that belongs to a closed window and is discarded, and the only trace is the task's dropped-records counter, which Micrometer exports. The Camel form closes a batch per carrier at 5 shipments or after 10 seconds without a new one, so a late record joins whatever batch is open.

## [073] STREAMS · Slots, grace and one final list

p. 73

[chip] JAVA

```java
var builder = new StreamsBuilder();
var perSlot = builder.stream(Topics.SHIPPING_SCHEDULED,
                Consumed.with(Serdes.String(), scheduled).withTimestampExtractor(new PayloadTimestampExtractor()))
        .groupBy((orderId, event) -> event.shipment().carrier().name(),
                Grouped.with("by-carrier", Serdes.String(), scheduled))
        .windowedBy(TimeWindows.ofSizeAndGrace(SLOT, GRACE))
        .aggregate(PickupList::empty, (carrier, event, list) -> list.add(event.shipment().id()), store);

KStream<Windowed<String>, PickupList> out = updates
        ? perSlot.toStream()
        : perSlot.suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded())).toStream();
```

shipping-service · PickupTopology.java

> The stream reads event time from the payload through a timestamp extractor and re-keys by carrier, which writes the records to a repartition topic whose name is fixed so it stays stable across restarts. The window is a time window of one slot size with a grace, and the aggregate adds each shipment to the list for its carrier and slot. An aggregation's output is a table, so each record that lands in a window would emit an updated list, and a warehouse acting on the first one would pick up one parcel. Suppress until window closes holds the updates and emits only the last value per window, at the cost of the grace as added latency. The buffer is unbounded and in memory, which is acceptable here because one list per carrier per slot is small. For 1,000 shipments, the last list left 1.3 s after the tick.

## [074] STREAMS · Saga: compensation, not rollback

p. 74

- A saga is a sequence of local transactions, each publishing the event that triggers the next, in which a failure is undone by compensating transactions that are new events.
- Problem. An order touches three databases and no transaction spans them, so a declined payment leaves the stock reserved.
- Choreography. Each service reacts to the events of the others, with no coordinator. Each publishes through an outbox so a committed step always announces itself.
- No isolation. While it runs, readers see reserved stock for an order that may be cancelled.

> Garcia-Molina and Salem introduced the saga in 1987, and Richardson names the two ways to coordinate one: choreography, where each participant reacts to events, and orchestration, where a central unit gives the instructions. The outbox gives every service an atomic step, its row and its event committing together. It does not give the order a way to go back. A compensation does not restore the previous state. It records a new fact that makes the previous one harmless: a declined payment publishes PaymentFailed, order-service cancels the order and publishes OrderCancelled, and inventory releases on either event. Inventory may see both in no guaranteed order, either twice, or before the reservation exists, so the release has to survive all of it.

## [075] STREAMS · Choreographed saga and its compensation

p. 75

order-service, inventory-service, payment-service · events only, no coordinator

> order-service holds the saga row for each order, with a state, a deadline and a version, and every transition is an update that names the state it expects, so a payment reply and the timeout sweeper that arrive together for the same saga change exactly one row. Silence has no event to react to, so every wait has a deadline owned by one service, and the sweeper cancels a saga between 0.6 and 1.7 seconds after its deadline. The hazard is a participant that was down for the whole timeout and then reads a reservation for an order that was cancelled. A semantic lock handles it: payment-service records the cancelled order ids and checks them before it takes any money, so a late payment for a cancelled order is refused.

## [076] STREAMS · What each step can undo

p. 76

Step | Action | Compensation | Kind

Reserve stock | inventory-service holds the quantity | Release the reservation, idempotent and commutative | Compensatable

Take payment | payment-service takes the money | A refund, a business decision and not an undo | Pivot

Schedule shipment | Book the carrier | None; retried until it succeeds | Retriable

> Steps differ in what can be done about them afterwards. The three kinds come from Richardson and the Azure Architecture Center's Saga pattern. A compensatable step has an undo, so the stock release is written to be idempotent and commutative: applied twice it releases once, and applied before the reservation it leaves a marker so the late reservation is refused. The pivot is the step after which the saga goes forward or ends, and a payment cannot simply be undone, because a refund is a business decision. Everything after the pivot needs a retry, because there is no way back, and the shipment is that retriable step. Not every failure needs a compensation: when the stock is not there, the saga ends at once, and no release exists for a reservation that was never made.

## [077] STREAMS · Orchestrated sagas: LRA and Quarkus Flow

p. 77

- An orchestrated saga keeps the sequence and each compensation in one orchestrator that sends a command, waits for the reply, and on a failure or an expired limit tells the participants to undo.
- Camel Saga EIP. An LRA coordinator holds the saga state, and participants register their own undo.
- Quarkus Flow. A workflow lists the tasks, and the undo is a task you write.
- Both. Every saga gets an explicit time limit, and participants only execute commands.

> Three things push a flow toward a central owner: the sequence changes and every change is a coordinated release, someone has to answer where an order is when the answer is spread over three tables, and a time limit belongs to the whole saga. Hohpe and Woolf call the central unit a Process Manager. The same flow, order then stock then payment, runs here under two orchestrators over the same participants. With the Camel Saga EIP, the coordinator holds the saga and calls each registered compensation when it is cancelled. With a Quarkus Flow workflow, the state is a row per waiting instance in PostgreSQL, and the compensation is the tasks run after a failed or timed-out wait.

## [078] STREAMS · Choreography, Camel Saga and Flow

p. 78

order-service · the same flow in three forms

> With payment-service stopped, each form has a 20 second limit. The coordinator cancels the Camel saga when the limit passes, calls the compensations registered by then, and refuses a payment service that never joined when it tries to join a cancelled saga, so no payment is taken. The workflow's wait gives up when its limit passes, then refunds, releases and cancels, so the late payment is taken and then refunded, and the money moved and came back. Both end with the order cancelled, the stock released and one OrderCancelled published. The Camel form makes 6 coordinator requests per order. A burst of 100 orders had the last one paid after 3.8 s with Camel and 2.0 s with Flow. Choreograph a short flow owned by one team, and orchestrate when the sequence changes often or has to be audited.

## [079] STREAMS · Opening a saga with Camel

p. 79

[chip] JAVA

```java
from("direct:camel-saga").routeId("camel-saga-start")
        .saga().propagation(SagaPropagation.REQUIRES_NEW).completionMode(SagaCompletionMode.MANUAL)
            .timeout(Duration.parse(timeout))
            .compensation("direct:camel-cancel-order")
            .completion("direct:camel-complete-order")
            .option("orderId", simple("${header.orderId}"))
```

order-service · CamelOrderSagaRoute.java

> The start route opens the saga at the coordinator with a new propagation, a time limit read from configuration, the two routes the coordinator calls on cancel or completion, and the order id as a saga option. The route then sends the first command to inventory. Completion mode manual is the important line: with the default, the saga completes when the route that started it ends, which is right after the command is sent and before any participant has answered, so a PaymentFailed that arrives afterwards would find nothing to compensate. The saga id travels as a header on the Kafka record, and participants join it. The coordinator is state you have to keep: restarting it with one saga in flight lost that saga in 6 of 7 restarts, and the order stayed PLACED past its time limit.

## [080] STREAMS · Analyse with a model, decide with rules

p. 80

- Analyse then decide lets a model turn text into a value from a closed set, records it with the model and prompt that produced it, and lets rules decide from the record, never from the model.
- Problem. A model that also decides can answer differently tomorrow, gives no reason but more text, and puts a refund behind a prompt.
- Closed answer. A schema forces one of seven values, with temperature 0, a fixed seed and a versioned prompt.
- Rules decide. Each decision names the rule that fired, and a replay makes no model call.

> A carrier reports a parcel it could not deliver in a sentence its driver typed, and shipping-service has to decide whether to ship again or refund. Extraction from text is fuzzy and tolerates a small error rate, and a model reads that box crushed at the depot and torn wrapping are the same kind of event without a keyword list. A decision is business policy: it has to be exact, explainable and open to change by whoever owns the policy. The recorded analysis is the boundary. Everything before it may vary and is paid for once per note. Everything after it is a function of the record, so it is repeatable, explainable and replayable. Unknown refunds, so no customer waits on a parcel nobody understood.

## [081] STREAMS · Analyse, record, then decide

p. 81

shipping-service · analysis and decision as two recorded events

> A Camel route reads the note, calls the model through an AI service that returns a typed record, and writes the analysis to its own topic. A second route runs a Drools rule unit on each analysis and writes the decision with the rule's name and version. On ten invented notes a 4B-parameter open model gave every note the same reason in each of 15 runs from fresh containers, and gave nine of ten the reason a reader would. The tenth note is injected text, and the model obeyed it in all 15 runs: the same answer every time, and a wrong one, which the rules cannot tell apart from a right one. A model call took a median of 2.4 to 3.1 s per note on a CPU with no GPU, so one analysis per note, off the request path, is affordable.

## [082] STREAMS · Rules decide from the record

p. 82

[chip] JAVA

```java
Verdict fire(Analysis analysis, int limit) {
    var unit = new DecisionUnit();
    unit.setAttemptLimit(limit);
    unit.getAnalyses().add(analysis);
    try (RuleUnitInstance<DecisionUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit)) {
        instance.fire();
    }
```

shipping-service · DecisionService.java

> The service creates a rule unit instance for each analysis, loads the analysis and the one parameter a rule reads, and fires the rules. The service then returns the single verdict. The six rules are written to be mutually exclusive, so exactly one fires for any reason and attempt. A rule that overlaps another, or a case no rule covers, throws instead of picking silently. The rule's name goes into the decision event, so a refund can be traced to the rule that produced it. Replay reads the recorded analyses from the first offset and runs this same method, with no model call. Replaying with the model asked again took about 13 s for eight notes instead of half a second, and a model that answers differently another day would change the decisions too.

## [083] SECTION · Observing event flows

Traces, metrics and logs across a commit, a log reader and a topic

> An event flow has places that belong to no service: the outbox between a commit and a topic, the topic between a producer and a group, and the dead letter topic. When one of them stops moving, the services on both sides stay healthy, because each is waiting for the other. Three habits make those places visible. The trace context travels inside the message, so one trace crosses the relay. The metrics are read where the work waits, not inside each service. And one business identifier ties spans and log lines together, so a phone call about an order leads to its trace and its logs.

## [084] TRACE CONTEXT · Trace context in the message

p. 84

- Trace context propagation writes the trace context into the message, copies it at every relay, and lets the consumer continue the trace as a child or start a new one with a link.
- Problem. An HTTP call carries its trace in a header. A message has no request to ride on, and the outbox adds a table row and a log reader between the commit and the topic.
- Carriers. The outbox row keeps the context in a column, Debezium puts a traceparent header on the record, and the consumer reads it.
- Without it. The consumer starts a trace of its own and the picture ends at the commit.
- Parent or link. A single prompt record fits a child span. A batch, or work that runs after a delay, fits a link.

> An HTTP call carries its trace in a request header, and the server reads it before it starts work. A message has no request to ride on. The producer finishes, the transaction commits, and a relay publishes the event from another process. With the outbox there are two extra hops: the event is first a row in a table, then an entry in the database log that Connect reads. Each relay has to receive the trace context from the one before and pass it on. The W3C traceparent is the carrier: a version, a trace id, the parent span id and flags. Without it, the consumer starts a new trace and nothing connects it to the order.

## [085] TRACE CONTEXT · One order, one trace, three services

p. 85

order-service to inventory-service through the outbox and Debezium: spans in one trace

> The request span has an outbox write as a child, Debezium adds a span for the time the change sat in the database log and one for reading it, and the consumer span in inventory-service hangs from the log span. Everything between the end of the outbox write and the start of the consumer span is time the services did not spend: the log, the relay, the topic and the consumer's poll. In the runs measured that gap stayed under a second. A trace of only the two services would show a quiet hole there, and a trace through Connect shows how much of it is Debezium. The trace does not say why a gap is long. A stopped connector, a slow broker and a lagging consumer look the same, so metrics at the relay and the broker are what explain it.

## [086] FLOW METRICS · Measure the flow, not the service

p. 86

- Flow metrics measure each stage where its work waits: backlog at the relay, lag at the broker, event age on arrival, dead letters as a rate. Alert on the signal that grows while the flow is stuck.
- Problem. A service's own counters see only the work that reaches it. A paused connector leaves every service dashboard green.
- Backlog. The distance in bytes between the replication slot's write position and the position the connector confirmed. The outbox table itself holds no rows to count.
- Lag. Read from the broker, because a stopped consumer's own metric simply stops.
- Age. Recorded on arrival, so it is a graph and the number a customer feels, not the alert.

> A service's metrics describe the service: requests handled, errors, latency. An event flow also has places that belong to no service. The outbox sits between a commit and a topic, and a stall there leaves both sides healthy. The obvious readings fail in specific ways. The outbox table is emptied in the transaction that fills it, so counting rows always reads zero. A consumer's lag metric disappears with the consumer. And event age is measured when something arrives, so it says nothing until the stall ends. The backlog gauge works because it reads the replication slot. A heartbeat query keeps the confirmed position moving, and the alert threshold sits at 16 KiB, between the idle reading and a paused connector.

## [087] FLOW METRICS · Where each signal lives

p. 87

order-service, Debezium Connect, Kafka and inventory-service: what each signal reads

> Each signal is read in a different place and misses something. Outbox backlog comes from the replication slot and misses anything after the topic. Group lag comes from the broker, so it survives a stopped consumer, but a stalled relay never lets records reach the topic, so lag stays flat. Event age and record age come from the consumer, only when an event arrives. Behind a Debezium outbox the two agree, and they part when a record is written again: a redriven dead letter reads young by record age and old by event age. Dead letters are read as the end offset of the dead letter topic. In the run measured, with the connector paused, the alert on backlog reached Alerting a median of 29 seconds after the orders were placed. An alert on event age did not fire during the pause.

## [088] CORRELATION · Correlation identifier across signals

p. 88

- A correlation identifier puts the business identifier on every span and in every handler's log context, puts the trace identifier on every log line, and links any trace that a timer starts to the one it continues.
- Problem. A trace id means nothing to a customer, and an order id means nothing to a tracing library. Support starts from the order id.
- Three keys. The trace id finds one request tree and its lines, the order id finds every trace and line for the order, the link joins a timer's trace to its origin.
- Loki. One label per service. The order id and trace id are structured metadata, so the index does not grow with the orders.
- Risk. A business id that is personal data, or not unique, spreads or misleads.

> Someone phones to say an order never got its payment. They have an order id. Tempo is indexed by trace id and Loki holds lines of text, and neither knows what an order is. Three things break the link: a handler that never records which order its trace is about, a timer that starts work with no trace behind it, and a log line written without a trace id. Hohpe and Woolf describe the Correlation Identifier for matching a reply to a request. Here the same idea ties traces and logs together. The order id as a Loki label would open a stream per order, so it travels as structured metadata instead, which costs a filter at query time and keeps the index the size of the service list.

## [089] CORRELATION · From a log line to its trace and back

p. 89

order-service, payment route and sweeper: log lines, spans and links in Loki and Tempo

> Both directions are provisioned in Grafana. The Loki data source has a derived field that turns the trace id of a line into a link to the trace in Tempo. The Tempo data source has a trace-to-logs link that runs a Loki query on the trace id and the span's service. From a ticket, search Tempo for the order id, open the trace and jump to the lines of any service. Or start in Loki with the order id, read the lines of both traces in time order and open either. An order that times out produces two traces, because the sweeper runs on a timer. The sweeper links to the trace that placed the order, so the original trace stays short and the link says one continues the other. Not every line carries the order id: a line written before the handler set it has only the trace id, and a query by trace id returns both.

## [090] SECTION · Running on Kubernetes with Strimzi

Kafka, topics, connectors and failover as resources an operator keeps true

> On Docker Compose a broker is a container and its address is whatever the compose file names it. On Kubernetes a broker is a pod that can be deleted at any moment, and it has to come back with the same node id, find its own data and keep a stable name. Something has to hold those rules, and here that something is the Strimzi operator. The application services run unchanged: only the addresses move, and they were already environment variables. These patterns treat the cluster itself as declared resources: node pools, topics, connectors, rebalances and a second cluster for failover, each reconciled by an operator and each with a failure mode worth knowing.

## [091] OPERATOR-MANAGED KAFKA · KRaft roles and node pools

p. 91

- Operator-managed Kafka describes the cluster as resources and lets an operator create and keep the pods, volumes, Services and configuration that realise it.
- Problem. A broker needs a node id that survives restarts, a volume bound to the node, a stable address, and in KRaft a controller quorum. Deployments cannot decide restart order.
- Node pools. A group of nodes with the same roles, resources and storage. A node is a broker, a controller or both.
- Broker identity. A replaced pod keeps its node id and its volume claim, and the topic end offsets are unchanged.
- Services. Clients use the bootstrap Service, whose name does not change when a pod does.

> A compose stack starts one Kafka container. On Kubernetes a broker is a pod that can disappear at any moment, so four things have to be held by something other than the pod: the node id, the volume, the address and, in KRaft mode, the controller quorum. A set of Deployments can start containers but cannot choose the restart order of brokers or give a replaced pod its old identity. Strimzi does that through a Kafka resource for what the cluster shares and KafkaNodePool resources for groups of nodes. In the cluster measured, one controller pool and one broker pool are the smallest cluster that shows the two roles apart. Dedicated controllers suit production. Surviving N controller failures takes 2N + 1 controllers, so a real cluster runs at least three.

## [092] OPERATOR-MANAGED KAFKA · Controllers, brokers and observers

p. 92

KafkaNodePool resources: a controller pool votes, a broker pool observes the metadata log

> The controller is the leader and the only voter of the metadata quorum. The broker is an observer: it fetches the metadata log from the controller and does not vote on it. Kafka's own metadata quorum tool, run inside the broker pod, prints exactly that. Each pod is owned by a StrimziPodSet, not a ReplicaSet or a StatefulSet, and the node ids appear in each pool's status. Deleting a broker pod shows what the operator holds: a new pod with a new UID takes the name, mounts the same volume claim and keeps its node id. Identity and data belong to the node, not to the pod. The replication factors in this cluster are 1 because it has one broker, a choice for a laptop. A cluster that survives a broker loss needs more brokers and higher factors.

## [093] OPERATOR-MANAGED KAFKA · A broker node pool

p. 93

[chip] YAML

```yaml
apiVersion: kafka.strimzi.io/v1
kind: KafkaNodePool
metadata:
  name: broker
  labels:
    strimzi.io/cluster: edp
spec:
  replicas: 1
  roles:
    - broker
  storage:
    type: jbod
    volumes:
      - id: 0
        type: persistent-claim
        size: 2Gi
        deleteClaim: true
        kraftMetadata: shared
```

KafkaNodePool · nodepool-broker.yaml

> The pool is a short resource. The label ties it to its Kafka resource, the roles list makes these nodes brokers, and the storage section asks for a persistent volume per node. The delete claim setting removes the volume when the pool goes, which suits a lab where a fresh namespace should start empty, and would be wrong for data that matters. The controller pool is the same resource with the controller role. Setting both roles on one pool gives the dual-role form that Strimzi suggests for development and testing. The operator turns this resource into pods, claims, Services and listener configuration, and reports the node ids in the pool's status.

## [094] TOPICS AS RESOURCES · Topics as declared resources

p. 94

- Topics as declared resources declares every topic as a Kubernetes resource, turns off broker auto-creation, and lets the operator reconcile Kafka toward the resources and never the other way.
- Problem. With auto-creation a typo in a topic name creates a second topic and a healthy send. No list says which topics the system needs.
- Effect. An undeclared name fails at the first send with a metadata timeout, and no topic is created.
- Drift. A changed setting is reverted on the next pass. Partitions cannot be lowered, so that mismatch is reported as not ready, not repaired.
- Cost. A topic created in Kafka with the command line tools gets no resource and stays invisible to the list.

> The list of topics a system needs is part of the system, and Kubernetes already keeps lists of things and compares them with what runs. With auto-creation on, a topic appears as a side effect of a send, with broker defaults, and a producer configured with a misspelled name gets a new topic, a healthy send and a consumer that waits for events that never come. Strimzi's Unidirectional Topic Operator reads KafkaTopic resources and changes Kafka to match them. It never creates a resource from a topic it finds. The documented rule is that configuration changes flow from the resource to the topic, and changes made outside are reverted. That makes a missing resource visible too: leave one topic out of the set and the producer cannot publish.

## [095] TOPICS AS RESOURCES · Changes go in one direction

p. 95

KafkaTopic resources and Kafka: what the operator reverts and what it cannot

> A KafkaTopic applied creates the topic. A declared setting changed in Kafka is put back on the next reconciliation pass, within a minute at the fifteen second interval used here. A topic deleted in Kafka is recreated from its resource, as a new topic with a new id. Partitions raised in Kafka cannot be lowered, so the resource turns not ready with a reason that says decreasing partitions is not supported, and the mismatch is reported, not repaired. A topic created in Kafka gets no resource. Deleting the resource deletes the topic, because of a finalizer, so a topic that must outlive its resource is made unmanaged first. Teams that administer topics with Kafka's own tools will see their edits reverted.

## [096] TOPICS AS RESOURCES · A topic in the repository

p. 96

[chip] YAML

```yaml
apiVersion: kafka.strimzi.io/v1
kind: KafkaTopic
metadata:
  name: edp.orders.placed
  labels:
    strimzi.io/cluster: edp
spec:
  partitions: 3
  replicas: 1
  config:
    retention.ms: 3600000
```

KafkaTopic · kafkatopics.yaml

> This is one of nine topic resources in a single file. The name follows the topic convention for the domain, the label binds the resource to the cluster, and partitions, replicas and the retention setting are written down where review, diff and rollback work as on any manifest. The set is checked against the list of topics the services use, so a topic added to the services without a resource is caught. The retention here is one hour, a lab value. A setting that matters belongs in the resource, because a value changed in Kafka by hand is reverted. A topic that an application creates at run time, such as one per tenant, has no resource written in advance, and generating them is more machinery than the list is worth.

## [097] FIELD OWNERSHIP · Server-side apply and field managers

p. 97

- Server-side apply applies under a named field manager, so the API server records who owns each field and refuses a write from another manager that would change it.
- Problem. Client-side apply refuses neither of two writers. A platform patch to a memory limit is silently overwritten by the next deployment.
- Size limit. Client-side apply copies the manifest into an annotation, capped at 256 KiB. The CloudNativePG install fails on its two largest CRDs.
- Conflict. A second manager gets an error naming the manager and the field. Forcing takes the field.
- Operators. Strimzi applies its Services server-side too, so a foreign annotation survives but a change to its own field is put back.

> Application manifests set a memory limit, a platform team raises it with a patch, and the next deployment puts it back. Nothing reports an error, and the last writer wins. Client-side apply compares the manifest, the live object and a copy of the last manifest stored in an annotation. That copy has two consequences. The annotation counts toward a 256 KiB limit for all annotations on one object, so a large CRD cannot be applied. And the server treats every write as an ordinary update. Server-side apply sends the manifest to the API server, which records field owners in managed fields. Fields set to the same value by two managers are shared. Different values are a conflict until a manager forces. Force in a pipeline with nobody watching turns the report off, so keep it for a decision.

## [098] FIELD OWNERSHIP · Two managers, one field

p. 98

Deployment managed fields: edp-apps and edp-platform, a conflict and a forced change

> Two managers apply to one Deployment. The first owns the container's memory limit. The second sends a partial Deployment with only that field, and the server refuses it with a message naming the manager and the field. With the force flag the value changes and the managed fields show the second manager as owner. The next apply by the first manager now conflicts the other way. A diff computed by the server shows what an apply would change before anything is written, which is the check to run before applying a shared object. Mixing client-side and server-side apply on one object is the way to lose track of owners. Server-side apply reports a conflict. It does not say which value is right, so settle ownership before the second manager exists.

## [099] ADMISSION · Restricted pods by default

p. 99

- Restricted pods by default labels every namespace that runs workloads with the restricted Pod Security level, and gives each workload the security context that level requires from the one place that owns it.
- Problem. Kubernetes admits any well-formed pod. Operators create most pods in a cluster, and the operator decides their security context.
- Applications. A patch adds non-root, no privilege escalation, dropped capabilities, a seccomp profile and a read-only root filesystem with a writable temporary directory.
- Strimzi. Select the restricted pod security provider, or the default provider's Kafka pods are refused.
- Exceptions. An image that cannot comply gets a namespace of its own, because a label cannot exempt one pod.

> Kubernetes ships three Pod Security Standards: privileged, baseline and restricted. Nothing applies one until a namespace asks. Telling every team to write a good security context depends on every team remembering, and most pods in a cluster are written by operators, not by the team. A namespace label turns the rule on, and the API server then refuses a violating pod whoever created it. Labelling a namespace that already holds pods prints a warning for each pod that would fail and evicts nothing, so the refusal arrives with the next pod creation, usually a rollout. The UBI images name a numeric user, so the kubelet can check the non-root requirement. The level checks pod specs and nothing else: it does not scan images or limit network paths.

## [100] ADMISSION · What the API server admits

p. 100

Namespace labels and pod specs: restricted namespaces, one exception namespace

> The two namespaces that hold Kafka, PostgreSQL and the applications carry the restricted label, pinned to a policy version so an upgrade does not move the rule. The applications pass with the security context patch. The Strimzi pods pass once the operator uses the restricted provider. The CloudNativePG pods need no change, because the operator and the instance pods already run as non-root with a runtime default seccomp profile. The observability image runs as published, with no security context, so it lives in a namespace without the label. A server dry-run of the label answers whether that image could be admitted, and repeats the answer whenever the image changes.

## [101] OPERATOR METRICS · Three layers, one query language

p. 101

Strimzi Metrics Reporter, operator endpoint and kube-state-metrics into one collector

> An operator-managed cluster has three layers that fail independently: the Kafka processes, the operator, and the status of the resources the operator manages. Consumer lag and relay backlog say that something is stuck and not why. The pattern exposes each layer through its own endpoint and scrapes all of them with the collector that already carries the application telemetry. The Strimzi Metrics Reporter serves Prometheus text on every Kafka pod. The operator exposes reconciliation counters. Kube-state-metrics turns the status of custom resources into series. A topic whose partitions were raised in Kafka reads not ready while every broker stays healthy, and only the resource series shows it. The allow list is the one setting that applies without rolling the pods, and a narrowed list hides a signal with no error.

## [102] REBALANCE BY PROPOSAL · Scaling and moving partitions

p. 102

- Rebalance by proposal asks Cruise Control for a proposal, reads what it would move, approves it only when acceptable, and lets the operator ask and approve when a pool grows or shrinks.
- Problem. A new broker joins empty, because placement is decided once at topic creation. A broker that holds replicas cannot simply be deleted.
- Proposal. A status on the KafkaRebalance resource with replicas to move, data to copy and balance before and after. Nothing moves until approval.
- Auto-rebalance. Templates for adding and removing brokers. A scale-down without one is refused while the broker holds replicas.
- Cost. A rebalance copies data and competes with clients for disk and network.

> A cluster that runs out of room gets another broker, which in Kubernetes is one number in a node pool. The pod starts, joins, passes readiness and holds nothing, because every existing partition stays where it was created. Cruise Control keeps a model of the cluster built from partition load and proposes the smallest set of movements that brings it within its goals. Strimzi surfaces that as a KafkaRebalance resource. The proposal appears in the resource's status, and approval is an annotation, a human decision by default. A large amount of data to move is the signal to wait for a quiet hour. The operator can also do both steps for the two cases it recognises, a pool that grows and a pool that shrinks.

## [103] REBALANCE BY PROPOSAL · Grow, propose, approve, move

p. 103

KafkaRebalance: proposal ready, rebalancing, ready, and the remove-brokers case

> The sequence is a scale, a proposal and a move. When the pool grows from two brokers to three, the new broker gets the next free node id, not the next number in a sequence, because ids are shared by all pools. The rebalance resource asks for a plan, the status carries the proposal, and after approval the resource goes to rebalancing and then ready, with the new broker holding replicas. Shrinking works the other way. Without auto-rebalance the operator refuses to remove a broker that still holds replicas, and the Kafka status carries a scale-down prevention warning. With the remove-brokers template it drains the broker first and then removes the pod. Auto-rebalance reacts to a changed replica count in an existing pool and to nothing else, so a skewed cluster that did not scale needs a manual full rebalance.

## [104] REPLICATED CLUSTER · Two clusters with MirrorMaker 2

p. 104

edp and edp-dr: mirrored topics, checkpoints and a consumer moved by bootstrap address

> A second cluster that receives a copy of the topics answers where the data is, not where a consumer should start. A consumer's position is a committed offset stored in the cluster it reads from, and Kafka does not promise a record has the same offset in both. MirrorMaker 2 records how source offsets map to target offsets and, with group sync on, writes each group's translated position into the second cluster. The identity replication policy keeps topic names, so a consumer changes only its bootstrap address and continues where it stopped. Two properties apply to every failover. The target group is written only while no consumer is active in it, and a translated offset can trail the real position, so records are read twice. The copy is made after the write, so records the mirror had not yet copied are lost.

## [105] CONNECTORS AS RESOURCES · Connect built in the cluster

p. 105

- Connectors as resources declare the Connect image, every connector and every offset operation as Kubernetes resources, and let the operator reconcile the worker toward them.
- Problem. A worker image must contain plugins, a connector is JSON sent over HTTP, and an offset change leaves no trace in the repository.
- Build. The operator runs a build pod, checks each artifact's checksum, and pushes an image to a registry the setup names. A wrong checksum stops the build.
- Credentials. A config provider reads the database secret in the Connect pod, so the password is never in the resource.
- Offsets. An annotation lists or alters them through a ConfigMap. A list works while the connector runs, an alter only while it is stopped.

> On a compose stack a connector is a JSON file that a script sends to the Connect REST API. Nothing in the repository says which connectors should exist, and nothing notices when one is missing. On Kubernetes the operator that runs Kafka can run Connect too. The image becomes a build section of the KafkaConnect resource, each connector a KafkaConnector, and an offset operation an annotation on the connector. The REST API stays for reading status and offsets. Strimzi runs no registry, so one has to be named for the build. Two Debezium connectors in one Connect cluster must not share a topic prefix, or the second never takes its snapshot. The captured table must be owned by the connector's role, or the publication cannot be created.

## [106] CONNECTORS AS RESOURCES · From resource to running connector

p. 106

KafkaConnect build pod, registry, worker and KafkaConnector resources on CloudNativePG

> The KafkaConnect resource carries a build section. The operator starts a build pod that downloads the Debezium plugin, verifies its checksum, assembles an image from Strimzi's Kafka image and pushes it to the registry. The worker pulls that image by digest. Two connectors are declared as resources: an outbox router for the order service and a log-tailing connector for the inventory table. Both read the database host from the CloudNativePG Service and the password from its Secret. In a namespace that enforces the restricted level the build pod is refused, so the build namespaces carry no Pod Security labels. Connect becomes ready 80 to 100 seconds after the apply, build and pull included.

## [107] SECTION · Testing, querying and deploying

Proving the guards, asking the stream, changing the running system

> Code that fails by omission, such as a notification that never left or a second one that did, needs tests that can see absence. A stream can be asked a question without a ksqlDB-style service. A redeployed consumer group has a defined effect on the records it reads. Two smaller patterns complete the set: a resequencer for events that span partitions, and a cache that is written only from committed events.

## [108] TESTING · Event-driven tests that bite

p. 108

- Real brokers. Dev Services starts real Kafka and PostgreSQL, so a test meets the real behaviour of what it guards.
- Bite cycle. Prove a guard by removing it from an altered copy of the route. Exactly the one test that names the guard must fail.
- Drain, not first match. A first match passes on a route that notifies twice. Only a read that waits out the window can assert absence.
- Oracles. Compute the expected partition with Kafka's own hash, and write the arrival order down. Do not wait for the application to produce it.
- Limit. 20 rounds without a failure is not proof of no race. Compare against a run with the guard switched off.

> A synchronous service fails loudly with an exception or a status code. An event-driven one fails by omission: the notification that never left, the second one that did, the record keyed by the wrong field. A test that sends a message and waits for an answer passes on all of these. The route here has three guards: a repeated event id is dropped, a cancelled order gets no notification, and the notification is keyed by the order id. Each is a line a refactoring can delete while the tests stay green. Mutation testing, proposed by DeMillo, Lipton and Sayward, is the idea behind the bite cycle: the three hand-written patches are the mutants. The harness costs one Maven run per patch, so it runs from the verify script and not on every save.

## [109] TESTING · Prove a guard by breaking it

p. 109

notification-service route: an altered copy per guard and the one test that must fail

> The cycle has four steps. Copy the module, remove one guard from the copy, run the broker-backed tests against the copy, and read the reports. The set of failed tests must equal the one test that names the guard. A copy that fails two tests means a test is coupled to something it does not name. A copy that fails none means the guard is unprotected. The altered copies live under the build directory and the source is never touched. Write a bite for the guards a wrong release would hurt most: deduplication, the opt-out, the key and the retry policy. Camel route tests with AdviceWith answer a different and quicker question, what the route does to one exchange, with the consumer replaced by a direct endpoint and the producer by a mock.

## [110] COMPOSE TO KUBERNETES · The same config on two targets

p. 110

Kafka and PostgreSQL settings captured from Docker Compose and from Kubernetes, then compared

> Moving a service from compose to Kubernetes changes its addresses, and the settings underneath move too, with no report when one is lost. Reading two files side by side finds the lines that were copied. It does not find what an operator adds or changes, because the operator's defaults are in neither file. CloudNativePG defaults the write-ahead log level to logical, so its Cluster needs no line for it while compose has to ask. The complete view is the running system's own answer. The same fixed list of keys is read from the broker and from PostgreSQL on both targets, and the comparison lists what differs. Four settings differ here, each with a stated cause, and any other difference fails the comparison. A capture with a missing key fails too, because an empty file equals an empty file.

## [111] QUERYING STREAMS · Querying streams without ksqlDB

p. 111

- Question. Deliveries per carrier per minute of event time, correct when a record arrives a few seconds after its minute ended.
- Kafka Streams. Ask the application. State lives in a window store in the service and a REST call reads it. A new question is a new endpoint.
- Flink SQL. A job writes closed windows to a topic. A second system to run, and no question the job does not already compute.
- RisingWave. Materialized views read with psql. A new question is one more query, with state in the database.
- Licences. ksqlDB and Materialize are not open source, so neither is used.

> Operations wants the number of deliveries per carrier per minute, and wants it right when a record turns up late. A consumer that increments a counter counts by arrival time, so a paused or replayed consumer puts a delivery in the wrong minute. Three open source engines answer the question from one topic. Each takes event time from the delivery timestamp and each closes a window by a lateness bound. The bound is the grace period in Kafka Streams and the delay in the watermark expression in Flink and RisingWave. All three get the same value, ten seconds. The engines agree only when an out-of-order record is inside the lateness of every one of them. None of them tells the reader about a record it dropped.

## [112] QUERYING STREAMS · One topic, three ways to ask

p. 112

shipping-service deliveries topic read by Kafka Streams, Flink SQL and RisingWave

> Where the state lives decides how a question is asked. In Kafka Streams the state belongs to the application, and with several instances a client has to be sent to the instance that holds the carrier it asks about. In Flink the answer leaves as records, and a window leaves once, when the watermark passes its end, so the output holds closed windows only. In RisingWave the answer is a table, open windows are visible, and an emit-on-window-close clause hides them. On late records the test scenario shows the cost of mismatched bounds. A record 7 seconds behind the newest event time is counted by all three. A record for a closed window is skipped by Kafka Streams with a warning and ignored by the other two. With a grace of zero in Kafka Streams, window 1 counts 3 where the others count 4.

## [113] RESEQUENCER · Stream or batch resequencing

p. 113

Camel Resequencer: stream form on a global sequence, batch form sorted by order id

> A per-order buffer in PostgreSQL restores order when an order's events share a key. When several producers and topics carry one order, only a sequence that spans all of them can. A resequencer across partitions holds each event in memory until an ordering rule releases it, so the order it restores is exactly as wide as the rule. Hohpe and Woolf define the Resequencer, and Camel implements a stream form and a batch form. The stream form needs one gapless global sequence. With per-order numbers, events with equal numbers collide and are dropped as duplicates with nothing logged. The batch form sorts within a batch, which makes disorder rarer and does not remove it. Held events live only in the process: offsets are committed on a timer, so records held at a stop are neither released nor read again. The stream form holds 1000 events by default, and then blocks the consumer.

## [114] DEPLOYING · A rolling update, mid-roll

p. 114

notification-service Deployment: maxSurge 1, maxUnavailable 0, partitions moving between members

> A consumer in a group owns partitions, and whether a change loses records, repeats them or only delays them depends on how pods are replaced. With a surge of one and no unavailable pods, the Deployment starts a new pod, waits for readiness, then removes an old one. Each start and each stop is a membership change and so a rebalance. The member that gives up a partition has committed only as far as its last commit, so records after that point can be read again by the new owner. The consumer protocol moves the driving of rebalances to the broker, and the image runs under either protocol unchanged. Every strategy here delivers at least once, so a consumer that cannot tolerate a repeat needs an idempotent consumer first. A breaking schema change is a separate risk: the insert fails, the offset is committed, lag stays zero and the row does not exist.

## [115] DEPLOYING · Blue-green consumer cutover

p. 115

notify-blue and notify-green consumer groups: offsets exported from one and imported into the other

> Blue-green for consumers works on the unit of the consumer group. Blue is live under one group. Green is a second Deployment of the next version under its own group, at zero replicas. A new group has no offsets, so green would replay the topic or skip what blue had not read. The cutover gives green blue's position in four steps: scale blue to zero and wait until its group is empty, export its offsets, import them into green's group, scale green up. Nothing is missed, because green starts where blue's commits ended. Records can be read twice, bounded by what blue processed in its last commit interval. Rollback runs the same four steps backwards. The costs are two groups, a copy step and a stop between them. A compatible change does the same work with a rolling update and no copy.

## [116] WRITE-FORWARD CACHE · A cache written only from events

p. 116

order-service, Debezium, Kafka and a Camel route applying orders to Redis behind a version guard

> PostgreSQL and Redis share no transaction, so a request that writes both can die between the writes. A write-forward cache is written only from the events of committed changes, so it can fall behind its source but never contain something the source did not commit. The request writes the order and an outbox row that carries the full state in one transaction. Debezium publishes the row, and one Camel route applies it to Redis, comparing a version with a watch and a transaction because Redis has no conditional upsert. The staleness is the delay of the path: measured from the answer to the first read that finds the order in Redis, the median was 491 to 496 milliseconds across runs, close to the connector's default poll interval of 500. The costs are an outbox, a connector, a slot, a consumer and a second store, and a delay the reader sees. Where a reader must see its own write, read the source.

## [117] PRIVATE REGISTRY · Build on the host, copy into a registry

p. 117

buildah build, skopeo copy by digest into a private registry on an on-premises Kubernetes distribution

> The same images that run on compose and on minikube also run on an on-premises Kubernetes distribution, and nothing in the code changes. What differs is how the image arrives. A source build inside the cluster would produce a different image, so the unchanged Containerfile is built on the host with buildah, rootless and with no daemon, and copied into the registry the cluster already runs with skopeo. On a first copy into an empty registry a plain copy changes the digest, because skopeo compresses layers that buildah stored uncompressed. Preserving digests keeps the manifest as it is, or fails, and the Deployment then names the image by digest. TLS is verified against the cluster's published CA on every call, and the registry token is short-lived and held in one shell variable for one call.

## [118] BATCH PROCESSING · A Process Manager over batch steps

p. 118

- Problem. A reconciliation reads two co-partitioned topics. It needs a plan of steps, a fan-out with one unit of work per partition, an aggregate, and a replay of only the failed partitions.
- State between steps. Something has to remember which steps exist, which one runs and what each decided. A single long program cannot be resumed at 90 percent.
- Solution. A long-lived runner is the Process Manager of Hohpe and Woolf: it creates one Job per step and routes on what each Job did.
- Fan-out. The reconcile step is a Kubernetes Indexed Job, and index i reads partition i of both topics. Exit code 42 marks a poison partition and fails only that index.
- Memory. Step, partition and result rows live in PostgreSQL, so a replay reruns only the indexes that are not DONE.

> A reconciliation compares what was scheduled with what was delivered, per carrier, over a window. The two topics are keyed by order id and share a partition count, so partition i of one lines up with partition i of the other and the work splits cleanly by partition. What the split does not give is memory. A program that does it all in one run can be started and can finish, and when it fails near the end the only recovery is to start over and read every partition again. The runner solves that as a Process Manager: it holds the plan of three steps, plan, reconcile and aggregate, creates a Kubernetes Job for each, and records the state of every step and every partition in PostgreSQL. The reconcile step is an Indexed Job with one pod per partition. A transient fault retries one index. A poison partition fails that index alone, and a replay reruns only what is not done.

## [119] BATCH PROCESSING · Replay runs only the failed index

p. 119

batch-runner and batch-worker: a replay Job whose finished indexes exit at once

> In the first attempt indexes 0 and 2 finish and index 1 exits with code 42, a poison partition. A pod failure policy turns that code into FailIndex, so the index fails at once without a retry and the other two carry on. The run is FAILED and its offsets are already fixed, so a replay does not plan again. It creates a new reconcile Job, a new attempt. A Job cannot be told to run only index 1, so all three indexes start, and the worker checks the partition row first. A row that is already DONE means exit 0 without reading Kafka. Index 1 reads its partition and becomes DONE, and aggregate then runs once on three DONE partitions. The totals are an upsert keyed by run, partition and carrier, so the guard avoids the read and the upsert keeps the result correct. A replay costs few Kafka reads and the full quota, because the Job still asks for all three pods, 1500m.

## [120] BATCH PROCESSING · Admission and quota with Kueue

p. 120

- Suspended Jobs. The runner creates every Job with the queue-name label, and Kueue admits it only when the whole pod set fits the quota. Until then the step is QUEUED.
- ClusterQueue. The quota is 1750m of CPU and 3Gi of memory. Three reconcile pods of 500m fill most of it, so a second reconcile Job waits.
- Quota, not capacity. Kueue never checks the node. The queue limits what the batch may request and the node limits what exists.
- WorkloadPriorityClass. batch-low is 100 and batch-high is 1000, chosen by a second label on the Job. A Job without the label has priority 0.
- Eviction. A suspended-again Job is not a failure. The step returns to QUEUED, and only a failed Job stops the run.

> Kubernetes starts a Job when it is created, so two batches submitted together both start and ask for more than a team's share. Kueue adds the missing queue. The runner labels each Job with the LocalQueue, the webhook suspends it, and a Workload is created that is admitted against the ClusterQueue only when its whole request fits. Quota is reserved for the whole pod set, so a reconcile Job holds 1500m even when one pod is left. With BestEffortFIFO, an older Workload that does not fit does not block a newer one that does. A higher-priority run preempted the lower one's running index, and the interrupted attempt counted, so that index ended with two attempts, only the missing index got a new pod, and the finished indexes kept their results. The runner counts a preemption separately from a failure, because a queued step only waits.

## [121] CHOOSING A PATTERN · Choosing a pattern

p. 121

Problem | Pattern

A consumer must react, and can ask for details | Event notification

A consumer needs state and cannot call back | Event-carried state transfer

Related events must stay in order | Keys to partitions; a resequencer across keys

A commit and a publish can disagree | Transactional outbox, read by log tailing

The same message arrives twice | Idempotent consumer with an inbox

Read, process and write must happen once | Kafka transactions

One bad message blocks a partition | Retry and dead letter

The history is the record | Event store with snapshots

Reads and writes need different shapes | CQRS projections

Stored events must stay readable | Upcasting on read

Data arrives late or out of order | Event-time windows with grace

A business process spans services | Saga: choreography or orchestration

A stuck flow shows no error | Trace context, flow metrics, correlation ids

> If a consumer can ask the source for details, a notification is enough and the payload stays small. If it cannot call back, the event carries the state and the consumer keeps its own copy. If a service must change a database and publish, the outbox makes both one commit and a log reader publishes. Duplicate delivery is met with an idempotent consumer, and a failure that will not clear is moved aside with a dead letter. Every pattern here has a cost in operations or latency, and each has a case where it is the wrong choice.

## [122] CREDITS · Works these patterns draw on

p. 122

Author | Work

Gregor Hohpe, Bobby Woolf | Enterprise Integration Patterns

Chris Richardson | Microservices Patterns; transactional outbox and saga pattern pages

Adam Bellemare | Building Event-Driven Microservices, Second Edition

Martin Kleppmann, Chris Riccomini | Designing Data-Intensive Applications, Second Edition

Vaughn Vernon | Implementing Domain-Driven Design

Alex Lawrence | Message- and Event-Driven Systems with CQRS and Event Sourcing

Martin Fowler | Event Sourcing; CQRS; What do you mean by "Event-Driven"?

Greg Young | CQRS Documents; Versioning in an Event Sourced System

Danilo Sato | Parallel Change

R. A. DeMillo, R. J. Lipton, F. G. Sayward | Hints on Test Data Selection: Help for the Practicing Programmer

Hector Garcia-Molina, Kenneth Salem | Sagas

Microsoft Azure Architecture Center | Materialized View pattern; Saga design pattern

W3C | Trace Context

> The pattern names come mostly from Hohpe and Woolf, and the outbox, log tailing and saga descriptions from Richardson. Fowler names event notification, event-carried state transfer and event sourcing, and Young supplies the event store operations and versioning topics. Danilo Sato's Parallel Change gives the expand and contract sequence used for schema changes. DeMillo, Lipton and Sayward proposed mutation testing, the idea behind the bite cycle. The Azure Architecture Center's pattern pages describe the materialized view and the saga's step kinds. Garcia-Molina and Salem introduced sagas, and the W3C specification defines the trace context. None of the text or code of these works is reproduced here.

## [123] SCAN TO EXPLORE · Take it with you

p. 123

The site

Chapters and runnable Quarkus and Camel examples for every pattern

```
patterncatalyst.github.io/event-driven-patterns-with-quarkus-and-camel
```

Workshops & Tutorials

The whole series: every workshop, tutorial, and deck in one place

```
patterncatalyst.github.io/patterncatalyst-workshops-tutorials-list
```

> Take it with you. On the left is the site for this book, with every chapter and the runnable Quarkus and Camel example behind each pattern. On the right is the Workshops and Tutorials hub, which lists every workshop, tutorial and deck in the Pattern Catalyst series in one place. Both are public on the Pattern Catalyst GitHub organization.
