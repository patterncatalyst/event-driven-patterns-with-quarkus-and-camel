---
layout: plan
title: r06 plan
render_with_liquid: false
---

# r06 plan: chapters 14 and 15, aggregate root and the event store, snapshots (Opus planner, 2026-11-12)

Status: **approved by the owner 2026-11-12, all 15 recommendations**; merge when validation and CI pass (owner). Vernon TOC not pasted: 1A confirms chapter titles through the O'Reilly catalog search; Kleppmann cited (owner, 2026-11-12, reverses recommendation 11's condition), chapter title confirmed by 1A. Branch `release/r06`, cut from main at a54ab4f.

Planner note: this plan was made with read-only commands, web fetches of Maven Central metadata, the GitHub releases API, code.quarkus.io and Docker Hub, the Notion PRD, two O'Reilly catalog searches and one fetch of Greg Young's CQRS Documents PDF. No stack was started, no container was run, nothing was written and no git command changed state.

## Findings

### Repo state and what chapter 01 already teaches

- **Chapter 01 already has the event-sourcing basics.** Example 01 has `order_event` with `UNIQUE (stream_id, version)`, `jsonb` payloads under a sealed `OrderEventPayload` (`OrderPlaced`, `OrderPaid`, `OrderShipped`, `OrderDelivered`, `OrderCancelled`), and append-only row and statement triggers. `OrderService.appendEvent` replays, checks `OrderStatus.canMoveTo` and appends at `expectedVersion + 1`. A unique violation becomes `ConcurrentAppendException`, which the API returns as 409. The prose already covers replay as a left fold, the unique version settling a race, and an experiment where the second of two writers that send the same `expectedVersion` gets 409. It also says "Long streams need snapshots" (claim 01-32, Fowler).
- **So chapter 14 must not re-teach those.** It refers back to them in one sentence and adds what 01 does not have:
  1. The aggregate as one object that owns its invariants: rehydrate, decide, apply.
  2. A generic event store keyed by stream, with the expected version taken from the loaded aggregate, not from the client.
  3. Conflict policy: retry by re-deciding, or report to the client (`If-Match`). A blind re-append is the negative witness.
  4. Commands from two sources (REST and Kafka) racing on one stream.
  5. Redelivered Kafka commands made harmless by the aggregate itself.
  6. Publication of the public subset of events through the chapter 08 outbox in the same transaction.
  7. Camel at the edge.
- **Chapter 01 has no `orders` read table in chapter 14.** In 14 the stream is the only state. A schema check proves it.
- **Chapter 08 contradicts the r06 design.** Its "When not to use it" says: "When the events are the state itself, event sourcing removes the second write instead of making it safe." No claims row backs this sentence, and chapter 14 shows that an event-sourced service still publishes, through an outbox row in the same transaction. Fix now (step 2C, recommendation 9).
- **domain-model needs no bump.** `Topics` already has `ORDERS_PLACED`, `ORDERS_CANCELLED`, `PAYMENTS_PROCESSED` and `SHIPPING_SCHEDULED`; chapter 15 uses no topic. `OrderStatus.canMoveTo` and `InventoryItem` (quantity >= 0) exist. domain-model stays at 2.2.0. Only `_plans/domain-model.md` rows 14 and 15 are rewritten (step 1C).
- **Event names.** `OrderPaid`, `OrderShipped` and `OrderDelivered` are already in domain row 01. Chapter 15 needs two internal ledger events about `InventoryItem`: `StockReceived` and `StockReserved` (recommendation 6). They are messages about an existing entity, not new entities. They are deliberately not named `InventoryReserved`, because that is the public catalog event on `edp.inventory.reserved`.
- **The PRD still has freight wording.** Rows 14 to 18 (`Consignment`, `TrackingHistory`, `HandlingEvent`) would fail `validate.py`'s BANNED_DOMAIN check if copied. Rewrite rows 14 and 15 at ship, as r05 did for 11 to 13.
- **PRD constraints that bind this iteration:**
  - Non-goal: "Event-sourcing frameworks (Axon, Kurrent clients). The pattern is the lesson."
  - Q3 decided: PostgreSQL, append-only `events` table, unique `(stream_id, version)`, `jsonb`.
  - Q6 covers chapters 19 to 21 only. Elsewhere "Camel stays at the edges". The audience row asks "where Camel fits in an event-driven and event-sourced system, and where it doesn't". So the Camel path in 14 and 15 is an edge comparison, not a second store.
- **Citations.** sources.md says Bellemare has no chapter title naming aggregates or snapshots, so cite no Bellemare chapter. Lawrence ch 4 "Tactical Building Blocks" (14) and ch 6 "Event Sourcing" (14, 15) are confirmed TOC fits. Vernon, Young and Kleppmann are **not yet** in `bibliography.md` or `sources.md`, despite the brief.
- **r05 lessons carried over** (verification log):
  - Restarted consumers need `session.timeout.ms=10000`, and checks after a restart poll up to 60 s.
  - One console-producer call per intended batch; header syntax `parse.key/parse.headers`.
  - Keep record lines only (KIP-848 notice). Never `grep -q` under pipefail.
  - `precondition` stops the run. Check numbering is frozen after the window.
  - One Prometheus check per example with `service_name`.
  - 1-partition topics wherever order is asserted.
  - A pause or lock precondition is read from logs or `pg_stat_activity` before the competing input is sent.
  - `@Retry` must wrap a call to a separate `@Transactional` bean, so each attempt is a new transaction (r05 S4).
  - Camel redelivery re-invokes the bean in a new transaction (r05 S5, re-confirmed in S2 below).
- **Word counts at r05 ship:** 11 2,222; 12 2,181; 13 2,491. Target about 2,000, never padded.

### Versions re-survey (preliminary, 2026-11-12; 1A completes and records in versions.md)

| Item | Found | Source | Change |
|---|---|---|---|
| quarkus-bom / quarkus-camel-bom | 3.40.1.2 is still the newest final and the `recommended`, `lts` stream. **4.0.0.CR1 appeared 2026-11-11** (prerelease, not used) | https://repo1.maven.org/maven2/io/quarkus/platform/quarkus-bom/maven-metadata.xml ; https://code.quarkus.io/api/streams | none |
| camel-bom | 4.22.2 is the newest 4.22.x (LTS); 4.23.0 is metadata `latest` but not LTS | https://repo1.maven.org/maven2/org/apache/camel/camel-bom/maven-metadata.xml | none |
| Camel Quarkus | 3.40.0 (2026-09-28) is newest. `camel-quarkus-scheduler`, `-sql`, `-kafka` and `-microprofile-fault-tolerance` are all 3.40.0 in quarkus-camel-bom 3.40.1.2 (local pom) | https://repo1.maven.org/maven2/org/apache/camel/quarkus/camel-quarkus-kafka/maven-metadata.xml | none |
| debezium-quarkus-outbox / Connect image | 3.7.1.Final is newest (metadata updated 2026-11-10) | https://repo1.maven.org/maven2/io/debezium/debezium-quarkus-outbox/maven-metadata.xml | none; 1A re-reads the Connect tag digest (it moved twice in r04) |
| Strimzi | 1.3.1 (2026-11-04) is still the latest final, so Kafka 4.3.2 stays held | https://api.github.com/repos/strimzi/strimzi-kafka-operator/releases | none |
| grafana/otel-lgtm | v0.40.0 (2026-11-06) is still the latest | https://api.github.com/repos/grafana/docker-otel-lgtm/releases | none |
| library/postgres | 18.6 is still the newest 18.x (`18.6` pushed 2026-11-06; alpine variants re-pushed 2026-11-11, not used) | https://hub.docker.com/v2/repositories/library/postgres/tags | none |
| Not yet checked (1A) | CNPG operand 18.x timestamped tag; ubi10 openjdk-25 tags and digests; Maven #57216; SmallRye FT and Reactive Messaging in the platform; Temurin 25 patch | as r05 | |

