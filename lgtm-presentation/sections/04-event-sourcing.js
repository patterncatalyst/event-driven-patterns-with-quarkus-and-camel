// Section: Event sourcing. Aggregates, conflicts, snapshots, CQRS, replay against projection, upcasting.
"use strict";

module.exports = function (lib) {
  const { divider, bulletSlide, figSlide, codeSlide, tableSlide } = lib;
  const EYE = "EVENT SOURCING";

  divider(
    "Event sourcing",
    "An order is the stream of its own events",
    `An event-sourced service keeps no table of current state. The state of an order is the fold of its events, and the only way to change it is to append another one. Follow one order through that model: the aggregate that decides each command, what happens when two commands race, how a snapshot keeps a long stream cheap to load, how a read model answers the questions a stream cannot, when to replay and when to project, and how old events stay readable after the schema changes.`,
  );

  // ---- Aggregate ---------------------------------------------------------
  bulletSlide(
    EYE,
    "Aggregate: one object decides",
    [
      ["Definition:", " an aggregate is one object, reached through its root, that rebuilds its state from its own event stream, decides each command and appends the result at the version it loaded."],
      ["Problem:", " a cancellation and a shipment that both read PAID are each correct against what they saw, and together leave CANCELLED after SHIPPED."],
      ["Rebuilt per command:", " no stored copy of the state exists, so none can be stale."],
      ["Decide, then record:", " a refused command returns no events and writes nothing."],
      ["Version check:", " the append names the version it was decided against, and a unique (stream_id, version) constraint rejects a stale one."],
    ],
    `Three callers change an order: a REST request cancels it, a payment record marks it paid, and a shipment record ships it. If each one reads the status, checks the move and writes the result, the rules live in three places and drift apart. Worse, two callers acting on the same status at the same moment both pass their own check. The aggregate removes that by being the only place a rule lives. It is rebuilt from the stream for every command, so there is nothing cached to go stale. It decides before it records, and the append carries the version it was decided against, which lets the store refuse it when the stream has moved on. The order service keeps no order table at all: the stream is the state.`,
  );

  figSlide(
    EYE,
    "One command, one transaction",
    "14-aggregate-command-cycle",
    "order-service · command cycle: load, rebuild, decide, append, export",
    `A command arrives and the service loads the stream of that order in version order. The aggregate folds the stored events into a status, an amount and a payment id, then decides the command against that state. The result is a list of events, or a refusal that writes nothing. The events are appended at the loaded version plus one inside the same transaction. Only two of the events are public: OrderPlaced and OrderCancelled each get an outbox row in that transaction, and Debezium carries them to their topics. The other events stay private to the store, so the table's schema is never the contract with other services. If the command fails after the append, the event row and the outbox row roll back together.`,
  );

  codeSlide(
    EYE,
    "The append is the concurrency check",
    "JAVA",
    "14-aggregate-root-and-event-store/order-service/src/main/java/com/patterncatalyst/shipping/aggregate/EventStore.java",
    27, 48,
    "    /** Appends the events as versions expectedVersion + 1 onwards, or throws {@link ConcurrencyConflict}. */",
    "order-service · EventStore.java",
    `The store has two operations, load and append. Append writes each event at the expected version plus one through Panache. The unique constraint on stream id and version is the whole concurrency check: when two commands loaded the same version, one insert succeeds and the other violates the constraint. Hibernate reports that as a constraint violation inside a persistence exception, and the store turns the one for the version constraint into a ConcurrencyConflict, while any other failure passes through unchanged. Greg Young describes the same two operations with an optimistic check against an expected version; keeping the version in the constraint means no second table is needed. Without the constraint nothing detects the race and a history can stop being a legal lifecycle.`,
  );

  // ---- Conflicts ---------------------------------------------------------
  bulletSlide(
    EYE,
    "A lost race: decide again or report",
    [
      ["Rule:", " retry what does not depend on what the caller saw, and report what does."],
      ["No precondition:", " a cancellation means \"cancel if the rules allow\", so the server reloads the stream and decides again, up to three times with SmallRye Fault Tolerance."],
      ["With If-Match:", " a client that decided from version 2 gets 412 Precondition Failed when the stream has moved, and no command runs."],
      ["Never blind:", " appending the decided events at the new head puts CANCELLED after SHIPPED into the history."],
      ["A refusal is an outcome:", " 409 over REST, logged and acknowledged on a Kafka channel."],
    ],
    `When two commands load the same version, one append wins and the other fails with a conflict, and the loser's transaction writes nothing. What happens next depends on who made the decision. A cancellation sent with no precondition does not depend on the state the server read, so the server loads the stream again and decides again: if a shipment won, the second decision is a refusal, and if a payment won, the cancellation is accepted at the next version. A client that cancelled because of what it saw has not asked for a cancellation at whatever version exists by the time it runs, so it sends its ETag as If-Match and a stale tag is answered with 412. The retry wraps a call to another bean, so every attempt is a new transaction.`,
  );

  figSlide(
    EYE,
    "Decide again, never append blind",
    "14-conflict-redecide",
    "order-service · a cancellation at version 2 loses to a shipment at version 3",
    `A cancellation was decided at version 2, and a shipment was appended as version 3 in the meantime. The cancellation's append fails on the version constraint. Decided again from version 3, the cancellation meets a shipped order and is refused with 409, which is a valid outcome. Appended blind at version 4, the same events land after the shipment and the history records an order that shipped and then was cancelled, which the lifecycle forbids. The same service can run the retry in a Camel route instead of a SmallRye channel: a redelivery policy on the conflict exception, with the refusal handled and acknowledged. Both run the same transactional bean, so the stream and the aggregate behave identically.`,
  );

  // ---- Snapshots ---------------------------------------------------------
  bulletSlide(
    EYE,
    "Snapshot: replay only the tail",
    [
      ["Definition:", " a snapshot stores the aggregate's state at one version, so a load reads it and replays only the events after it."],
      ["Problem:", " a load costs the length of the stream. SKU-1503 holds 100,000 events and every reservation reads all of them."],
      ["A cache, not a record:", " it can be deleted at any time and the events rebuild it. Only the newest is kept, and the aggregate's own code writes it."],
      ["Measured:", " streams of 1,100, 10,100 and 100,200 events load in 1.6, 11.6 and 127.7 ms in full, and in 0.50, 0.63 and 0.95 ms with a snapshot."],
      ["Skip it", " for short streams: 1,100 events load in 1.6 ms without one."],
    ],
    `Most streams stay short, but stock, an account or a counter takes an event for every change, and the oldest events still count in the answer. The obvious fixes each give something up: deleting old events destroys the history, a second table of current quantity is a source of truth nothing marks as derived, and an in-memory cache is lost on restart and does not help a second instance. A snapshot keeps the stream as the record and only controls how many events a load reads. The timings are medians of three runs, each in a new JVM, taken inside the service with 99 events after the snapshot, the longest tail the policy allows. The full load is linear in the stream, about 1.3 microseconds per event.`,
  );

  figSlide(
    EYE,
    "Snapshot plus tail",
    "15-snapshot-plus-tail",
    "inventory-service · a load reads one snapshot row and the events after it",
    `A load reads the newest snapshot, if there is one, and then the events after its version. The aggregate is built from the snapshot plus that tail, so the work is bounded by the snapshot interval and not by the stream. The snapshot is written in the same transaction as a command, every N events, with a single upsert whose guard stops an older snapshot from moving the stored version backwards. A smaller N bounds the tail more tightly and writes more snapshots: at N of 10 a load replays at most 9 events and 1,000 reservations write 100 snapshots, while at N of 1,000 a load replays at most 999 events and 1,000 reservations write one. A route on a timer can write snapshots out of band, but then the tail is not bounded.`,
  );

  // ---- CQRS --------------------------------------------------------------
  bulletSlide(
    EYE,
    "CQRS: a read model beside the stream",
    [
      ["Definition:", " CQRS keeps a read model apart from the write model and feeds it from the write model's events, so each side has the shape its questions need."],
      ["Problem:", " the aggregate answers whether a command may happen. A customer's order list needs facts from three services, for many orders, and no index by customer."],
      ["Three rules:", " each column is written by the one event that owns it, each update only moves forward and is safe twice, and each answer says how current it is."],
      ["Cost:", " the view is eventually consistent, so a customer may list orders and not yet see the one just placed."],
    ],
    `The write model is shaped for one job: load one stream, decide, append. A customer's question is another job. The events table has no index by customer, its payloads are the owner's private format, and it holds none of the payment or shipping facts. Reading another service's store couples every reader to its schema, and asking each service per order makes the list as slow as the slowest of three. So the order service builds a table of its own from the topics the other services already publish. It is disposable, because it can be rebuilt from the events, and no request writes to it. Fowler cautions that for most systems the pattern adds risky complexity, so it belongs on a bounded context and not on a whole system.`,
  );

  figSlide(
    EYE,
    "Commands in, a view out",
    "16-command-query-split",
    "order-service · order_view fed by six topics",
    `The write side decides commands against streams. The read side is one row per order in a table, with a read-only Panache entity in front of it, answered without touching Kafka. Writing is the projector's job alone: one upsert per event that sets only the columns that event owns, so a payment never touches the shipment. The projector acknowledges a record after its transaction commits, and a crash in between replays the record, which the upsert tolerates, so the view needs no inbox table. Every answer carries asOf, the timestamp of the last event applied, and a timer exports the lag from record timestamp to acknowledgement. A burst of 250 orders, 1,250 records, was applied with a 95th percentile lag of 318 to 334 ms; with each event delayed by 50 ms it was 12,881 ms.`,
  );

  figSlide(
    EYE,
    "Six topics, six partitions, no order",
    "16-cross-topic-order",
    "order-service · the shipment upsert refuses an event that does not advance the row",
    `Kafka orders records within one partition, and the view reads six partitions on six topics, so nothing orders ShipmentInTransit before ShipmentDelivered. A last-writer-wins upsert turns that into a wrong answer: an order delivered on Tuesday shows as in transit again. The shipment upsert refuses it in SQL. A small immutable function numbers the lifecycle, and the WHERE clause makes the statement a no-op for an event that does not advance the row, which also makes a redelivered record harmless. The check lives in the statement so that the check and the write are one step, and no other channel can write the row between them. The guard works because the facts are a lifecycle, where further is defined. For a column with no order, such as an address changed twice, the event needs a version from its owner.`,
  );

  // ---- Replay or projection ---------------------------------------------
  bulletSlide(
    EYE,
    "Replay or project",
    [
      ["Definition:", " a projection is a read model built by folding the event history in order, kept current by applying each new event once, and rebuilt from the first event when its logic or question changes."],
      ["Problem:", " shipments per carrier per day touch every stream, and nothing indexes them."],
      ["Replay:", " fold every aggregate when the question arrives. Correct by construction and no new moving part, but it pays the size of the store on every request."],
      ["Projection:", " a table, a checkpoint and a timer. Fast to read, one tick behind, and wrong until rebuilt if its fold has a bug."],
    ],
    `Operations asks how many orders each carrier shipped each day. The order service has no table to ask, and a snapshot bounds one aggregate's load without helping a question about all of them. Replay answers it with the same fold the commands use. A projection answers it from a small table kept current by a checkpointed poller. The events remain the only record in both cases, so losing a projection costs time and never data. The Azure Architecture Center calls a materialized view completely disposable for that reason. A projection is worth its extra model only when the question is asked often enough, and a replay is slow enough, to justify a second thing that can be wrong.`,
  );

  figSlide(
    EYE,
    "Same question, two paths",
    "17-replay-vs-projection",
    "order-service · ReplayReport and CarrierDailyProjector",
    `Both paths return the same lines: a carrier, a UTC day and a count, and a SQL query over the raw events is the oracle for both. The checkpoint matters. Reading everything after the last position seen loses rows without a trace, because a position is assigned at insert and a commit happens after it: a transaction that holds position 41 open while 42 commits would be skipped forever. The batch query therefore reads in transaction order and only under the lowest transaction id still running, so no row can appear behind the checkpoint. The price is that one long transaction holds the projection back, and the report shows the lag in events while it does.`,
  );

  tableSlide(
    EYE,
    "What each answer costs",
    ["", "Replay every aggregate", "Projection"],
    [
      ["Read at 1,000 / 10,000 / 100,000 orders", "15 / 77 / 586 ms", "0.78 / 0.61 / 0.46 ms"],
      ["Freshness", "The committed state at the read", "One scheduler tick, about 1 s"],
      ["A new question", "Code only, exact at once", "Code plus a rebuild, 1.8 s at 100,000"],
      ["Storage", "None", "27 rows at every size"],
      ["What goes wrong", "Read cost grows with the store", "Lag, a bug repeated until rebuilt"],
    ],
    [3.6, 4.2, 4.29],
    `Both paths were timed inside the service on a store loaded to 1,000, 10,000 and 100,000 orders, which is 3,600, 36,000 and 360,000 events, using medians of three runs from fresh containers. The replay grows with the store, at about 1.6 microseconds per event at the largest size, while the projection read scans the same 27 rows at every size and is about 1,300 times faster there. At 1,000 orders the gap is 14 ms and replay is the better choice, because there is nothing to keep correct. Freshness is the projection's price: a run of 50 commands at the one-second default reached the projection with a median of 994 ms. A question that must see the command it just committed belongs to the aggregate.`,
    { fontSize: 14, rowH: 0.62 },
  );

  // ---- Upcasting ---------------------------------------------------------
  bulletSlide(
    EYE,
    "Upcaster: old events, current shape",
    [
      ["Definition:", " an upcaster turns an event stored in an older shape into the same event in the current shape, on a copy, when the event is read."],
      ["Problem:", " the store is append-only, so a new field such as shippingPriority has no place to be fixed in the old rows."],
      ["Chain:", " one small step per schema version, so a version 1 row passes through every step to the current one. An unknown version is an error."],
      ["On the wire:", " an Avro registry with BACKWARD_TRANSITIVE refuses a version that a reader built on it could not use on every record ever published."],
    ],
    `Customers can now ask for express delivery, so OrderPlaced gains a shipping priority. Two kinds of reader already hold the old shape: the aggregate loads rows written before the field existed and can never rewrite them, and a consumer replaying the topic from its first record meets records without the field. Rewriting the old rows breaks the rule that made the store trustworthy. Teaching the aggregate every shape spreads the history of the schema through code that should know only the order. An upcaster keeps the stored history as recorded, and a bug in a step is fixed in code with nothing to repair. The default a step supplies is a business decision, so it is written once and the published contract states the same value.`,
  );

  figSlide(
    EYE,
    "Upcast on read, never on write",
    "18-upcast-on-read",
    "order-service · stored rows pass through the chain before they become records",
    `A row is loaded with the schema version it was written in. The store copies the payload, applies the steps between that version and the current one, and only then maps the node to the current record, so the aggregate never sees an old shape. Appends always write the current version, which means a stream can hold both shapes for as long as it lives. The step is plain Java in the store, and no Camel type appears in it: Camel belongs to the wire, where the registry checks each new schema version against the older ones. When the chain is skipped, the current record refuses the missing field and the failure names the cause. An upcaster cannot invent a fact: when a change alters what an event means, a new event type is the answer.`,
  );

  codeSlide(
    EYE,
    "The version 1 to 2 step",
    "JAVA",
    "18-schema-evolution-and-upcasting/order-service/src/main/java/com/patterncatalyst/shipping/evolution/Upcasters.java",
    21, 28,
    "    /** The schema_version this release writes. */",
    "order-service · Upcasters.java",
    `The chain is a map from event type and schema version to a transform of the JSON node. For OrderPlaced stored at version 1, the step adds shippingPriority with the value STANDARD, the priority an order had before customers could choose. That constant must equal the default in the Avro contract, so the stored history and the published schema say the same thing. The current version is a constant of the release, and every load applies the steps that lie between the stored version and that constant. The cost is a copy of the payload on each load, and a chain that grows a step per release means every load runs every step. In exchange nothing is ever migrated, and a mistake in a step is repaired by changing code.`,
  );
};
