// Events between services: notification, state transfer, claim check,
// partitioning, resequencer, batching, consumer concurrency, share groups.

"use strict";

module.exports = function (lib) {
  lib.divider(
    "Events between services",
    "What an event carries, which partition it lands in, and how fast it moves",
    `These are the choices a team makes the moment one service tells another that something happened. The first is the size of the event: only an identifier, the whole state, or a reference to a stored attachment. The second is order: Kafka orders records only within a partition, so the record key decides which events can be trusted to arrive in sequence. The third is speed: how a publisher batches, how a subscriber fetches and polls, and how a share group lets any worker take any record.`,
  );

  lib.bulletSlide(
    "EVENT NOTIFICATION",
    "Event notification",
    [
      ["Event notification", " announces that something happened and names the thing, leaving each consumer to ask the source for the details it needs."],
      ["Problem.", " A service that calls every interested party must know them all and wait for each. A consumer told only that something happened cannot act."],
      ["Solution.", " Order-service publishes OrderPlaced with the order id after the commit. notification-service fetches the order over HTTP."],
      ["Cost.", " Each consumer adds one request per event, reads current state instead of state at event time, and cannot finish while the source is down."],
      ["Fit.", " Use it when the details change and the latest value is what matters."],
    ],
    `Order placement matters to inventory, to notification and to anyone who wants a metric. If order-service called each of them, it would have to know who they are and change whenever a new party appears. With an event notification it says that order O-1001 was placed and is done. Fowler separates this from event-carried state transfer, event sourcing and CQRS, which share the name event-driven. The producer holds no list of consumers, but every consumer still depends on order-service for the details. A notification built after the order was cancelled says CANCELLED, which is right for a customer and wrong for an auditor who wants the order as it was when placed.`,
  );

  lib.figSlide(
    "EVENT NOTIFICATION",
    "The event names the order",
    "02-fetch-back",
    "order-service publishes after the commit; notification-service fetches the order and stores a Notification",
    `The order is stored in one transaction, and the event is fired from inside it as a CDI event. A transactional observer receives it only after the commit succeeded and hands it to a Camel route that publishes it keyed by the order id. Publishing after the commit matters to the consumer, which fetches the order the moment it reads the record: a record published before the commit could be read before the row exists. notification-service unmarshals the event, calls order-service with the Camel http component and stores the customer, the amount and the status as order-service reported them at that moment. A single trace covers the request, the publish, the consume and the fetch across both services, because the trace context travels in the Kafka headers and the HTTP request.`,
  );

  lib.figSlide(
    "EVENT NOTIFICATION",
    "A source that is down holds the record",
    "02-source-down",
    "The record stays uncommitted and is polled again until the fetch succeeds",
    `The cost of the fetch shows when order-service is stopped. The consumer route sets breakOnFirstError to true, so a record whose route fails is not skipped: the consumer stops at that record and polls it again. After two redeliveries one second apart the exchange fails, and the next poll returns the same record, so the notification waits instead of being lost. Without that option the offset moves past the failed record and the notification never appears. The same behaviour has a limit: a record bound to fail every time, such as an order that will never exist, stalls its partition. A fetch that fails for a permanent reason needs its own handling, and publishing after the commit still leaves a window in which the process can stop between the commit and the publish.`,
  );

  lib.bulletSlide(
    "STATE TRANSFER AND CLAIM CHECK",
    "State transfer and claim check",
    [
      ["Event-carried state transfer", " puts the state a consumer needs into the event, so the consumer keeps its own copy and never calls the source."],
      ["Problem.", " Notification couples availability: a consumer cannot answer while the source is down, and each use costs a request."],
      ["Cost.", " The copy is as fresh as the last event applied, and the producer cannot rename a field consumers read."],
      ["Claim check", " stores the large part of a message in a persistent store and passes a reference, so only consumers that need the content fetch it."],
      ["Result.", " A 100 KB attachment travels as a claim and the event stays under 1 KB; the attachment request fails while the source is down."],
    ],
    `Put the whole order in the event and notification-service answers customer questions from its own table, even with order-service stopped. The copy needs no HTTP client, retry or circuit breaker because it makes no call. One guard matters: an event that would move the status backwards is ignored, so a late OrderPlaced cannot undo a cancellation that arrived first, because the two topics have no order between them. Carrying state also grows the event. Kafka bounds the size, with a producer limit of 1048576 bytes by default, so a large attachment goes into PostgreSQL and the event carries a claim. The cost is a second store with its own lifecycle, and these claims never expire. Do not copy state that must be current, such as a stock level that decides something right now.`,
  );

  lib.figSlide(
    "STATE TRANSFER AND CLAIM CHECK",
    "What the consumer does after the event",
    "03-notification-vs-state-transfer",
    "Notification carries an id and the consumer fetches; state transfer carries the Order and the consumer reads its copy",
    `Event notification carries only the order id, so the consumer calls the source and receives current state. Event-carried state transfer carries the whole Order and its status, so the consumer upserts its own order_copy table and answers from it. The first keeps one owner and always returns current data, but it couples availability and sends the source one request per event per consumer. The second removes the call and the coupling to availability, and replaces them with a dependency on the event's fields and a copy that is only as fresh as the last event applied. Choose notification when one owner must answer with current data. Choose state transfer when consumers read most of the state and it is small.`,
  );

  lib.figSlide(
    "STATE TRANSFER AND CLAIM CHECK",
    "Store, publish the claim, fetch on demand",
    "03-claim-check",
    "order-service stores the attachment in orders.claim_check; the event carries only the claim id and size",
    `The attachment is stored in the order service's claim_check table in the same transaction as the order, and the event carries a claim with an id and a size in place of the content. Any holder of the claim id reads the content from the order service, and notification-service fetches it only for the attachment endpoint, never for the order fields. Camel has a Claim Check EIP, but it stashes content for the same route, so it cannot carry content from one service to another; between services the store has to be shared, here PostgreSQL. Sending the same attachment inline makes the producer refuse the event with a RecordTooLargeException, and the order has already committed. Do not use a claim check for small payloads: it adds a second store, a second call and a lifecycle.`,
  );

  lib.bulletSlide(
    "PARTITIONED ORDERING",
    "Keys and partitions",
    [
      ["Partitioned ordering", " gives every event of an entity the same record key, so the entity lives in one partition, the only place Kafka guarantees order."],
      ["Problem.", " Replicas read partitions at different speeds, so an order's events in different partitions are applied in whatever order the replicas reach them."],
      ["Key.", " The default partitioner hashes the key bytes with murmur2 modulo the partition count, so one key always lands in one partition."],
      ["Wrong keys.", " The event type splits one order across two partitions. The carrier uses at most two of three partitions and leaves one idle."],
      ["Limit.", " Order holds per entity only, and the partition count caps parallelism."],
    ],
    `The shipping service publishes ShipmentScheduled, ShipmentInTransit and ShipmentDelivered, and the order service turns each into a status with the last write winning. If Delivered is applied before Scheduled, the order ends SHIPPED and no error is raised. Kafka's documentation states the guarantee as order per partition and none across partitions. Keyed by order id, all three events of one order go to one partition in increasing offsets. Keyed by event type, one replica owns Scheduled and another owns the other two, so pausing the first makes the status regress from DELIVERED to SHIPPED. Keyed by carrier, nothing reorders, but only two keys exist, so one partition has nothing to do.`,
  );

  lib.figSlide(
    "PARTITIONED ORDERING",
    "The key picks the partition",
    "04-keys-to-partitions",
    "Order id, event type and carrier as keys over three partitions",
    `Three key strategies run against the same three-partition topic. The order id keeps an order's events together and spreads different orders across the partitions. The event type puts ShipmentScheduled in one partition and the other two events in another, for every order, so one order spans two partitions and two replicas. The carrier keeps each order in one partition but has only two values, so at most two of the three partitions receive records. In the stalled-replica run, the order id key ends DELIVERED, the event type key ends SHIPPED, and the carrier key reorders nothing but leaves a replica idle. Keying by entity id also leaves the partition count as the limit on parallelism, and it does not order events across entities.`,
  );

  lib.codeSlide(
    "PARTITIONED ORDERING",
    "The key is a header on the record",
    "JAVA",
    "04-ordering-and-partitioning/shipping-service/src/main/java/com/patterncatalyst/shipping/ordering/ShipmentPublishRoute.java",
    13, 27,
    "    @Override",
    "shipping-service · ShipmentPublishRoute.java",
    `The route splits the plan into the three shipment events, sets the Kafka key header from a strategy bean, marshals each event to JSON and sends it with the Kafka producer, waiting for the broker's answer. The strategy is chosen by configuration, so switching from the order id to the event type changes one environment variable and no code. Waiting for the answer with synchronous true and recordMetadata true puts the partition and offset of every record in the response, which is how a run shows where each event landed. A route that sets the key from the order id needs no buffer at all.`,
  );

  lib.bulletSlide(
    "RESEQUENCER",
    "Resequencer",
    [
      ["A Resequencer", " is a stateful filter that holds out-of-sequence messages and releases them in order (Hohpe and Woolf)."],
      ["Problem.", " When several producers or topics describe one order, no single key puts its events in one partition, so Kafka cannot keep them in sequence."],
      ["Solution.", " Each event carries a sequence number. Replicas hold early events in a table and apply from the next expected number under a row lock."],
      ["Result.", " With the event type as key, the stalled-replica run that ended SHIPPED now ends DELIVERED, in lifecycle order."],
      ["Cost.", " One row per early event and one lock per event. A missing event holds that order's following events until someone repairs it."],
    ],
    `Keying by entity id is the first answer because it needs no buffer. A resequencer is for the cases where the key cannot change, and it pays with held rows and with events that wait. Every shipment event already carries its number within the order, and both order-service replicas share the database, so the database is where an order's events wait. One transactional method inserts the order's row if it is missing, locks it, ignores an event already applied or held, stores the event, and applies every event from the next expected number. An event is either held or applied, never both. If order only matters for the final state, a version number checked at the write can reject an older event with no buffer at all.`,
  );

  lib.figSlide(
    "RESEQUENCER",
    "Hold in the database, apply in order",
    "04-resequencer",
    "Events 2 and 3 wait in the pending table until event 1 arrives; then 1, 2 and 3 are applied and deleted",
    `Replica B receives events 2 and 3 first. It finds that number 1 is missing and stores both in the pending table, which has a unique constraint on order id and sequence. Nothing is applied. When replica A receives event 1, one transaction under the order's row lock applies 1, 2 and 3 in order and empties the table. Two replicas handling an order's first event cannot both insert its row, because the insert does nothing on a conflict, and the lock then serialises them, so no event is applied twice or out of turn. Events arriving as 3, 2, 1 are applied as 1, 2, 3, and a duplicate is ignored. A gap in one order does not hold another order's events, because the lock and the counter belong to one order.`,
  );

  lib.bulletSlide(
    "BATCHING PUBLISHERS",
    "Batching publishers",
    [
      ["The producer batches", " records into one open batch per partition and sends every ready batch in one request, trading a little latency for fewer, larger requests."],
      ["Problem.", " A flash sale of 20,000 orders costs the broker one request per record unless sends overlap and batches fill."],
      ["Settings.", " Four settings matter: linger.ms (5 ms by default), batch.size (16384 bytes by default), compression, and acks. acks=all and idempotence are the defaults."],
      ["Overlap.", " A caller that waits for each acknowledgement never fills a batch. Batches fill only when several sends are in the producer at once."],
      ["Measured.", " The default route moved 4.9 times the unbatched throughput (median of five rounds, range 2.1 to 6.0). The tuned route (20 ms, 128 KiB, lz4) moved 0.4 times the unbatched throughput, because every record waits out the linger."],
    ],
    `Send does not write to the network. The producer appends the record to an open batch and returns, and a background thread sends batches that are full or have waited long enough. The linger default was 0 and is now 5 milliseconds, raised by KIP-1030. The numbers are ratios to the unbatched route in the same run, medians of five rounds. 200 callers each waited for an acknowledgement, so a longer linger only added latency. Compression is the part that pays, with lz4 cutting bytes per record to 0.38 of none and zstd to 0.19. Tune when records per request is close to one while sends are concurrent, or when bytes are the cost, and raise the linger only as far as the latency budget allows.`,
  );

  lib.figSlide(
    "BATCHING PUBLISHERS",
    "One open batch per partition",
    "05-batch-fill",
    "Records from many callers fill one batch per partition; a request carries every ready batch",
    `Records from many callers go into one open batch for each partition. A batch is ready when it holds batch.size bytes or has waited linger.ms, whichever comes first, and one request to a broker carries the ready batches for every partition that broker leads. With three partitions on one broker, the unbatched control cannot send more than three records per request, while the default and tuned routes send many. A single caller that waits for each acknowledgement sends about one record per request, however long the linger. In Camel the settings are endpoint options on the Kafka component, so the route reads like the settings it applies. A Micrometer client factory binds the producer's own metrics, so records per request is a Prometheus query.`,
  );

  lib.bulletSlide(
    "CONSUMER CONCURRENCY",
    "Consumer concurrency",
    [
      ["consumersCount", " starts that many consumer threads in one Camel route. Each is a group member, so the group divides the partitions among them."],
      ["Problem.", " After an outage a backlog must drain, each record costs work, and the thread must not leave the group while it works."],
      ["Ceiling.", " Nine partitions means nine useful consumers. The default RangeAssignor gave all nine to three members of a twelve-thread group."],
      ["Measured.", " Drain time against one thread: 0.33 for three threads, and 0.12 for twelve under RoundRobinAssignor."],
      ["Eviction.", " The time to process max.poll.records must stay inside max.poll.interval.ms, or the member is removed and its batch is delivered again."],
    ],
    `Three topics of three partitions give nine partitions, so nine is the most consumers that can read at once. Extra members are not an error, but they cost memory and a place in every rebalance and add no throughput. The assignor decides how even the split is: RangeAssignor deals each topic separately and starts from the first member every time, so three members got everything. Draining 3000 records at 5 milliseconds each, three threads took a third of the time of one, and RoundRobinAssignor with twelve threads took about an eighth. The same measurements show a fetch.min.bytes of 65536 cutting fetch requests from about 2,400 to 60 on a trickle, at the cost of up to 500 milliseconds of delay per record.`,
  );

  lib.figSlide(
    "CONSUMER CONCURRENCY",
    "Parallelism stops at the partition count",
    "06-concurrency-cap",
    "Nine partitions assigned to the first nine of twelve consumers; the last three are idle",
    `Under RoundRobinAssignor the nine partitions are each assigned to one of the first nine consumers, and the last three own nothing. The partition count is the ceiling on how many members of one consumer group read at once, so beyond it a group needs more partitions. The classic rebalance protocol with an eager assignor makes every member give up all of its partitions and rejoin, and a replica that started a moment ago interrupts all the others: the first replica gave up all nine. With the consumer protocol from KIP-848 it gave up fewer than half, four or five in recorded runs. On the classic protocol, CooperativeStickyAssignor gave up four. When the work per record is the problem, make it cheaper or move it out of the poll loop.`,
  );

  lib.bulletSlide(
    "SHARE GROUPS",
    "Share groups",
    [
      ["Competing consumers", " are several workers taking jobs from one channel, each job going to whichever worker is free. Kafka share groups implement it with per-record acknowledgement."],
      ["Problem.", " A consumer group gives each partition to one worker, so one slow job holds back every job behind it, even when other workers are idle."],
      ["Acknowledge.", " Accept, release or reject each record. A lock lasts 30 seconds by default, and a record is archived after 5 deliveries by default."],
      ["Cost.", " No ordering within a partition. Delivery is at least once, so a job that must not run twice needs an idempotent receiver."],
      ["Camel.", " The Camel Kafka component has no share-group option, so the worker is a SmallRye Reactive Messaging method."],
    ],
    `The notification service sends an email for every order placed. One customer's mailbox takes eight seconds, and twelve more jobs sit behind it in the same partition. A share group lets any worker acquire any record, so the slow job holds only the worker that took it. Use a consumer group when order per key matters, and a share group when jobs are independent. The share side costs time on every job, because a worker answers each job before it fetches the next: a partition of eight to ten fast jobs took about 2 to 7 seconds through the share group and under 1 second through the consumer group. The partition with the slow job finished in about the same time either way, but most of its jobs no longer waited.`,
  );

  lib.figSlide(
    "SHARE GROUPS",
    "Who waits behind one slow job",
    "07-head-of-line",
    "In a consumer group the jobs behind a slow one wait; in a share group another worker takes them",
    `In a consumer group, worker-1 owns the partition and delivers its records one after another, so the jobs behind the slow one wait while other workers are idle. Adding workers beyond the partition count does not help, because the extra ones own nothing. In a share group, worker-1 acquires only the slow job, and another worker takes the jobs behind it in the same partition, one at a time. A burst written as one producer batch is acquired by one worker as a whole, so the jobs behind a slow one wait for it on that worker. Setting max.poll.records to one and a small fetch size keeps each fetch short, and a one-second retry cap brings a worker back within about a second of its last acknowledgement.`,
  );

  lib.figSlide(
    "SHARE GROUPS",
    "The life of one record",
    "07-share-acquire-ack",
    "Available, acquired and locked, then accepted, released, rejected or released by lock expiry",
    `A record starts available. A worker acquires it, the broker locks it for that worker and raises its delivery count. The worker then answers with accept, which ends the job, release, which returns it to the group with a higher delivery count, or reject, which archives it. In SmallRye an ack always accepts, and release or reject travel on a nack. If the worker gives no answer before the lock duration ends, the lock expires and the record becomes available again. A worker that is only slow keeps its lock, because the Quarkus connector renews the lock of a job still in progress, and a worker that is stalled loses it. Kafka states no ordering guarantee for share groups, and the delivery count is approximate.`,
  );
};