No version appears in the 14 or 15 prose.

### What the sources say (confirmed in this session)

- **Young, CQRS Documents** (2010, https://cqrs.wordpress.com/wp-content/uploads/2010/11/cqrs_documents.pdf, HTTP 200; the old cqrs.files.wordpress.com URL redirects to it). Paraphrased, never quoted at length:
  - An event store needs two operations: read a stream in version order, and append a set of events with an optimistic concurrency check against an expected version, in one transaction. He uses an aggregates table holding the current version; chapter 14 uses the unique `(stream_id, version)` constraint instead, which is the Q3 decision.
  - Rolling snapshots are a heuristic that saves loading every event.
  - A separate snapshot table keyed to a version means a snapshot never conflicts with writers and is "valid at the version it was taken".
  - A background snapshotter finds streams more than a set number of events past their last snapshot.
  - Default serialisation of snapshots has versioning problems: when the structure changes, existing snapshots are deleted and rebuilt (or a Memento is used).
  - Snapshots can be added later.
  - Publishing from the store without two-phase commit ("Event Storage as a Queue").
  - TOC sections: "Events as a Storage Mechanism", "Rolling Snapshots" (pp. 33 and 44), "Building an Event Storage".
- **Fowler, "DDD Aggregate"** (bliki, 2013-04-23, https://martinfowler.com/bliki/DDD_Aggregate.html): a cluster of domain objects treated as one unit, reached through one root that keeps it consistent.
- **Vernon, Implementing Domain-Driven Design** (Addison-Wesley, 2013, O'Reilly urn 9780133039900, confirmed in the catalog): chapter titles are not confirmed in this session. Owner to paste the TOC, or 1A confirms it from the InformIT TOC page. The expected entries are ch 10 "Aggregates" and Appendix A "Aggregates and Event Sourcing".
- **Kleppmann and Riccomini, Designing Data-Intensive Applications, 2nd ed.** (O'Reilly, 2026-02-18, urn 9781098119058, confirmed in the catalog): the chapter title is not confirmed. Cite it only if the owner supplies the chapter title (recommendation 11).

## Approach

Same gate shape as r05:

1. G0: spikes plus the re-survey and source fetches (parallel).
2. Two parallel authoring executors on disjoint files, with `mvn verify` under the stack lock.
3. Serial exclusive runtime windows (15 first, then 14).
4. The orchestrator re-runs every prose experiment.
5. Final serial run 00 to 15.
6. Opus validation, then ship.

Both chapters use plain PostgreSQL with Panache as the event store. There is no event-store product and no framework (PRD non-goal; the Q3 decision).

**Rejected alternatives (one line each):**

- **EventStoreDB/KurrentDB, Axon, Marten-style libraries:** licence (Kurrent) or the PRD non-goal; the pattern is the lesson.
- **Tailing the `events` table directly with Debezium (the store as its own outbox):**
  - It publishes every internal event (`OrderPaid`, `OrderShipped`) and makes the store's schema the public contract.
  - Order-service's public events are only OrderPlaced and OrderCancelled.
  - The Event Router needs a non-null routing column for every row.
  - It gets one sourced sentence (Young, "Event Storage as a Queue") in "When not to use it".
- **Publishing to Kafka after the commit, or with a Kafka transaction:** a dual write; chapters 08 and 12 already show why.
- **Snapshots upcast across schema versions:** snapshots are disposable, and the events rebuild them (Young). Upcasting applies to events and belongs to a later chapter, which is not named in prose (no forward reference).
- **Time-based snapshot policy as the main path:** it does not bound the tail on a hot stream. The Camel snapshotter phase measures exactly that.
- **JMH for the rebuild cost:** too heavy for an example. A timed load inside the service with a stated method, plus deterministic event counts, is enough and repeatable.
- **A pessimistic lock on a stream head row:** it serialises every writer. It gets one sentence (PostgreSQL explicit locking docs) if the word budget allows; otherwise it is omitted.
- **A Camel second store, or the Camel Aggregator EIP:** unrelated to the DDD aggregate. One sentence names the clash.

## Per-chapter design

### 14 Aggregate root and the event store (`examples/14-aggregate-root-and-event-store`, `_docs/14-aggregate-root-and-event-store.md`)

**Headings.**

- `## Pattern: Aggregate`, with `### The problem`, `### The solution` and `### How Quarkus and Camel model it`. The bold definition covers an aggregate whose state is its own event stream.
- Then two extra H2s, as chapter 01 has one: `## Conflicts: retry or report` and `## Publishing from an event-sourced aggregate`.
- Then `## Build, run, observe`, `## When not to use it`, `## References`.
- Front matter: `part: event-sourcing`.

**Opening.** One backward sentence: chapter 01 already appends order events and lets a unique version settle a race. What it lacks is one place that owns every rule about an order, a policy for the writer that loses, and a way out to Kafka.

**Service.** order-service on port 8080 with:

- quarkus-rest-jackson, hibernate-orm-panache, jdbc-postgresql and flyway;
- quarkus-messaging-kafka and quarkus-smallrye-fault-tolerance;
- debezium-quarkus-outbox;
- micrometer-opentelemetry and opentelemetry;
- camel-quarkus-kafka, -jackson and -bean (comparison only).

Compose includes kafka, postgres, connect and lgtm, plus topics-init, which creates `edp.orders.placed`, `edp.orders.cancelled`, `edp.payments.processed` and `edp.shipping.scheduled` with 1 partition each. The connector `order-outbox` is copied from chapter 08 with its own slot and publication name (`edp_order_aggregate_outbox`).

**Schema (Flyway V1, schema `orders`).**

- `orders.events`, with columns:
  - `position bigserial pk`;
  - `event_id uuid not null unique`;
  - `stream_type text`;
  - `stream_id text`;
  - `version int check > 0`;
  - `type text`;
  - `payload jsonb`;
  - `occurred_at timestamptz`;
  - `CONSTRAINT events_stream_version UNIQUE (stream_id, version)`.
- Append-only triggers, the same technique as 01. The prose refers back to 01 and does not explain them again.
- `orders.outboxevent`, exactly as in chapter 08 (topic column).
- **No order state table.** Schema management is `validate`.

**Code** (package `com.patterncatalyst.shipping.aggregate`; each type small):

- **`OrderEvent`**: a sealed interface of payload records (`OrderPlaced`, `OrderPaid(paymentId, amount)`, `OrderShipped(shipmentId, carrier)`, `OrderCancelled(reason)`). Delivered is left out unless needed. Jackson-typed as in 01.
- **`OrderAggregate`, the root.**
  - `static rehydrate(List<StoredEvent>)` folds the stream.
  - `version()` returns the current version.
  - Command methods `place`, `markPaid`, `markShipped` and `cancel` each check the invariants against the current state and return the new events, applying them to itself.
  - Invariants:
    - The lifecycle comes from `OrderStatus.canMoveTo`.
    - Place only on an empty stream.
    - `markPaid` with the same payment id as the recorded one is a no-op (no events): a redelivered PaymentProcessed is harmless.
    - A different payment id on a PAID order is refused.
    - The payment amount must equal the order amount (mvn test only).
  - Refusal is `CommandRefused(reason)`.
- **`StoredEvent`**: Panache entity, `@Immutable`.
- **`EventStore`**: a `PanacheRepositoryBase<StoredEvent, Long>` with `load(streamId)` and `append(streamType, streamId, expectedVersion, List<OrderEvent>)`. Append does persist and flush at `expectedVersion + 1..n`. A unique violation on `events_stream_version` becomes `ConcurrencyConflict(streamId, expectedVersion)`. Spike S1 confirms the constraint-name mapping.
- **`OrderTransaction`**, `@Transactional`:
  - `handle(Command, OptionalInt clientExpected)` loads, rehydrates, decides, appends, and fires the outbox `ExportedEvent` only for OrderPlaced and OrderCancelled, in the same transaction.
  - `DemoPause.afterDecide(orderId)` sits between decide and append. It is labelled demo code and pauses once per order id from `DEMO_PAUSE_FOR`/`DEMO_PAUSE_MS`.
- **`OrderCommands`**: `@Retry(retryOn = ConcurrencyConflict.class, maxRetries = ${RETRY_MAX:3}, delay = 50, jitter = 25)` around `OrderTransaction.handle`, so every attempt is a new transaction that reloads and re-decides.
- **`RetryMode`** (demo): `RETRY_MODE=reappend` makes `OrderTransaction` re-append the already-decided events at the new version without re-deciding. This is the negative witness, labelled as such.
- **`OrderResource`**:
  - `POST /orders` returns 201 with `ETag "1"`; a duplicate returns 409.
  - `GET /orders/{id}` replays and returns the state with `ETag "<version>"`.
  - `POST /orders/{id}/cancellation` without `If-Match` goes through `OrderCommands` (server retry). With `If-Match` it calls `OrderTransaction` with the client's version and no retry, so a stale tag returns 412 (RFC 9110). `Request.evaluatePreconditions(EntityTag)` is used if S1 shows it composes cleanly with the append check; otherwise a manual comparison is mapped to 412.
  - A refused command returns 409 with the reason.
- **`PaymentConsumer` and `ShipmentConsumer`**: SmallRye `@Incoming` with `@Blocking` calling `OrderCommands`. A refusal is logged as `refused <Command> <orderId>: <reason>`, counted, and acked: a refused command is a business outcome, not a failure. Groups `order-payments` and `order-shipments`, `session.timeout.ms=10000`. Payload records `PaymentProcessed(Payment payment)` and `ShipmentScheduled(Shipment shipment)` live in the example.
- **`PaymentCommandRoute`** (profile `camel-commands`, comparison): `from(kafka:edp.payments.processed?groupId=order-payments-camel)`, `unmarshal().json(PaymentProcessed.class)`, `.bean(orderTransaction, "markPaid")`. It has `onException(ConcurrencyConflict.class).maximumRedeliveries(${CAMEL_REDELIVERIES:3}).redeliveryDelay(50)` and `onException(CommandRefused.class).handled(true)` with a log line. In that profile the SmallRye payments channel is disabled; the shipments channel stays (07 and 13 precedent).
- **Micrometer**: `order.command.conflicts` (tag `source=rest|smallrye|camel`), `order.command.refused`.

**mvn tests (Dev Services: PostgreSQL 18.6 and Kafka 4.3.2).**

- `OrderAggregate` unit tests with no Quarkus:
  - every lifecycle rule;
  - same payment twice gives no event;
  - a second payment id is refused;
  - an amount mismatch is refused;
  - rehydrate equals applying in order.
- An append at a stale version throws `ConcurrencyConflict`, and no row is added.
- **Deterministic interleaving** (a latch-based test hook in place of `DemoPause`):
  - cancel loaded at PAID, ship appended: the retry re-decides and refuses;
  - cancel loaded at PLACED, pay appended: the retry succeeds at version 3;
  - with `RETRY_MODE=reappend` (test profile) the history has CANCELLED after SHIPPED. That test asserts the bad outcome, to prove the witness bites.
- A stale `If-Match` gives 412 and no event.
- Outbox: in the test profile `remove-after-insert=false`, as in 08.
  - OrderPlaced and OrderCancelled leave one outbox row each, OrderPaid none.
  - A failure after the append (a test hook) leaves neither an event row nor an outbox row.
- Camel profile: a conflict is redelivered and re-decided.

**verify.sh (about 33 checks; numbering frozen after the window). "P" marks a precondition.**

Infra:

1. P: order-service ready; groups `order-payments` and `order-shipments` have partition 0 assigned.
2. P: connector `order-outbox` RUNNING; the four topics exist with 1 partition each.
3. Schema `orders` holds only `events`, `outboxevent` and the Flyway history: no table holds order state.

Place (O-1401, amount 25.00):

4. POST returns 201 with ETag `"1"`.
5. The stream O-1401 has exactly one row: version 1, OrderPlaced.
6. `edp.orders.placed` holds exactly one record keyed O-1401, with an `id` header, `eventType` OrderPlaced and the amount 25.00.
7. A second POST returns 409; after a wait there is still 1 row and 1 record.

Redelivered command:

8. P: `edp.payments.processed` holds 2 records for O-1401 with the same payment id (one producer call).
9. Lag 0; the O-1401 stream is versions 1 and 2 with exactly one OrderPaid.
10. GET returns PAID with ETag `"2"`.
11. No record for OrderPaid on any `edp.orders.*` topic, and no `outbox.event.*` topic.

Race 1, REST cancel retried and refused (O-1402 placed and paid; cancel paused after decide):

12. P: log `paused CancelOrder O-1402 at version 2`.
13. P: after ShipmentScheduled O-1402 is produced, version 3 OrderShipped exists while the cancel request is still open.
14. Cancel returns 409 with the reason `SHIPPED to CANCELLED`.
15. Stream O-1402 is versions 1, 2 and 3, no OrderCancelled, and every stream's history obeys `canMoveTo` (a SQL plus Python lifecycle walk over all streams).
16. Exactly one conflict was logged and retried for O-1402.
17. No record for O-1402 on `edp.orders.cancelled`.

Race 2, REST cancel retried and accepted (O-1403; cancel paused at version 1):

18. P: paused log, and version 2 OrderPaid exists while the request is open.
19. Cancel returns 200 with ETag `"3"`.
20. Stream O-1403 is OrderPlaced, OrderPaid, OrderCancelled at versions 1, 2, 3.
21. `edp.orders.cancelled` holds exactly one record for O-1403.

Client expected version (O-1404 placed and paid):

22. Cancel with `If-Match: "1"` returns 412; no event and no retry log for O-1404.
23. Cancel with `If-Match: "2"` returns 200 with ETag `"3"`; one cancelled record.

Race 3, Kafka-side conflict on the SmallRye path (O-1405 placed; MarkOrderPaid paused at version 1):

24. P: log `paused MarkOrderPaid O-1405 at version 1`.
25. REST cancel returns 200 (version 2).
26. After the pause: stream O-1405 is OrderPlaced, OrderCancelled; the refusal `CANCELLED to PAID` is logged after one retry; group lag 0.

Camel comparison (container recreated with profile `camel-commands` and `DEMO_PAUSE_FOR=O-1406`; group `order-payments-camel` reset `--to-earliest` while inactive):

27. P: the route has started and the group has partition 0.
28. After lag 0, every earlier stream is unchanged (row count and max version per stream compared with a capture taken before the phase). Every earlier payment, re-read, is a no-op or a refusal.
29. Race 3 again with O-1406: P paused log; cancel 200; stream OrderPlaced, OrderCancelled; one Camel redelivery and the refusal are logged.
30. `edp.orders.placed` holds 6 records and `edp.orders.cancelled` 4 (O-1403, O-1404, O-1405, O-1406).

LGTM:

31. Prometheus `order_command_conflicts_total{service_name="order-service"}` is present (S1 confirms the name).
32. Trace in Tempo.
33. Log in Loki.

**Experiments for "Build, run, observe" (each run in step 4, with its failing checks recorded):**

- `RETRY_MODE=reappend`: race 1 ends with OrderCancelled at version 4 after OrderShipped, and a cancelled record is published. Expected failures: 14, 15, 17 (and 16 if the log wording differs). This is the chapter's main negative witness.
- `RETRY_MAX=0`: races 1 to 3 report the conflict instead of re-deciding. Expected failures: 14, 16, 19 to 21, 26.
- Remove `CONSTRAINT events_stream_version` from V1: both writers in race 1 append version 3. Expected failures: 15 (duplicate version, illegal history), 17.
- `CAMEL_REDELIVERIES=0`: the O-1406 conflict surfaces as an error and the offset is committed, so the command is dropped rather than decided. Expected failure: 29 (refusal log). The stream outcome is the same in this race, and the prose says so honestly.
- Optional, observed only: move `@Retry` onto the `@Transactional` method. Kept only if S1 shows a deterministic outcome (r05 S4 suggests the retry runs inside an aborted transaction).

**Figures.**

- `14-aggregate-command-cycle`: command, load stream, rehydrate, decide, append at expected version plus an outbox row in one transaction; Debezium carries only OrderPlaced and OrderCancelled; internal events stay in the store.
- `14-conflict-redecide`: two timelines over race 1. Re-decide: conflict, reload v3 SHIPPED, refused. Blind re-append: CANCELLED after SHIPPED.
- Optional: `14-retry-or-report` (server retry vs `If-Match` 412).

**Decision table in prose (conflicts).** Every row is tested:

| Policy | When it is right | What breaks if misapplied |
|---|---|---|
| Retry by reloading and re-deciding (FT `@Retry`, Camel redelivery) | the command's intent does not depend on the state the caller saw | nothing; a refusal is a valid outcome |
| Report to the caller (`If-Match`, 412) | the caller decided from a state it read | silent override of a decision the caller never saw |
| Re-append the decided events | never | an illegal history |

**Camel comparison table, short.** Rows:

- where the retry is declared;
- what is re-run (both re-invoke the transactional bean, so both reload);
- what happens to a refused command (ack and log vs `handled(true)`);
- offset commit timing;
- what Camel provides for the aggregate or the store: nothing, it is the adapter. One line says Camel's Aggregator EIP is a different concept.

**Callout.** Names `OrderAggregate`, `OrderTransaction`, `OrderCommands`, `PaymentCommandRoute`. Rule: on a version conflict, reload and decide again, or hand the conflict to the caller who decided; never re-append what was decided against an old version.

**Citations.**

- Fowler, "DDD Aggregate" and "Event Sourcing".
- Vernon IDDD ch 10 (and Appendix A) once the TOC is confirmed.
- Young, CQRS Documents ("Building an Event Storage": the two operations and the optimistic check; "Event Storage as a Queue").
- Lawrence ch 4 and ch 6.
- Microsoft Event Sourcing pattern.
- RFC 9110 (`If-Match`, 412).
- Quarkus guides: Hibernate ORM with Panache, SmallRye Fault Tolerance, Kafka (`@Blocking`, ack).
- Debezium outbox extension and Event Router (from r04 sources).
- Camel Exception Clause and redelivery.
- PostgreSQL unique constraints.
- Backward links to chapters 01, 08 and 11 (natural idempotence). Links to EIP Camel and CNDP only where they cover aggregates (1A checks; none expected).

### 15 Snapshots (`examples/15-snapshots`, `_docs/15-snapshots.md`)

**Headings.**

- `## Pattern: Snapshot`, with problem, solution and how Quarkus and Camel model it.
- `## What a rebuild costs`, with the measured numbers and the method.
- `## Inline or out of band`, the Camel comparison.
- Then build, run and observe; when not to use it; references.
- Front matter: `part: event-sourcing`.

**Service.** inventory-service on port 8080 with:

- quarkus-rest-jackson, hibernate-orm-panache, jdbc-postgresql and flyway;
- quarkus-smallrye-fault-tolerance (the hot-SKU retries);
- micrometer-opentelemetry and opentelemetry;
- camel-quarkus-scheduler, -sql and -bean (comparison).

Compose has postgres and lgtm only, **no Kafka**, the smallest stack that shows the pattern. `mem_limit` comes from S3 (512m expected).

**Schema (Flyway V1, schema `inventory`).**

- `inventory.events`: the same shape as chapter 14, plus append-only triggers.
- `inventory.snapshots`:
  - `stream_id text pk`, `version int not null`, `schema_version int not null`, `state jsonb not null`, `taken_at timestamptz`, `taken_by text`.
  - `taken_by` is a demo diagnostic, `inline` or `camel`.
  - Latest snapshot only. It is **not** append-only: snapshots are a cache.

**Aggregate.**

- `InventoryItemAggregate` (root) for one SKU.
- State: `sku`, `quantityOnHand`, `version`.
- Commands:
  - `receive(qty)` gives `StockReceived(qty)`;
  - `reserve(orderId, qty)` gives `StockReserved(orderId, qty)`, refused with 409 if `quantityOnHand < qty` (`InventoryItem` rule: never below 0).
- `snapshot()` gives `InventorySnapshot(sku, quantityOnHand)` with `SCHEMA = 1`.
- `static from(InventorySnapshot, version, tail)` rebuilds from a snapshot plus the tail.

**Load path (`StockLedger`).**

1. Read the snapshot row.
2. If it is absent, or `schema_version != SCHEMA`, ignore it (counted, logged `snapshot schema <n> ignored for <sku>`).
3. Stream `events` with `version > snapshot.version` in version order. This uses Panache `stream()` inside a transaction with a fetch size, not `list()`; S3 measures the heap.

A `LoadStats(snapshotVersion, eventsReplayed, loadNanos)` record is returned with the state. `GET /items/{sku}` exposes it as diagnostic fields. A demo query parameter `?ignoreSnapshot=true` exists only so the same JVM can measure full replay on the same data.

**Policy (main path, inline).** After a successful command, if `version - snapshotVersion >= N` (`inventory.snapshot.every=${SNAPSHOT_EVERY:100}`; 0 disables), upsert the snapshot in the **same transaction** with:

```sql
INSERT ... ON CONFLICT (stream_id) DO UPDATE SET ... WHERE excluded.version > snapshots.version
```

Reads never write. A stream bulk-loaded without snapshots gets its first snapshot at its first command, which is also when the cold full replay happens.

**Out-of-band comparison (profile `camel-snapshotter`).** `SnapshotterRoute` (Young's snapshotter):

```
from("scheduler:snapshotter?delay={{inventory.snapshotter.delay-ms}}")
  .to("sql:<streams whose head version minus snapshot version >= :#N>")
  .split(body())
  .bean(snapshots, "take")
```

`take` is its own `@Transactional` load-and-upsert with the same guard. Inline snapshots are off in this profile. One sentence notes that Quarkus `@Scheduled` does the same job; Camel is shown because a query, split and per-item call is a route.

**Hot-SKU contention.** `ReservationCommands` uses `@Retry(retryOn = ConcurrencyConflict.class)`, the same shape as chapter 14 and referred back to, not re-taught.

**Seeding (verify.sh, SQL `generate_series`).** The lengths are a recommendation (7):

- SKU-1501: 1,000 events.
- SKU-1502: 10,000 events.
- SKU-1503: 100,000 events.

Every tenth event is `StockReceived 10`, the rest `StockReserved 1` with order ids `O-15xxxxx`. The expected quantity is computed in SQL from the payloads. The seed is "bulk-loaded history"; a precondition proves the service reads it identically.

**Measurement method (stated in prose without versions).**

- **Timing.** `loadNanos` is measured inside the service around the database read, the JSON mapping and the fold. Per SKU and mode: 5 warm-up loads, then 21 timed loads, alternating snapshot and full mode to cancel drift; the median is reported. Machine class "8-core/16-thread laptop CPU" on the compose stack.
- **Deterministic numbers.** Events replayed come from `LoadStats`. `EXPLAIN (ANALYZE, BUFFERS)` row counts for the full and tail queries are NOTE only.
- **Per-event cost and N.** The full-replay medians at 1k, 10k and 100k give a per-event cost (slope). The prose uses it to state the rule: pick N so that N times the per-event cost fits the read budget.
- **Snapshot write cost.** A timer `inventory.snapshot.write`, reported as a NOTE.
- **Snapshot size.** `pg_column_size(state)` is recorded to show the state size is constant, which is why the policy counts events, not bytes.

**mvn tests.**

- Pure equivalence: for random streams and every split point k, `from(snapshotOf(fold(events[0..k])), k, events[k..])` equals `fold(events)`; for N in {1, 7, 100}.
- A snapshot with a stale schema is ignored and a full replay matches.
- Reads never write a snapshot.
- The guarded upsert never lowers the version (an older `take` after a newer one).
- An insufficient-stock reserve appends nothing and leaves the snapshot unchanged.
- 8 threads x 25 reserves on one SKU: the final quantity is correct and the conflict count is > 0 (probabilistic, so it is asserted >= 0 and reported).
- Camel profile: the scheduler writes a snapshot for a stream past N, with `taken_by=camel`.

**verify.sh (about 26 checks; numbering frozen after the window).**

Infra and seed:

1. P: inventory-service ready.
2. P: event counts 1,000 / 10,000 / 100,000; triggers present; `snapshots` empty.
3. P: GET SKU-1503 equals the SQL-computed quantity and version 100000, with `eventsReplayed=100000` and no snapshot (the seed is read identically).

Inline policy (N = `SNAPSHOT_EVERY`, default 100):

4. One reserve on SKU-1503 returns 201; the snapshot is at version 100001, schema 1, `taken_by=inline`.
5. GET SKU-1503: `eventsReplayed=0`, snapshot version 100001, quantity = expected - 1.
6. After N-1 more reserves the snapshot is still at 100001 and GET replays exactly N-1 events.
7. The Nth reserve moves the snapshot to 100001+N; GET replays 0.
8. For all three SKUs, GET equals GET `?ignoreSnapshot=true` (quantity and version).
9. SKU-1501 and SKU-1502 (no command) have no snapshot and replay 1,000 and 10,000 (control: only a command writes snapshots).

Measured:

10. For SKU-1503, median snapshot load times R is less than or equal to median full load, with R from S3 (recommendation 8). NOTE lines give the medians for 1k, 10k and 100k full and for the snapshot load, plus the EXPLAIN rows.
11. Prometheus holds the `inventory_rehydrate` timer with `source` tags and `service_name` (S3 confirms the exported name).

Snapshot is a cache:

12. After `DELETE FROM inventory.snapshots`, GET returns the same quantity and version with `eventsReplayed` = the full length.
13. The next reserve writes a snapshot again (the policy recovers).

Schema version:

14. Set the snapshot's `schema_version=0` (a deliberately altered copy): GET replays the full length and the state is unchanged.
15. The log or counter shows `snapshot schema 0 ignored for SKU-1503`.

Invariant from snapshot state:

16. A reserve larger than the quantity on hand returns 409; no event; snapshot unchanged.

Contention:

17. 200 reserves of 1 on SKU-1502 from 8 parallel clients all return 201. The final quantity equals the start minus 200, and versions are contiguous. A NOTE gives the conflict count.

Out of band (container recreated with profile `camel-snapshotter`, `SNAPSHOTTER_DELAY_MS=1000`):

18. P: the route has started.
19. During a burst of 3N reserves on SKU-1502, `taken_by` is never `inline`. The NOTE gives the maximum `eventsReplayed` sampled during the burst; the tail can exceed N.
20. Within 3 scheduler periods after the burst, the snapshot is within N of the head and `taken_by=camel`.
21. The snapshot version sampled every 200 ms during the burst never decreases.
22. GET equals GET `?ignoreSnapshot=true` for SKU-1502.
23. The command path wrote no snapshot in this phase (`inventory.snapshot.write{writer=inline}` unchanged).

LGTM:

24. Trace in Tempo.
25. Log in Loki.
26. The Prometheus counter for ignored snapshots is present.

**Experiments:**

- `SNAPSHOT_EVERY=0`: no snapshot is ever written. Expected failures: 4 to 7, 10, 13 (and 5 shows a full replay each time).
- `SNAPSHOT_EVERY=10` and `=1000`: all checks pass. NOTE the tail bound and the snapshot writes per 100 commands. A three-row table in prose (N, events replayed at most, snapshot writes per 1,000 commands, median load).
- `SNAPSHOT_SCHEMA=2` at restart (KEEP stack): the first load replays the full length, then the next command writes schema 2.
- A deliberately altered snapshot quantity (same schema): GET returns the wrong quantity. Check 8 fails. Lesson: a snapshot is trusted, so only the aggregate's own code writes it, and deleting it is always safe.
- `SNAPSHOTTER_DELAY_MS=30000`: the tail during a burst grows to the burst size (measured).
- Guard removed from the upsert: kept only if S4 shows a deterministic regression. Otherwise it stays an mvn test.

**Figures.**

- `15-snapshot-plus-tail`: stream 1..100,001+k; the snapshot at version s; a load reads 1 row plus the events after s, against a full read.
- `15-rebuild-cost`: a bar figure from the measured medians. The numbers are written into the spec after the window, so it regenerates byte-identically.
- `15-inline-vs-snapshotter`: two timelines (inline in the command transaction every N; scheduler lagging behind a burst).

**Callout.** Names `InventoryItemAggregate`, `StockLedger`, `SnapshotterRoute`, `InventorySnapshot.SCHEMA`. Rule: a snapshot is a cache of the fold at one version. Load the snapshot plus every later event, throw a snapshot away when its schema changes, and choose N from the measured per-event cost.

**Citations.**

- Young, CQRS Documents ("Rolling Snapshots" twice, the snapshotter, snapshot versioning).
- Fowler, Event Sourcing.
- Microsoft Event Sourcing pattern (1A confirms its snapshot paragraph).
- Lawrence ch 6.
- Quarkus Panache (`stream`), SmallRye Fault Tolerance.
- Camel Scheduler, SQL and Split.
- PostgreSQL `INSERT ... ON CONFLICT DO UPDATE ... WHERE`, `EXPLAIN`, `generate_series`.
- Backward references to chapters 01 ("Long streams need snapshots") and 14.

## Claims rows to plan (claims.md, status pending until evidence)

Chapter 14:

| id | Claim | Evidence |
|---|---|---|
| 14-a | The stream is the only order state; GET replays | verify 3, 10 |
| 14-b | The aggregate refuses illegal transitions; same payment twice gives no event; a second payment id is refused; amount mismatch is refused | aggregate unit tests; verify 9 |
| 14-c | Append at a stale expected version is rejected by the unique constraint | mvn; experiment "constraint removed" |
| 14-d | Retry re-decides: race 1 refused, race 2 accepted at v3 | verify 12 to 21; mvn interleaving |
| 14-e | Blind re-append produces CANCELLED after SHIPPED | experiment `RETRY_MODE=reappend`; mvn witness |
| 14-f | A stale `If-Match` gives 412 with no retry | verify 22, 23; RFC 9110 |
| 14-g | Only OrderPlaced and OrderCancelled reach Kafka, from an outbox row in the same transaction; a failed command leaves neither row | verify 5, 6, 11, 17, 21, 30; mvn outbox tests |
| 14-h | Re-reading every PaymentProcessed changes no stream | verify 28 |
| 14-i | Camel redelivery re-invokes the transactional bean and re-decides; without redelivery the command is dropped | verify 29; experiment `CAMEL_REDELIVERIES=0` |
| 14-j | Every `@Retry` attempt is a new transaction | S1, mvn |
| 14-src-* | Fowler aggregate; Young's two operations and the queue; Vernon chapter title; RFC 9110 `If-Match`/412; SmallRye FT `retryOn`; Camel exception clause | sources.md |
| 08-xx | The rewritten chapter 08 sentence | chapter 14 mvn outbox test |

Chapter 15:

| id | Claim | Evidence |
|---|---|---|
| 15-a | Snapshot plus tail equals full replay for any split | property test; verify 8, 22 |
| 15-b | A command writes a snapshot every N events; a load replays at most N-1 | verify 4 to 7 |
| 15-c | The measured medians (numbers) and per-event cost, with the method | the window logs (3 runs, the median of the medians reported, the range noted) |
| 15-d | Deleting snapshots changes only cost | verify 12, 13 |
| 15-e | A stale schema version is ignored and rebuilt | verify 14, 15; experiment `SNAPSHOT_SCHEMA=2` |
| 15-f | An altered snapshot is believed | experiment |
| 15-g | The out-of-band snapshotter lags a burst (measured maximum tail) and never lowers the version | verify 19 to 23 |
| 15-h | The state size is constant (bytes) | NOTE |
| 15-i | N=10 / 100 / 1000 table | experiments |
| 15-j | Contention on a hot SKU gives conflicts that the retry absorbs | verify 17; mvn |
| 15-src-* | Young (rolling snapshots, snapshotter, versioning); Microsoft; Camel scheduler/sql/split; PostgreSQL upsert guard | sources.md |

## Spikes (1B, scratchpad only, under the stack lock, Dev Services; about 90 minutes serial)

- **S1 (ch 14, Quarkus path):**
  - Map the unique violation to `ConcurrencyConflict` by constraint name inside `@Transactional` with `persistAndFlush`.
  - `@Retry` on `OrderCommands` around `OrderTransaction`: a new transaction per attempt, with a reload proved by a log.
  - Placement and once-only behaviour of `DemoPause`.
  - `Request.evaluatePreconditions(EntityTag)` in Quarkus REST, and how 412 composes with an append conflict.
  - Two `ExportedEvent` types with the same type parameters; the outbox row rolls back with a failed append.
  - SmallRye `@Blocking @Incoming` calling `OrderCommands`, and refusal acks.
  - The exported Prometheus names of the counters.
  - `@Retry` placed on the `@Transactional` method (record what happens).
- **S2 (ch 14, Camel path):**
  - Redelivery on `ConcurrencyConflict` re-invokes `OrderTransaction` in a new transaction and re-decides.
  - `handled(true)` on `CommandRefused` commits the offset.
  - Group reset `--to-earliest` replays every payment as no-ops.
  - `CAMEL_REDELIVERIES=0` behaviour.
- **S3 (ch 15, measurement):**
  - The SQL seed of 100k rows is readable by the Jackson polymorphic mapping (precondition 3).
  - `stream()` vs `list()` heap at 100k within the container limit.
  - Medians at 1k, 10k and 100k, and for snapshot loads, over 3 runs: variance, then the ratio threshold for check 10.
  - The guarded upsert SQL through a Panache native query.
  - The timer and counter export names.
  - Time for the 8-client contention burst.
- **S4 (ch 15, Camel snapshotter):**
  - `scheduler` plus `sql` with the named parameter, `split`, and `bean` per stream in its own transaction.
  - Monotonic snapshot version under concurrent commands.
  - The maximum tail observed during a 3N burst.
  - Whether removing the guard gives a deterministic regression.
- **S5 (tooling):** PaymentProcessed and ShipmentScheduled lines through the console producer (key = order id); reuse of the chapter 08 connector registration with the new slot name; `pg_stat_activity` or log-based pause preconditions.

## Steps

| Step | Work | Files | Owner | Depends | Parallel | Stack | Status |
|---|---|---|---|---|---|---|---|
| 0 | Preflight: branch, Docker clean, iteration-plan r06 in progress, save plan | `_plans/iteration-plan.md`, `_plans/r06-plan.md` | orchestrator | owner OK | none | no | done |
| 1A | Re-survey (complete the table above) and source fetches: Young (done here; record), Fowler bliki, Vernon TOC, RFC 9110, Microsoft snapshot paragraph, Quarkus/Camel/PostgreSQL pages; claim rows `*-src-*` | `_plans/versions.md`, `_plans/sources.md`, `bibliography.md` | exec 1A | 0 | 1B, 1C | no | done |
| 1B | Spikes S1 to S5 | scratchpad | exec 1B | 0 | 1A, 1C | lock, serial | done |
| 1C | `_plans/domain-model.md` rows 14 and 15 rewritten (below); event-name note for StockReceived/StockReserved | `_plans/domain-model.md` | exec 1C | 0 | 1A, 1B | no | done |
| G0 | Gate: spikes pass or fallback chosen; G0 decisions section appended to this plan | this plan | orchestrator | 1A to 1C | none | no | done |
| 2A | Ch 14: code, tests, Containerfile, compose, connector JSON, README, example page, figure specs and outputs, chapter draft without numbers | `examples/14-*/**`, `_docs/14-*.md`, `_example_pages/14-*.md`, `diagrams/specs/14-*.py`, `diagrams/14-*.*` | exec 2A | G0 | with 2B | mvn under lock | done |
| 2B | Ch 15: same set | `examples/15-*/**`, `_docs/15-*.md`, `_example_pages/15-*.md`, `diagrams/specs/15-*.py`, `diagrams/15-*.*` | exec 2B | G0 | with 2A | mvn under lock | done |
| 2C | Ch 08 sentence fix (recommendation 9) plus claim row | `_docs/08-transactional-outbox.md` | orchestrator | G0 | yes | no | done |
| 3 | Runtime windows, serial, exclusive, 90 min cap, order 15 then 14: verify N/N twice from clean, every experiment once, measurement three times (ch 15), no `edp-*` left; fill numbers; write measured values into `15-rebuild-cost.py`; freeze numbering | as 2A/2B | orchestrator | 2A, 2B | serial | exclusive | done |
| 4 | Orchestrator re-run of every prose experiment, logged with failing check numbers; grep "check N" after any renumbering | `_plans/verification-log.md` | orchestrator | 3 | serial | yes | done |
| 5 | Final serial run 00 to 15 (fresh containers each); static gates; claims and log; per-chapter commits with host_ids before each | claims, log | orchestrator | 4 | serial | yes | done |
| 6 | Opus validation (max 2 repair rounds): claims audit, check-bite audit, 7-gram originality vs Young's PDF text and chapter 01, fresh clone, word counts, figures byte-identical | none | validator | 5 | none | yes | done (round 2: SHIP) |
| 7 | Ship: PR, CI green, squash on owner OK, tag r06.0, notes-only release, delete branch; PRD rows 14 and 15 rewritten to the domain (freight names removed) and roadmap; iteration-plan done; CLAUDE.md decisions log entry | as listed | orchestrator | 6 | none | no | pending |

Executors:

- They make no git writes.
- They do not edit `claims.md`, `verification-log.md`, `CLAUDE.md`, `_infra`, `validate.py` or `domain-model`.
- Live stacks run only in 1B, the step 3 windows and steps 4 to 6, one at a time, under `stack-guard.sh` and the flock.
- Every verify.sh starts with `down -v --remove-orphans`, then `up -d --build --force-recreate --renew-anon-volumes --wait`, and ends with `down -v` unless `KEEP=1`.
- In-run profile switches use `up -d --no-deps --force-recreate --wait <svc>` (08 and 13 precedent).

**domain-model.md rows (1C):**

- **14:** "the `Order` aggregate rebuilt from its own stream in an append-only `events` table; commands from REST and from PaymentProcessed and ShipmentScheduled are checked against the replayed state; optimistic concurrency on `(stream_id, version)` with retry by re-deciding or `If-Match` reported to the caller; OrderPlaced and OrderCancelled published through the chapter 08 outbox in the same transaction, OrderPaid and OrderShipped stay internal; a Camel route delivering PaymentProcessed as the comparison".
- **15:** "an `InventoryItem` stock ledger (StockReceived, StockReserved) for one popular SKU with 100,000 events; a snapshot every N events in the command's transaction; rebuild cost measured with and without; a stale snapshot schema is discarded; a Camel scheduled snapshotter as the out-of-band comparison".

## Acceptance criteria

**14**:

- verify N/N, N >= 30, from clean twice; mvn green.
- Race 1 is refused after a re-decide and race 2 accepted, with pause preconditions.
- `RETRY_MODE=reappend` produces the illegal history and fails the named checks.
- A stale `If-Match` gives 412.
- Only OrderPlaced and OrderCancelled reach Kafka, from the outbox in the same transaction (an mvn rollback test).
- The Camel phase re-reads every payment with no stream change and re-decides the O-1406 conflict.
- No order state table.

**15**:

- verify N/N, N >= 24, from clean twice; mvn green.
- The snapshot-plus-tail equivalence property passes.
- Loads replay at most N-1 events with snapshots, and the full length without.
- The measured medians for 1k, 10k and 100k and the snapshot load are recorded three times, with the method in the log. The prose quotes one set with the method.
- Delete and stale-schema checks pass.
- The out-of-band phase shows lag and a monotonic version.
- The N table comes from the runs.

**Every chapter**:

- <= 2,500 prose words (target about 2,000, never padded); exactly one callout; >= 2 figures from specs.
- No dashes, versions, test names, self-reference, forward references or aphorisms.
- `part: event-sourcing`.
- UBI 10 Containerfile; env conventions; `/q/health/ready` healthcheck.
- LGTM traces, metrics and logs, with one Prometheus check.
- Topics only from `Topics`.
- Every claim row verified or cut, and every prose experiment re-run in step 4.

**Iteration**:

- validate ok; voice 0 ban-tier; idiomatic 0 high; host_ids 0; the site builds.
- Final serial 00 to 15 logged.
- Ch 08 sentence fixed with a claim row.
- CI green; tag and release; PRD (rows 14 and 15 in domain wording) and iteration plan updated.

## Sources to fetch and confirm (1A)

- Young, CQRS Documents (record page numbers: "Events as a Storage Mechanism" 25, "Rolling Snapshots" 33 and 44, "Building an Event Storage" 41, "Event Storage as a Queue" 46).
- Fowler, DDD Aggregate (confirmed 2026-11-12); Fowler, Event Sourcing (already confirmed).
- Vernon, IDDD TOC (owner paste or InformIT).
- Evans, *Domain-Driven Design*, only if the owner wants the original Aggregate reference.
- Microsoft Event Sourcing pattern: its snapshot paragraph.
- RFC 9110, sections 13.1.1 (If-Match) and 15.5.13 (412).
- Quarkus guides: Hibernate ORM with Panache (`stream`, native queries); SmallRye Fault Tolerance (`@Retry`, `retryOn`, config keys); Kafka (`@Blocking`, acknowledgement); REST (`Request`, `EntityTag`).
- Camel 4.22.x: Exception Clause, redelivery; Scheduler component; SQL component (named parameters); Split EIP.
- PostgreSQL 18: INSERT `ON CONFLICT DO UPDATE ... WHERE`; unique constraints; `generate_series`; `EXPLAIN`.
- Lawrence ch 4 and ch 6 (TOC rows exist).
- Kleppmann and Riccomini DDIA 2e: chapter title only if the owner supplies it.

## Risks

| Risk | Detection | Response |
|---|---|---|
| Pause-based races are flaky | S1, two consecutive N/N; P checks on logs and on the version existing while the request is open | longer pause (6 s), 60 s polls; the race moves to mvn only if live is not 3/3 |
| `@Retry` retries inside an aborted transaction | S1 | separate bean (r05 lesson); mvn proves a new transaction per attempt |
| `evaluatePreconditions` does not compose with the append conflict | S1 | manual tag comparison mapped to 412 |
| Constraint-name mapping differs (Hibernate wraps the exception differently) | S1 | map from `PersistenceException` cause chain by SQLState 23505 plus constraint name |
| Seeded jsonb does not match the Jackson mapping | precondition 3 | S3 fixes the seed SQL |
| 100k replay exhausts the heap or is too slow for the window | S3 | `stream()` with fetch size, `mem_limit` raised within the 7 GB budget, or 50k events with the prose numbers adjusted |
| Timing ratio noisy | S3, 3 runs | conservative R, or NOTE-only (recommendation 8); event counts stay the bite |
| Camel snapshotter conflicts with inline | profiles exclusive | inline off in `camel-snapshotter` |
| Word budget (14 has three topics) | draft count at step 2 | trim the Camel table to 4 rows; the pessimistic-lock sentence goes first |
| Overlap with chapter 01 | validator 7-gram vs ch 01, n-gram vs Young | refer back to 01 instead of repeating; Young paraphrased |
| Forward references (14 naming snapshots or projections, 15 naming upcasting) | validator read | backward only |
| Ch 08 sentence change ripples into claims | step 2C | new claim row |
| Unrun experiments in prose | step 4 | re-run all |
| Renumbering | frozen numbering plus grep | re-map |
| Stack contention | flock | serial windows only |

## Owner decisions (recommendations)

1. **Event store.** Plain PostgreSQL with Panache: `events` table per service schema, unique `(stream_id, version)`, `jsonb`, append-only triggers; no product or framework (Q3, PRD non-goal). The expected version comes from the loaded aggregate.
2. **Aggregate for 14.** `Order` as an OO root (`OrderAggregate`) with rehydrate, decide and apply. Commands from REST (place, cancel) and Kafka (PaymentProcessed gives paid, ShipmentScheduled gives shipped). The stream is the only state; no read table.
3. **Conflict policy.**
   - Server retry by reloading and re-deciding, through SmallRye FT `@Retry(retryOn = ConcurrencyConflict)` around a separate `@Transactional` bean (`RETRY_MAX` default 3).
   - `If-Match` reports 412 with no retry.
   - A refused command is a business outcome: 409 on REST, acked and logged on Kafka.
   - `RETRY_MODE=reappend` (demo code) is the negative witness.
   - Concurrency is induced with a labelled demo pause (`DEMO_PAUSE_FOR`), as with r05's crash switches.
4. **Publishing in 14.** Reuse the chapter 08 outbox (debezium-quarkus-outbox plus the Event Router) in the same transaction as the append, for the public events OrderPlaced and OrderCancelled only. Tailing the events table directly is rejected (it publishes internal events and makes the store schema public) and gets one sourced sentence.
5. **Camel in 14 and 15, at the edge.**
   - 14: `PaymentCommandRoute` with redelivery on conflict, against the SmallRye path on the same topic after a group reset.
   - 15: `SnapshotterRoute` (scheduler, sql, split, bean) as Young's out-of-band snapshotter, against inline snapshots.
   - One sentence on the Camel Aggregator name clash.
6. **Chapter 15 aggregate and events.** `InventoryItem` per SKU, with internal ledger events `StockReceived` and `StockReserved`. They are named apart from the public `InventoryReserved`. Recorded in domain-model.md row 15; no domain-model code change, which stays at 2.2.0.
7. **Snapshot policy and data.**
   - Every N events (`SNAPSHOT_EVERY`, default 100), written in the command's transaction with a version-guarded upsert. The latest snapshot only, in its own table, never written by reads.
   - Seeded streams of 1k, 10k and 100k events.
   - N=10 and 1000 as experiments.
   - The prose rule: choose N from the measured per-event cost.
8. **Measurement and what is checked.** Event counts (deterministic) are the live checks. The timing ratio is a check with a conservative factor R from S3 (only if 3/3 spike runs clear it by at least 4x R), otherwise NOTE-only. Medians are reported with the stated method, the machine class and no versions.
9. **Ch 08 fix now.** Replace "When the events are the state itself, event sourcing removes the second write instead of making it safe" with a backed sentence, for example: "When the events are the state itself, the event row is the state change, and the outbox row for a public event commits beside it." This adds claim row 08-xx, backed by the chapter 14 outbox test. Alternative: delete the sentence.
10. **Snapshot schema versioning.** A `schema_version` constant; a mismatch is discarded and rebuilt, never upcast (Young). No snapshot upcasting in r06.
11. **Citations.**
    - 14: Fowler (Aggregate, Event Sourcing), Young, Vernon IDDD (titles once confirmed), Lawrence ch 4 and 6, Microsoft, RFC 9110.
    - 15: Young, Fowler, Microsoft, Lawrence ch 6.
    - No Bellemare chapter. Kleppmann only with an owner-confirmed chapter title.
    - New bibliography rows: Young, Fowler DDD Aggregate, Vernon IDDD, RFC 9110.
12. **Headings.** 14 `## Pattern: Aggregate` plus `## Conflicts: retry or report` and `## Publishing from an event-sourced aggregate`. 15 `## Pattern: Snapshot` plus `## What a rebuild costs` and `## Inline or out of band`.
13. **Runtime window order.** 15 (PostgreSQL and LGTM only), then 14 (full stack with Connect).
14. **PRD and log at ship.** Rewrite PRD rows 14 and 15 to the order-to-delivery wording (they still say Consignment and TrackingHistory, which the validator bans). Add a CLAUDE.md decisions entry for r06.
15. **Example slugs.** `examples/14-aggregate-root-and-event-store` and `examples/15-snapshots`, with the same chapter slugs. Packages `com.patterncatalyst.shipping.aggregate` and `com.patterncatalyst.shipping.snapshots`.

### Critical Files for Implementation

- `examples/01-crud-vs-events/src/main/java/com/patterncatalyst/shipping/crudvsevents/OrderService.java` (what 14 builds on; refer back, do not duplicate)
- `examples/08-transactional-outbox/order-service/src/main/java/com/patterncatalyst/shipping/outbox/OrderPlacedEvent.java` and `examples/08-transactional-outbox/connect/order-outbox.json` (outbox reuse)
- `examples/13-retry-and-dead-letter/verify.sh` and `examples/13-retry-and-dead-letter/inventory-service/src/main/resources/application.properties` (verify helpers, preconditions, profile switching, `@Retry` config keys)
- `_docs/08-transactional-outbox.md` (line 125, sentence to fix)
- `_plans/domain-model.md` (rows 14 and 15)

---


## G0 decisions (orchestrator, 2026-11-12)

Spikes S1 to S5 all passed (report and logs in the session scratchpad, `r06-spikes/REPORT.md`; not committed). 1A re-survey: UBI openjdk-25 bumped repo-wide to 1.24-20; everything else unchanged. Vernon chapter titles confirmed from InformIT (ch 8 "Domain Events", ch 10 "Aggregates", Appendix A "Aggregates and Event Sourcing: A+ES"). Kleppmann and Riccomini, DDIA 2nd ed., cited as a book; chapter title unconfirmed until the owner pastes the TOC, so no chapter-level citation.

Corrections carried into 2A and 2B (they override the design sections above where they differ):

1. `DEMO_PAUSE_FOR` takes `orderId:Command`; a bare order id pauses the first command.
2. Both examples set `quarkus.hibernate-orm.mapping.format.global=ignore` (as example 01).
3. Constraint mapping: catch `PersistenceException`, walk the cause chain for `ConstraintViolationException` with constraint name `events_stream_version` (no multi-catch). `@Retry` lives on `OrderCommands` around a separate `@Transactional` bean; `@Retry` on the transactional method itself fails deterministically (`current transaction is aborted`) and may stay as the optional experiment.
4. `@Consumes(JSON)` only on POSTs with a body (class-level gives 415 on body-less POSTs). `Request.evaluatePreconditions(EntityTag)` gives the 412 path.
5. Camel redelivery logs need `.logRetryAttempted(true).retryAttemptedLogLevel(LoggingLevel.INFO)` (check 29); route properties are `commands.camel.*`.
6. Ch 15 replay reads through a Panache projection `.project(Row.class).stream()` (record `Row(StockEvent payload)`); plain entity `stream()` does not bound heap (Hibernate keeps entities in the persistence context; OOM at Xmx 96m), `list()` OOMs at 128m. This is a prose-worthy, tested fact (claim row).
7. Ch 15 reserve path `@Retry(maxRetries=30, jitter=30)` (8 retries lost 1 to 6 of 200 under 8 clients). Config keys `<fqcn>/<method>/Retry/maxRetries`.
8. Seed: version 1 is `StockReceived 100`, every tenth event `StockReceived 10`, the rest `StockReserved 1`; expected quantities 201, 1101, 10101 for 1k, 10k, 100k (+ the stated tails).
9. Check 19 (Camel snapshotter phase) deletes the inline snapshot rows first (or uses a fresh SKU).
10. Check 10 timing ratio: R=20 on SKU-1503 (100k) only; spike medians 267 ms full vs 1.9 ms snapshot, ratio 140 to 148 in 3/3 runs (7x margin). 1k and 10k ratios are reported, not checked.
11. Metric names: `order_command_conflicts_total`, `order_command_refused_total`, `ft_retry_retries_total`; `inventory_rehydrate_milliseconds_*{source}`, `inventory_snapshot_write_milliseconds_*{writer}`, `inventory_snapshot_ignored_total` (exists only after first increment). Metric checks are live only (OTel SDK off in tests).
12. Snapshot upsert through `EntityManager.createNativeQuery` with `cast(?4 as jsonb)`, guard `WHERE excluded.version > inventory.snapshots.version`; the stale-take guard experiment is live through a labelled demo endpoint.
13. Outbox connector reuses chapter 08's config with slot and publication `edp_order_aggregate_outbox`.
14. Console producer: `kafka-console-producer.sh --property parse.key=true --property key.separator=|`; the lag helper treats `-` offsets of an inactive group.

Note on G0-1 (validator L7): G0-1 records a spike finding, not a feature. `DEMO_PAUSE_FOR` accepts only `orderId:Command`; no bare-order-id form is implemented or documented.

## Validation round 1 (Opus, 2026-11-12)

3 High, 9 Medium, 10 Low. Live 4/4 green (14 33/33, 15 27/27, mvn 26 and 13), all gates green. High: ch 15 snapshot timings quoted from a second, inconsistent bench (H1); Figure 15.2 one constant for three lengths (H2); three ch 15 experiments cited as final-run evidence but never in the final run (H3). Repair round 1 split by file ownership: ch 15 executor; ch 14 + ch 08 + bibliography executor; orchestrator for claims, log and plan (M2, M6, L7).
