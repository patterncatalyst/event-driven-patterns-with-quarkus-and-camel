---
layout: plan
title: r07 plan
render_with_liquid: false
---

# r07 plan: chapters 16 to 18, CQRS and projections, aggregate replay or projection, schema evolution and upcasting (Opus planner, 2026-11-12)

Status: **approved by the owner 2026-11-12, all 19 recommendations**; merge when validation and CI pass (owner). Branch `release/r07`, cut from main at 0a54b1f (r06.0).

Planner note: this plan was made with read-only commands, Maven Central metadata, the quay.io and Red Hat catalog APIs, the GitHub releases API, code.quarkus.io, the Notion PRD and web fetches of the Leanpub page for Young's versioning book, the Apicurio 3.1.x rule reference, the Debezium Outbox Event Router page and the Quarkus Schema Registry and Avro guide. No stack was started, no container was run, nothing was written and no git command changed state.

## Findings

### Repo state that binds this iteration

- **Apicurio is in the stack but no example uses it yet.** `examples/_infra/compose-apicurio.yaml` runs `quay.io/apicurio/apicurio-registry:3.3.4` (in-memory H2, empty after every restart; host 8081, health 19000). Chapter 00 checks it is healthy. Chapter 18 is the first chapter that puts schemas in it. No example pom has an Apicurio dependency.
- **Serde version gap.** The Quarkus platform 3.40.1.2 manages `apicurio-registry-avro-serde-kafka`, `-java-sdk` and the serde commons at **3.1.7**. Central has **3.3.4** for each (serde, Java SDK, Maven plugin), matching the server. versions.md already flags this ("verify serde against the 3.3.4 server at first use"). G0 decision 12.
- **Avro** 1.12.2 is managed by the platform and is the newest on Central.
- **Event payload records live per example.** Chapter 03 has its own `OrderPlaced(orderId, Order, OrderStatus, Claim, attachment)`. Chapter 14 has `OrderEvent` (sealed: `OrderPlaced`, `OrderPaid`, `OrderShipped(shipmentId, carrier)`, `OrderCancelled(reason)`), `PaymentProcessed(Payment)` and `ShipmentScheduled(Shipment)`. Chapters 16 to 18 define their own copies.
- **domain-model needs no code change.** `Topics` has every catalog topic chapters 16 and 18 use (`ORDERS_PLACED`, `ORDERS_CANCELLED`, `PAYMENTS_PROCESSED`, `SHIPPING_SCHEDULED`, `SHIPPING_IN_TRANSIT`, `SHIPPING_DELIVERED`). `OrderStatus`, `ShipmentStatus` (PENDING < SCHEDULED < IN_TRANSIT < DELIVERED), `Carrier` and `Shipment` exist. `shippingPriority` is a payload field of OrderPlaced v2, not an entity, so its values (STANDARD, EXPRESS) live in the chapter 18 Avro schema and in a domain-model.md note. domain-model stays 2.2.0.
- **The validator bans `Manifest`, `Vessel`, `PortCall`, `HandlingEvent`, `TrackingHistory`, `Consignment`** and more (`BANNED_DOMAIN` in `scripts/validate.py`). PRD rows 16 to 18 still use them. Never copy PRD wording. Rewrite the PRD rows at ship, as r06 did for 14 and 15. The word "manifest" must not appear in chapter 17, even in lower case in identifiers (the regex is case-sensitive on `Manifest`, but avoid it entirely).
- **Domain rows in `_plans/domain-model.md`:**
  - 16: "an order-status read model per customer in PostgreSQL, projected from order and shipment events; read lag asserted"
  - 17: "shipments per carrier per day (FASTLINE vs AIRCO) by aggregate replay and by a new projection; decision table"
  - 18: "OrderPlaced v1 to v2 (adds a `shippingPriority` field with a default); Apicurio compatibility rule; upcaster on read"
  - Appendix A row: "event contract tests (schema compatibility in Apicurio, after ch 18)". Chapter 18's registry test is the contract test the appendix will point to at r13. No appendix work in r07.
- **Earlier prose that r07 must stay consistent with:**
  - Ch 01 line 107: "Every reader of the current state either replays a stream or maintains a second, derived copy that can lag." Chapter 16 and 17 make this concrete; no change.
  - Ch 01 line 107: "Events are permanent contracts: a payload shape written today must still be readable after the code changes." Chapter 18 makes it concrete; no change.
  - Ch 15 line 178: "When the read side is hot, a projection is the better cache." Step 2D checks its claims row. If it has no evidence, it gets the chapter 17 numbers or is reworded.
  - Ch 03's `order_copy` is a read model kept from events. Chapter 16 may refer back to it in one sentence.
  - Ch 14 rejected tailing the events table with Debezium for publication. Chapter 17 projects inside the owning service from its own store, which is consistent with that.
- **Part card** `_parts/03-event-sourcing.md` already says "projections, CQRS read models, snapshots and schema evolution". `part: event-sourcing` for all three chapters.
- **Housekeeping:** r06-plan.md step 7 still says `pending` although r06 shipped. Step 0 sets it to done.

### r06 lessons carried into r07 (verification log, validation rounds 1 and 2)

- **One bench, values per size, run before any check mutates state.** In r06, H1 was timings quoted from a second, inconsistent bench, and H2 was a figure with one constant for three lengths. Chapter 17's decision table and figure come only from the one bench in `verify.sh`, 3 runs from fresh containers, reported as the median of the run medians with the range.
- **Every experiment the prose cites runs in the final run or a logged repair re-run** (r06 H3).
- **Checks that fail unstably under an experiment are not named in prose** (r06 check 17 under `SNAPSHOT_EVERY=0`).
- **Preconditions keep an experiment from cascading.** Add them before the window so failing sets are stable.
- **Large reads use a Panache projection** (`.project(Row.class).stream()`), never entity `stream()` or `list()`. Entity streaming kept 100k entities in the persistence context.
- **`@Retry` wraps a separate `@Transactional` bean.**
- **Restarted consumers** use `session.timeout.ms=10000`, and polls after a restart wait up to 60 s.
- **Console producer:** one call per intended batch, `--property parse.key=true --property key.separator=|`, plus `parse.headers` for headers. Keep record lines only.
- **Shell:** never `grep -q` under pipefail.
- **One Prometheus check per example with `service_name`.** OTel is off in tests, so metric checks are live only.
- **Pause and hold preconditions** are read from the logs or `pg_stat_activity` before the competing input is sent.
- **Figures:** spec docstring, caption and printed labels agree with the measured numbers (r06 round 2).
- **Check numbering is frozen after the window.** Grep "check N" in prose after any renumbering.

### Versions re-survey (preliminary, 2026-11-12; 1A completes and records in versions.md)

| Item | Found | Source | Change |
|---|---|---|---|
| quarkus-bom / quarkus-camel-bom | 3.40.1.2 is still the newest final and the `recommended`, `lts` stream. 4.0.0.CR1 (2026-11-11) is a prerelease and is not used | https://repo1.maven.org/maven2/io/quarkus/platform/quarkus-bom/maven-metadata.xml ; https://code.quarkus.io/api/streams | none |
| Camel Quarkus | 3.40.0 (2026-09-28) is the newest | https://repo1.maven.org/maven2/org/apache/camel/quarkus/camel-quarkus-kafka/maven-metadata.xml | none |
| camel-bom | 4.22.2 is the newest 4.22.x (LTS); 4.23.0 is not LTS | https://repo1.maven.org/maven2/org/apache/camel/camel-bom/maven-metadata.xml | none |
| Kafka | kafka-clients 4.4.0 exists. The broker stays held at 4.3.2: Strimzi 1.3.1 (2026-11-04) is still the latest final | https://api.github.com/repos/strimzi/strimzi-kafka-operator/releases | none, hold re-recorded |
| Apicurio Registry server | 3.3.4 (2026-11-03). The quay digest sha256:3ee4423c0f21... equals the pin. Only snapshot and `-mutable` tags are newer | https://quay.io/api/v1/repository/apicurio/apicurio-registry/tag/ ; https://api.github.com/repos/Apicurio/apicurio-registry/releases | none |
| Apicurio serde, Java SDK, Maven plugin | Central 3.3.4. The platform manages 3.1.7 | https://repo1.maven.org/maven2/io/apicurio/apicurio-registry-avro-serde-kafka/maven-metadata.xml ; local quarkus-bom 3.40.1.2 pom | **G0-12**: override to 3.3.4 (camel-bom precedent) if spike S4 passes, else hold at 3.1.7 with the reason |
| Avro | 1.12.2 (platform equals latest) | https://repo1.maven.org/maven2/org/apache/avro/avro/maven-metadata.xml | none |
| debezium-quarkus-outbox / Connect image | 3.7.1.Final. Quay `3.7.1.Final` digest sha256:05fcb3ca2160... (last_modified 2026-11-12 01:31 UTC) equals the r06 record. `3.8`, `latest` and `3.8.0.Alpha1` are prerelease builds and are not used | https://quay.io/api/v1/repository/debezium/connect/tag/ | none; 1A re-reads the digest |
| ubi10/openjdk-25, -runtime | 1.24-20 is still `latest` and `1.24` (catalog creation 2026-11-12 12:58 UTC) | Red Hat catalog API | none |
| Not yet checked (1A) | CNPG operand 18.x timestamped tag; library/postgres 18.6; otel-lgtm; Temurin 25 patch; Maven #57216; the SmallRye Reactive Messaging version in the platform (pausable channels); the Dev Services Apicurio default image (it must be pinned to 3.3.4) | as r06 | |

No version appears in the 16 to 18 prose.

### What the sources say (confirmed in this session)

- **Young, *Versioning in an Event Sourced System*** (Leanpub, last updated 2017-04-09, pay-what-you-want with a minimum price; https://leanpub.com/esversioning).
  - Only the table of contents is readable without buying: Why Version?; Why can't I update an event?; Basic Type Based Versioning; Weak Schema; Negotiation; General Versioning Concerns; Whoops, I Did It Again; Copy and Replace; Cheating; Internal vs External Models; Versioning Process Managers.
  - The body was not fetched, and the word "upcasting" was not confirmed in it.
  - **Cite by author, title and chapter titles only, for the topic each title names** (Vernon precedent). No content claim rests on it. Owner may supply the text.
- **Apicurio Registry content rule reference** (3.1.x page):
  - Compatibility values: FULL, FULL_TRANSITIVE, BACKWARD (a new-version reader reads data written with the most recent version), BACKWARD_TRANSITIVE (with any previous version), FORWARD, FORWARD_TRANSITIVE, NONE.
  - Compatibility checking is supported for Avro, Protobuf, JSON Schema, OpenAPI and XSD.
  - Rules exist at artifact and global level, and an artifact rule overrides the global one.
  - The HTTP status on a violation is not on that page; 1A finds it in the 3.3.x REST docs, and S4 observes it.
- **Debezium Outbox Event Router, "Using Apache Avro as the payload format":**
  - The payload column is binary (`bytea` on PostgreSQL), and `value.converter=io.debezium.converters.BinaryDataConverter` passes the bytes through unchanged.
  - How the application produces the Avro bytes is out of scope for Debezium. A registry serializer applied before the insert is the documented option.
  - Heartbeat and schema change events need a delegate converter.
- **Quarkus "Using Apache Kafka with Schema Registry and Avro":**
  - `quarkus-apicurio-registry-avro` provides the serde, and `src/main/avro/*.avsc` is compiled to classes.
  - Serde classes are autodetected for SmallRye channels.
  - Dev Services starts Apicurio in dev and test.
  - `apicurio.registry.auto-register=true` registers the schema on an outgoing channel.
  - The specific Avro reader is configured automatically.
  - The page says nothing about compatibility rules.
  - The registry 2.x and 3.x id formats cannot be mixed on one topic. Not relevant here: the registry is empty at every run.

## Approach

Same gate shape as r06:

1. G0: re-survey, source fetches and spikes, in parallel.
2. Three parallel authoring executors on disjoint files, with `mvn verify` serialized under the stack lock.
3. Serial, exclusive runtime windows (17, then 16, then 18).
4. The orchestrator re-runs every prose experiment.
5. Final serial run 00 to 18.
6. Opus validation (at most 2 repair rounds), then ship.

One example per chapter (house pattern). Each chapter teaches one read-side or contract decision and refers back to 14 and 15 for the event store instead of re-teaching it.

- **16** projects public events from **Kafka** (three owners: order, payment, shipping) into an order-status read model per customer. It shows **read lag** as a deterministic, visible staleness and as a measured latency, and **cross-topic order** guarded by a monotonic upsert.
- **17** answers one question (shipments per carrier per day) **by replaying every `Order` aggregate from the store** and **by a new projection** built from position 0 of the same store. It measures both on **one bench at three sizes**. Its negative witness is the commit-order gap a naive position checkpoint falls into.
- **18** evolves **OrderPlaced v1 to v2**:
  - In the **event store**, an upcaster runs on read: rows are never rewritten, and the append-only trigger refuses a rewrite.
  - **On Kafka**, Avro is used with Apicurio. Reader-schema resolution fills the default, so no hand-written upcaster runs on consume.
  - A **BACKWARD_TRANSITIVE** rule refuses an incompatible v3, both at the registry API and at command time. The command's transaction rolls back.

**Rejected alternatives (one line each):**

- **Ch 16 projecting from the event store table** (in-process poller): only the owner's own events, and it brings the commit-order gap into a chapter about lag. It is the right tool in 17 instead.
- **Ch 16 projecting through Debezium CDC of the events table:** it adds Connect and an internal topic for a view that the public topics already feed. One sentence in "When not to use it" points to it for views that need internal events.
- **Ch 16 with a copied ch 14 command side (aggregate, outbox, Connect):** it doubles the example for no new lesson. The write side is chapter 14, referred back.
- **Last-writer-wins upserts:** they regress status across topics. Kept only as the labelled negative witness `PROJECTION_GUARD=off`.
- **Counters in the read model:** they would need the chapter 11 inbox. Status columns with guarded upserts are idempotent without it, and summaries are computed by query.
- **Ch 17 on Kafka (group reset as the rebuild):** an aggregate replay needs the store. Using one source for both answers makes the bench fair. Kafka retention as a rebuild limit is one decision-table row.
- **Ch 17 ad hoc SQL over jsonb payloads as a third answer:** it couples readers to the stored payload shape. It is used only as the verify.sh oracle and is not taught.
- **Ch 17 extending the ch 14 example:** it breaks one-example-per-chapter and ch 14's frozen check numbering.
- **Ch 17 store tail without a watermark:** it silently loses events that commit out of position order. It is the negative witness, not the design.
- **Ch 18 migrating stored payloads in place (UPDATE):** the store is append-only and the trigger refuses it. Copy-and-replace into a new store gets one sentence (Young chapter title).
- **Ch 18 JSON Schema instead of Avro:** Apicurio checks JSON Schema compatibility, but the JSON serde has no reader-schema resolution. The consumer would then need its own upcaster for the default, which is the hand-rolled path. The PRD pins the Avro serde.
- **Ch 18 publishing with a SmallRye Emitter after the append:** a dual write (chapter 08).
- **Ch 18 Avro through Connect's Apicurio AvroConverter on a JSON outbox payload:** Connect would derive the schema from the row, so the contract would not be the hand-written `.avsc`.
- **Ch 18 upcasting snapshots:** chapter 15 discards stale snapshots, and no snapshot exists here.
- **Event-sourcing or projection frameworks (Axon, Marten-style libraries, Kurrent):** PRD non-goal or licence.

## Per-chapter design

### 16 CQRS and projections (`examples/16-cqrs-and-projections`, `_docs/16-cqrs-and-projections.md`)

**Headings.**

- `## Pattern: CQRS`, with `### The problem`, `### The solution` (bold one-sentence definition of a read model kept apart from the write model and fed by its events) and `### How Quarkus and Camel model it`.
- Then `## Read lag`, `## Order across topics`, `## Build, run, observe`, `## When not to use it`, `## References`.
- Front matter: `part: event-sourcing`.

**Opening.** The `Order` aggregate in chapter 14 answers "may this command happen" by replaying one stream. A customer asks something else: "where are all my orders", across payment and shipping facts the aggregate never sees. One backward sentence names chapter 14 as the write side.

**Service.** order-service (query side only), port 8080, package `com.patterncatalyst.shipping.projections`, with:

- quarkus-rest-jackson, hibernate-orm-panache, jdbc-postgresql and flyway;
- quarkus-messaging-kafka;
- micrometer-opentelemetry and opentelemetry;
- camel-quarkus-kafka, -jackson and -bean (comparison).

Compose includes kafka, postgres, lgtm and topics-init, which creates the six topics with 1 partition each. No Connect and no Apicurio.

**Schema (Flyway V1, schema `orders`).** One table, `orders.order_view`:

- `order_id text pk`;
- `customer_id text null`, indexed;
- `item_sku`, `amount`;
- `order_status text` (PLACED or CANCELLED, from order-service);
- `payment_status` (from payment-service);
- `shipment_status`, `carrier`, `tracking_number` (from shipping-service);
- `last_event_at timestamptz` (the record timestamp of the last applied event);
- `updated_at`.

No events table, because the read side keeps no write model.

**Code.**

- **Payload records** (per example): `OrderPlaced(Order)`, `OrderCancelled(orderId, reason)`, `PaymentProcessed(Payment)`, `ShipmentScheduled(Shipment)`, `ShipmentInTransit(Shipment)`, `ShipmentDelivered(Shipment)`. Each has a Jackson deserializer class as in 14.
- **`OrderViewProjector`** (`@ApplicationScoped`, `@Transactional` methods):
  - One method per event.
  - Each is a single native upsert that sets only the columns its owner owns.
  - Shipment status uses a rank guard: `ON CONFLICT (order_id) DO UPDATE SET ... WHERE rank(excluded.shipment_status) > rank(order_view.shipment_status)`, with the rank taken from `ShipmentStatus` ordinal through a SQL `CASE` or array position.
  - CANCELLED is terminal for `order_status`.
  - An event for an unknown order inserts a partial row (no customer yet).
- **`OrderView`** (Panache entity, read only), plus a `status()` derived as an `OrderStatus`:
  - CANCELLED if `order_status` is CANCELLED;
  - otherwise DELIVERED, SHIPPED (shipment SCHEDULED or IN_TRANSIT), PAID or PLACED, by the furthest fact.
- **`ProjectionChannels`**: six SmallRye `@Incoming` methods, `@Blocking`, calling the projector. Groups `order-view-placed`, `-cancelled`, `-paid`, `-scheduled`, `-in-transit`, `-delivered`; `session.timeout.ms=10000`; `pausable=true` (S1). The record timestamp comes from `IncomingKafkaRecordMetadata`, and a Micrometer timer `order.view.lag` records `now - record timestamp` at commit.
- **`OrderViewResource`**:
  - `GET /orders/{id}` returns the view with `status`, `shipmentStatus`, `carrier` and `asOf` (= `last_event_at`).
  - `GET /customers/{customerId}/orders` returns the list.
  - Reads never touch Kafka.
- **`DemoChannels`** (labelled demo code): `POST /demo/channels/{name}/pause|resume` and `GET /demo/channels` through SmallRye `ChannelRegistry` pausable channels (S1). Fallback: `KafkaClientService.getConsumer(channel).pause()`.
- **`ProjectionGuard`** (demo): `PROJECTION_GUARD=off` drops the rank guard (last writer wins). This is the negative witness.
- **`ProjectorDelay`** (demo): `PROJECTOR_DELAY_MS` sleeps per event, so lag can be shown as arrival rate against throughput.
- **`OrderViewRoute`** (profile `camel-projector`, comparison): one Camel Kafka consumer on all six topics (`kafka:edp.orders.placed,edp.orders.cancelled,...?groupId=order-view-camel`), `choice()` on the `kafka.TOPIC` header, `unmarshal().json(...)`, `.bean(projector, ...)`. In that profile the six SmallRye channels are disabled. The comparison point: one consumer and one group with one poll loop over six topics, against six channels with six groups. Both write through the same guarded projector.

**mvn tests (Dev Services: PostgreSQL 18.6, Kafka 4.3.2), at least 10:**

- the guarded upsert keeps DELIVERED when IN_TRANSIT arrives later, and `ProjectionGuard` off regresses it (asserts the bad outcome);
- the same event applied twice leaves an identical row;
- an event before OrderPlaced creates a partial row, which joins the customer list once OrderPlaced arrives;
- CANCELLED is terminal;
- `status()` derivation for every combination;
- an end-to-end Kafka-to-view test per topic;
- a pause leaves the row stale and resume converges;
- the Camel profile builds the same view from the same records;
- the lag timer records a positive value.

**verify.sh (about 29 checks; numbering frozen after the window; "P" marks a precondition).**

Infra:

1. P: service ready; the six groups have partition 0 assigned.
2. P: the six topics exist with 1 partition each.
3. Schema `orders` holds only `order_view` and the Flyway history: no events table, no write model.

Lifecycle (O-1601, customer C-1601, FASTLINE). Each step is produced by one console-producer call in the owner's record format (key = order id, `id` and `eventType` headers):

4. OrderPlaced: `GET /orders/O-1601` is PLACED within 10 s, with the customer and amount.
5. PaymentProcessed gives PAID.
6. ShipmentScheduled gives SHIPPED, with carrier FASTLINE and the tracking number.
7. ShipmentInTransit gives SHIPPED, with shipment IN_TRANSIT.
8. ShipmentDelivered gives DELIVERED.
9. `GET /customers/C-1601/orders` lists O-1601 as DELIVERED and no order of another customer.

Cancellation:

10. O-1602 placed then cancelled gives CANCELLED.

Read lag (O-1603 placed, paid and scheduled first):

11. P: the `delivered` channel reports paused.
12. After ShipmentDelivered O-1603 is produced, group `order-view-delivered` has lag 1.
13. Five reads over 5 s all return SHIPPED, with `asOf` earlier than the produced record's timestamp. The staleness is visible to the reader.
14. After resume, DELIVERED within 10 s and lag 0.
15. Prometheus `order_view_lag_seconds_max{service_name="order-service"}` is at least the pause length (S1 confirms the name and the window). This is the example's one Prometheus check.

Order across topics (O-1604):

16. P: the `in-transit` channel is paused.
17. ShipmentScheduled and ShipmentDelivered are produced, then ShipmentInTransit; the view shows DELIVERED.
18. After resume and lag 0, the view still shows DELIVERED and shipment DELIVERED. The guard keeps the later state.

Event before its order (O-1605, C-1605):

19. P: the `placed` channel is paused. After ShipmentScheduled O-1605, the row shows shipment SCHEDULED with no customer, and `GET /customers/C-1605/orders` is empty.
20. After resume, O-1605 is listed under C-1605 as SHIPPED.

Burst:

21. 250 orders (O-16100 to O-16349), one producer call per topic (1,250 records): every row reaches its expected final status within 60 s, and every group has lag 0. NOTE: the median and p95 of `order.view.lag` from the service.
22. p95 lag is below B seconds. B is set from S1 with at least a 4x margin over 3 spike runs; NOTE-only if it is not stable.

Replay is harmless:

23. Capture the view hash (rows ordered, timestamps excluded). Stop the service, reset all six groups `--to-earliest`, start it again. After lag 0 the hash is unchanged.

Camel comparison (service recreated with `COMMAND_PROFILE=camel-projector` after `DELETE FROM orders.order_view`):

24. P: the route has started; group `order-view-camel` has the six partitions assigned.
25. After lag 0, the view hash equals the SmallRye hash from check 23.
26. O-1604 is DELIVERED on the Camel path too (the shared guarded projector).

LGTM:

27. Trace in Tempo.
28. Log in Loki.
29. The read model was never written by a request: the Micrometer counter of REST writes is absent. Optional; drop it if S1 shows no clean signal.

**Experiments (each run in step 3 and re-run in step 4, failing checks recorded):**

- `PROJECTION_GUARD=off`: IN_TRANSIT overwrites DELIVERED. Expected failures: 18 and 26 (the main negative witness).
- `PROJECTOR_DELAY_MS=50`: the burst outruns the projector, so lag grows with the backlog. Expected failure: 22 (and possibly 21, at 60 s). The NOTE gives the measured p95.

**Figures.**

- `16-command-query-split`: three owners' write models publish to six topics; the projector applies them to `order_view`; queries read only the view. The lag window is marked between the topic and the view.
- `16-read-lag-window`: a timeline. The event is produced, the channel is paused, reads return SHIPPED with an older `asOf`, then resume and DELIVERED. The lag metric records the gap.
- `16-cross-topic-order`: DELIVERED arrives before IN_TRANSIT. With the guard the view stays DELIVERED; without it, it regresses.

**Camel comparison table, short.** Rows:

- consumers and groups (six channels and six groups, against one route and one group);
- where lag is read (per channel, against one figure for six partitions);
- pausing one topic (a pausable channel, against stopping the whole route);
- the projector bean (shared).

**Callout.** Names `OrderViewProjector`, `ProjectionChannels`, `OrderViewRoute` and `order_view`. Rule: a read model answers from what it has applied; make every update an idempotent upsert that only moves forward, let each column come from its owner, and give the reader `asOf` so lag is visible.

**Citations.**

- Young, CQRS Documents (the CQRS section; 1A records the pages).
- Fowler, "CQRS" bliki (1A fetches).
- Microsoft Azure Architecture Center, CQRS pattern and Materialized View pattern (1A fetches).
- Lawrence ch 5 "CQRS" and ch 6 "Event Sourcing".
- Bellemare ch 16 "Eventual Consistency" and ch 7 "Denormalization and Eventification".
- Quarkus Kafka guide; SmallRye pausable channels page; Camel Kafka component (multiple topics); PostgreSQL `INSERT ... ON CONFLICT DO UPDATE ... WHERE`; Kafka consumer group tool.
- Backward links to 03 (`order_copy`), 04 (order within a partition only), 11 (idempotent consumer) and 14 (the write side).

### 17 Aggregate replay or CQRS projection (`examples/17-aggregate-replay-or-projection`, `_docs/17-aggregate-replay-or-projection.md`)

**Headings.**

- `## Pattern: Projection rebuilt from history`, with problem, solution and `### How Quarkus models it` (no Camel; G0-13).
- `## The same question, two ways`.
- `## What each answer costs` (the bench, the method, the table).
- `## Commit order is not position order` (the watermark).
- Then build, run and observe; when not to use it; references.
- Front matter: `part: event-sourcing`.

**Opening.** Operations asks a question nobody planned for: how many orders FASTLINE and AIRCO shipped each day. The events hold the answer. Either replay every `Order` aggregate on request, or build a projection now from the whole history and keep it current.

**Service.** order-service, port 8080, package `com.patterncatalyst.shipping.replay`, with:

- quarkus-rest-jackson, hibernate-orm-panache, jdbc-postgresql and flyway;
- quarkus-scheduler;
- micrometer-opentelemetry and opentelemetry.

Compose includes **postgres and lgtm only**: no Kafka, the smallest stack that shows the decision. `mem_limit` comes from S2.

**Schema (Flyway V1, schema `orders`).**

- `orders.events`: the chapter 14 shape (unique `(stream_id, version)`, append-only triggers) plus `tx_id xid8 NOT NULL DEFAULT pg_current_xact_id()`, indexed `(tx_id, position)`.
- `orders.carrier_daily_shipments`: `carrier`, `day`, `shipments`; pk `(carrier, day)`.
- `orders.projection_checkpoint`: `name` pk, `last_tx_id xid8`, `last_position bigint`, `updated_at`.

The projection tables are not append-only: they are a derived cache.

**Code.**

- **`OrderEvent`** (sealed: `OrderPlaced`, `OrderPaid`, `OrderShipped(shipmentId, carrier)`, `OrderDelivered`, `OrderCancelled`) and **`OrderAggregate`**: the minimal copy of the chapter 14 rehydrate, with commands `place`, `pay`, `ship` and `cancel`. The prose refers back to 14 and does not re-teach it.
- **`EventStore`**: append (as 14) and `streamAll()`, a Panache projection stream ordered by `(stream_id, version)`, so one aggregate is folded at a time with constant memory (ch 15 lesson).
- **`ReplayReport`**: folds every stream and counts `shippedAt` day and carrier for aggregates that reached SHIPPED or DELIVERED. It returns `{rows, eventsRead, millis}`.
- **`CarrierDailyProjector`**:
  - `@Scheduled(every = "${projection.poll:0.2s}", concurrentExecution = SKIP)`.
  - Each tick is one `@Transactional` batch: lock the checkpoint row (`SELECT ... FOR UPDATE`, so a second replica cannot double count; one sentence), then read up to `${projection.batch:1000}` events.
  - The read is `WHERE (tx_id, position) > (:lastTx, :lastPos) AND tx_id < pg_snapshot_xmin(pg_current_snapshot()) ORDER BY tx_id, position`.
  - It upserts `shipments = shipments + n`, advances the checkpoint, and commits. Rows and checkpoint commit together, so a crash repeats nothing and loses nothing.
  - It reloops at once while batches are full.
- **`ProjectionWatermark`** (demo): `PROJECTION_WATERMARK=off` reads `WHERE position > :lastPos ORDER BY position`. This is the negative witness.
- **`DemoHold`** (demo): `DEMO_HOLD_FOR=<orderId>:<Command>` holds the command's transaction open for `DEMO_HOLD_MS` after its append, before commit. Same style as the chapter 14 `DemoPause`.
- **`ReportResource`**:
  - `GET /reports/shipments-per-carrier?source=replay|projection` returns rows plus `lagEvents` (events after the checkpoint, projection only) and timing.
  - `POST /orders/{id}/shipment` and the other commands, for the live checks.
- **`DemoBench`** (demo): `GET /demo/bench?warm=3&runs=11` alternates replay and projection reads, and returns medians and event counts. `POST /demo/projection/rebuild` deletes the projection rows and the checkpoint in one transaction and returns, so catch-up is timed from there.

**Seeding (verify.sh, SQL `generate_series`, deterministic).** Three cumulative sizes: 1,000, then +9,000, then +90,000 orders (100,000 in total). Each order is OrderPlaced and OrderPaid, then:

- 90% get OrderShipped, with the carrier by a fixed hash (about 60% FASTLINE, 40% AIRCO) and the day spread over 30 days;
- 60% get OrderDelivered;
- 10% get OrderCancelled from PLACED or PAID.

That is about 3.4 events per order, about 340k events at 100k. The SQL oracle groups the OrderShipped payloads, excluding orders that were cancelled. Order ids are `O-17xxxxxx`.

**Measurement method (stated in prose without versions).**

- Per size, measured before any mutating check: replay median and projection-read median (3 warm-up loads, 11 timed, alternating), rebuild time (reset to `lagEvents=0`), events read, and the projection size (`pg_total_relation_size`) against the events size.
- Three runs from fresh containers; the median of the run medians is reported with the range.
- Machine class: "8-core laptop" (claim 15-w precedent).
- Freshness: the median time from a command's commit to the projection reflecting it, over 50 commands (NOTE).

**mvn tests, at least 10:**

- replay equals a hand-computed answer on a small fixed history;
- the projection equals replay after catch-up;
- a failure inside a batch rolls back both the rows and the checkpoint;
- a restart resumes from the checkpoint with no double count;
- **the deterministic gap**, with a latch hook in place of `DemoHold`: tx A appends at position p and stays open; tx B appends at p+1 and commits; the projector runs.
  - With the watermark, neither is counted until A commits, then both are.
  - With the watermark off, B is counted, A never is. That test asserts the bad outcome.
- `FOR UPDATE` on the checkpoint serializes two concurrent ticks;
- a cancelled order is not counted;
- `streamAll` folds 100k seeded events under a small heap (S2 sets the -Xmx).

**verify.sh (about 24 checks).**

1. P: service ready.
2. P: 1,000 orders seeded (event count, triggers present); projection empty, no checkpoint.

At each size (1k: checks 3 to 5; 10k after the SQL append: 6 to 8; 100k: 9 to 11):

- replay equals the SQL oracle;
- the projection reaches `lagEvents=0` within T (from S2);
- projection equals replay.

NOTE lines give the bench values for that size.

12. At 100k, the median projection read is at least R times faster than the median replay (R from S2, at least 4x margin over 3 spike runs; NOTE-only otherwise).
13. Rebuild at 100k: P the rebuild started; a sample taken during catch-up has `lagEvents > 0` and totals below replay; after catch-up it equals replay. NOTE: the rebuild time.
14. Live: ShipOrder O-1700001 with AIRCO. The next replay includes it; the projection includes it within 2 s. NOTE: freshness median over 50 commands.

Commit-order gap:

15. P: log `held ShipOrder O-1701 after append`, and `pg_stat_activity` shows the app connection `idle in transaction`.
16. ShipOrder O-1702 commits while O-1701 is held. The projection does not move past the held transaction: `lagEvents >= 2`, and neither order is counted.
17. After the hold ends, projection equals replay (both counted).

Checkpoint survives a restart:

18. `docker restart` the service after catch-up. After `lagEvents=0`, projection equals replay (no double count).

Size and cost:

19. The projection holds at most 60 rows (2 carriers x 30 days) at every size. NOTE: bytes against events bytes.

LGTM:

20. Prometheus `projection_lag_events{service_name="order-service"}` (or the name S2 finds); the one Prometheus check.
21. Trace in Tempo.
22. Log in Loki.

**Experiments:**

- `PROJECTION_WATERMARK=off`: the projection moves past O-1701 and never counts it. Expected failures: 16, 17. This is the main negative witness.
- `PROJECTION_POLL=5s`: freshness becomes the poll period. Expected failure: 14. The NOTE gives the median. This backs the "freshness" row of the table.
- `PROJECTION_BATCH=100`: a slower rebuild, measured, NOTE only. It is kept in prose only if the difference is stable across 3 runs.

**Decision table in prose.** Every number comes from the one bench in the final runs:

| | Replay every aggregate | Projection |
|---|---|---|
| Read at 1k / 10k / 100k orders | measured medians | measured medians |
| Freshness | the committed state at the read | poll period plus batch (measured) |
| A new question | code only, answers at once | code plus a rebuild (measured at 3 sizes) |
| Storage | none | rows (constant here) |
| What can go wrong | read cost grows with the store | lag, the commit-order gap, a projector bug repeated until a rebuild |

**Figures.**

- `17-replay-vs-projection`: one store, two read paths.
- `17-cost-by-size`: a bar figure from the measured medians (replay and projection read per size, rebuild per size). The numbers are written into the spec after the window, so it regenerates byte-identically. Labels and caption match the prose table.
- `17-commit-order-gap`: tx A at position 41 and tx B at 42; B commits first; a position checkpoint passes 41 forever, while the xmin watermark waits for A.

**Callout.** Names `ReplayReport`, `CarrierDailyProjector`, `projection_checkpoint` and `PROJECTION_WATERMARK`. Rule: replay when the question is rare or the history is small enough for the read budget; project when it is asked often. A projection rebuilds from position 0, and its checkpoint advances only past transactions that can no longer commit.

**Citations.**

- Young, CQRS Documents (events as the storage mechanism; the read side built from events).
- Fowler, Event Sourcing (rebuilding state by re-running events).
- Bellemare ch 9 "Deterministic Stream Processing" (reprocessing).
- Lawrence ch 6.
- Kleppmann and Riccomini, DDIA 2nd ed., at book level.
- PostgreSQL 18: Transaction ID and Snapshot Information Functions (`pg_current_xact_id`, `pg_snapshot_xmin`, `xid8`); `SELECT ... FOR UPDATE`; `generate_series`.
- Quarkus scheduler guide (`concurrentExecution`); Panache projection `stream`.
- Backward links to 14, 15 (projection versus snapshot: a snapshot serves one aggregate's load) and 16.

### 18 Schema evolution and upcasting (`examples/18-schema-evolution-and-upcasting`, `_docs/18-schema-evolution-and-upcasting.md`)

**Headings.**

- `## Pattern: Upcaster` (stored events), with problem, solution and how Quarkus and Camel model it.
- `## Pattern: Compatible schema evolution` (the wire contract in Apicurio), with problem, solution and how.
- `## Refusing an incompatible version`.
- Then build, run and observe; when not to use it; references.
- Front matter: `part: event-sourcing`.

**Opening.** Customers can now ask for express delivery. OrderPlaced gains `shippingPriority`. Two kinds of reader already hold the old shape:

- the aggregate, which loads OrderPlaced v1 rows that can never be rewritten;
- every consumer of `edp.orders.placed`, which may replay from the start.

**Services (aggregator pom, two modules).**

- **order-service** (port 8080, package `com.patterncatalyst.shipping.evolution`): the event store (chapter 14 shape plus a `schema_version smallint NOT NULL` column, append-only triggers), the upcaster chain, and the chapter 08 outbox with an **Avro `bytea` payload**. Extensions:
  - quarkus-rest-jackson, hibernate-orm-panache, jdbc-postgresql and flyway;
  - debezium-quarkus-outbox and quarkus-apicurio-registry-avro (Avro classes from `src/main/avro/order-placed.avsc`);
  - micrometer-opentelemetry and opentelemetry.
- **notification-service** (port 8082, package `com.patterncatalyst.shipping.evolution.notification`): a SmallRye `@Incoming` of the generated `OrderPlaced` class (v2 reader schema, autodetected Apicurio deserializer) recording `orderId` and `shippingPriority` in `notifications.notification`. Comparison (profile `camel-reader`): `OrderPlacedReaderRoute`, a Camel Kafka consumer with the Apicurio deserializer and registry URL configured explicitly (Quarkus autodetection applies to SmallRye channels only; S5 confirms), feeding the same recorder bean.
- Compose includes kafka, postgres, connect, **apicurio** and lgtm, plus topics-init (`edp.orders.placed`, 1 partition). Connector `order-outbox` is copied from chapter 08 with slot and publication `edp_order_schema_outbox` and `value.converter=io.debezium.converters.BinaryDataConverter` (S4).

**Code (order-service).**

- **`OrderPlaced`** stored payload records:
  - `OrderPlacedV1(order)`, a demo record for the previous release;
  - `OrderPlaced(order, shippingPriority)`, the current one.
- **`Upcasters`**: an ordered chain of `JsonNode` transforms keyed by `schema_version` (1 to 2: add `shippingPriority: "STANDARD"`), applied before the typed Jackson mapping. An unknown version fails loudly. The stored row is never written.
- **`EventStore.load`** upcasts, then rehydrates. **`append`** always writes the current `schema_version`.
- **`OrderPlacedPublisher`**:
  - Inside the command's `@Transactional`, it serializes the Avro record with the Apicurio `AvroKafkaSerializer` (TopicIdStrategy, giving artifact `edp.orders.placed-value`; `auto-register=true`; the schema id in the payload, not in headers; S4).
  - It fires the outbox `ExportedEvent` with the bytes.
  - A registry refusal throws, so the append and the outbox row roll back together.
- **`ReleaseSchema`** (labelled demo code): `ORDER_PLACED_SCHEMA=1|2|3` makes the service behave as an earlier or a broken release.
  - 1 writes `OrderPlacedV1` rows (`schema_version` 1) and Avro GenericRecords from `order-placed-v1.avsc`.
  - 2 is the default and writes the current shape.
  - 3 writes GenericRecords from `order-placed-v3.avsc`, where `shippingPriority` has **no default**.
- **`UPCASTERS=off`** (demo): skips the chain. This is the negative witness.
- **`OrderResource`**: `POST /orders` (body with an optional `shippingPriority`), `GET /orders/{id}` (replayed, with `shippingPriority`), `POST /orders/{id}/cancellation`. A registry refusal maps to 503 with the registry's error class in the body (S4 decides the status).

**Registry rule.** verify.sh sets the global COMPATIBILITY rule `${REGISTRY_RULE:-BACKWARD_TRANSITIVE}` through the REST API before the first producer runs. A precondition reads it back. S4 picks global or group/artifact level for 3.3.4; global is the fallback. The prose says the rule lives in the registry, set by whoever owns the contract, not in the producer.

**Why v3 is the right bad example.**

- Removing the default from `shippingPriority` is still BACKWARD compatible with v2: a v3 reader reads v2 data, because the field is present.
- It is not compatible with v1: a v3 reader cannot fill the missing field.
- So BACKWARD accepts it and BACKWARD_TRANSITIVE refuses it.
- The harm is real for any reader that rebuilds from the start of the topic (chapter 17's rebuild, referred back).

**mvn tests (Dev Services: PostgreSQL, Kafka, Apicurio 3.3.4 image pinned), at least 12 across both modules.**

order-service:

- upcaster v1 to v2 gives STANDARD; v2 passes through unchanged; an unknown version fails;
- the upcaster default equals the Avro v2 schema default (read from the `.avsc`);
- a mixed v1/v2 stream rehydrates;
- `UPDATE` of a stored payload is refused by the trigger;
- the outbox row's payload starts with the Apicurio id and decodes to the v2 record;
- registry with BACKWARD_TRANSITIVE: v1 and v2 accepted, v3 refused;
- registry with BACKWARD: v3 accepted;
- Avro resolution with no registry: a v2 reader on v1 bytes gives STANDARD, and **a v3 reader on v1 bytes throws `AvroTypeException`** (backs the prose sentence for the BACKWARD experiment);
- a command under schema 3 leaves no event row and no outbox row.

notification-service:

- reads v1 and v2 records into STANDARD and EXPRESS;
- the Camel route reads the same.

**verify.sh (about 24 checks).**

1. P: both services ready; connector `order-outbox` RUNNING; the topic exists with 1 partition; the registry is ready (19000 health).
2. P: the global COMPATIBILITY rule reads back as `REGISTRY_RULE`.

Previous release (order-service recreated with `ORDER_PLACED_SCHEMA=1`):

3. Placing O-1801 and O-1802 returns 201. Their stored rows have `schema_version` 1 and no `shippingPriority`.
4. Artifact `edp.orders.placed-value` has exactly 1 version, without `shippingPriority`.
5. The topic holds 2 records whose embedded schema id resolves to version 1 (decoded with python from the magic byte and id).

Current release (recreated with the default):

6. Placing O-1803 with EXPRESS returns 201 and writes a `schema_version` 2 row; the artifact has 2 versions.
7. The topic holds 3 records; the third resolves to version 2.

Store:

8. `GET /orders/O-1801` returns STANDARD at version 1 (upcast on read).
9. `GET /orders/O-1803` returns EXPRESS.
10. Cancelling O-1801 returns 200. The v1 row's payload checksum is unchanged before and after (upcasting never writes).
11. An `UPDATE` of O-1801's stored payload is refused by the append-only trigger.

Wire:

12. notification-service recorded O-1801 and O-1802 as STANDARD and O-1803 as EXPRESS; the group has lag 0.
13. No deserialization failure is logged.

Refusal:

14. A REST registration of v3 is refused (409 or the status S4 records), and the body names the compatibility rule; the artifact still has 2 versions.
15. With order-service recreated with `ORDER_PLACED_SCHEMA=3`, `POST /orders` O-1804 fails. There is no event row and no outbox row for O-1804, the topic still holds 3 records, and the artifact still has 2 versions.
16. The service log shows the registry refusal for O-1804.

Camel comparison (notification-service recreated with profile `camel-reader`, group `notification-camel` from earliest):

17. P: the route has started.
18. After lag 0, the Camel path recorded the same three priorities.

LGTM:

19. Prometheus `order_upcasts_total{from="1",service_name="order-service"}` is at least 2 (S4 confirms the name); the one Prometheus check.
20. Trace in Tempo.
21. Log in Loki.

**Experiments:**

- `UPCASTERS=off`: loading a v1 OrderPlaced fails. Expected failures: 8, 10, 19 (and 9 passes, which the prose says).
- `REGISTRY_RULE=BACKWARD`: v3 is registered and published. Expected failures: 14, 15, 16. The prose adds, backed by the mvn Avro test, that a reader built on v3 cannot read the v1 records already on the topic.

**Figures.**

- `18-upcast-on-read`: a v1 row and a v2 row in the store, through the upcaster chain, to one `OrderPlaced`, to the aggregate. The store is unchanged and an UPDATE is refused.
- `18-registry-versions`: v1 and v2 accepted; v3 refused under BACKWARD_TRANSITIVE and accepted under BACKWARD. The v2 reader resolves v1 records with the default.
- `18-outbox-avro-bytes`: the command transaction serializes through the registry, writes the event row and the outbox `bytea` row, then the Event Router and BinaryDataConverter, then the topic and the consumer.

**Callout.** Names `Upcasters`, `OrderPlacedPublisher`, `order-placed.avsc` and `REGISTRY_RULE`. Rule: stored events are upcast on read and never rewritten; published events evolve only by changes the registry accepts under a transitive rule; a field added later always has a default.

**Citations.**

- Young, *Versioning in an Event Sourced System* (chapter titles: "Why can't I update an event?", "Basic Type Based Versioning", "Weak Schema", "Copy and Replace").
- Lawrence ch 8 "Evolution of Messages and Message Streams".
- Bellemare ch 4 "Schemas and Data Contracts".
- Kleppmann and Riccomini, DDIA 2nd ed., at book level.
- Apicurio Registry docs: rule reference, serde configuration, REST API v3.
- Apache Avro specification, "Schema Resolution" (1A fetches).
- Quarkus "Kafka, Schema Registry and Avro" guide.
- Debezium Outbox Event Router, "Using Apache Avro as the payload format".
- Camel Kafka component.
- Backward links to 01 ("permanent contracts"), 08 (outbox), 14 (store) and 17 (rebuild re-reads every version).

## Claims rows to plan (claims.md, status pending until evidence)

Chapter 16:

| id | Claim | Evidence |
|---|---|---|
| 16-a | The read side keeps no write model; queries read only `order_view` | verify 3 |
| 16-b | Each column is set only by its owner's event | mvn; verify 4 to 10 |
| 16-c | A paused projector leaves the view stale while the lag is visible (group lag, `asOf`) | verify 11 to 14 |
| 16-d | The lag metric records the staleness | verify 15 |
| 16-e | A guarded upsert keeps DELIVERED when IN_TRANSIT arrives later; without the guard it regresses | verify 18; experiment `PROJECTION_GUARD=off` |
| 16-f | An event before its order creates a partial row that joins the customer list later | verify 19, 20 |
| 16-g | Re-reading every topic from the start leaves the view unchanged | verify 23 |
| 16-h | The burst's lag numbers (method stated) | verify 21, 22 NOTE (3 runs) |
| 16-i | One Camel consumer over six topics builds the same view | verify 24 to 26 |
| 16-j | Lag grows when arrival outruns the projector | experiment `PROJECTOR_DELAY_MS` |
| 16-src-* | Young CQRS; Fowler CQRS; Microsoft CQRS and Materialized View; SmallRye pausable channels; Camel multi-topic consumer; PostgreSQL upsert WHERE | sources.md |

Chapter 17:

| id | Claim | Evidence |
|---|---|---|
| 17-a | Replay and projection give the oracle's answer at every size | verify 3 to 11 |
| 17-b | The measured medians, rebuild times and sizes (one bench, 3 runs) | the window logs, final run |
| 17-c | A projection read is at least R times faster at 100k | verify 12 |
| 17-d | During a rebuild the reader can tell the projection is behind | verify 13 |
| 17-e | Freshness is the poll period | verify 14; experiment `PROJECTION_POLL=5s` |
| 17-f | A position checkpoint loses an event that commits after a later position; the xmin watermark waits | verify 15 to 17; experiment; mvn gap test |
| 17-g | Rows and checkpoint commit together: no double count after a restart or a failed batch | verify 18; mvn |
| 17-h | Replay ordered by stream folds 100k orders with a small heap | mvn (S2 heap) |
| 17-src-* | PostgreSQL `pg_snapshot_xmin`, `xid8`, `FOR UPDATE`; Quarkus scheduler `concurrentExecution`; Bellemare ch 9 title; Young | sources.md |
| 15-x | Ch 15 "a projection is the better cache when the read side is hot", backed or reworded | 17-b, 17-c |

Chapter 18:

| id | Claim | Evidence |
|---|---|---|
| 18-a | v1 rows load as v2 with STANDARD; the stored row never changes | verify 8, 10; mvn |
| 18-b | The store refuses an in-place rewrite | verify 11; mvn |
| 18-c | Without the upcaster a v1 OrderPlaced cannot be loaded | experiment `UPCASTERS=off` |
| 18-d | A v2 reader reads v1 Avro records with the default | verify 12; mvn |
| 18-e | BACKWARD_TRANSITIVE refuses v3 at the registry and the command rolls back with no outbox row | verify 14 to 16; mvn |
| 18-f | BACKWARD accepts v3, and a v3 reader cannot read v1 records | experiment `REGISTRY_RULE=BACKWARD`; mvn Avro test |
| 18-g | The outbox carries Avro bytes unchanged through BinaryDataConverter | verify 5, 7; Debezium source |
| 18-h | The Camel reader needs the deserializer configured; SmallRye autodetects it | S5; mvn; Quarkus guide |
| 18-src-* | Apicurio rule meanings; Avro schema resolution; Debezium Avro payload; Young chapter titles; Lawrence ch 8; Bellemare ch 4 | sources.md |

## Spikes (1B, scratchpad only, under the stack lock, Dev Services first; about 150 minutes serial)

- **S1 (ch 16):**
  - SmallRye pausable channels (`pausable=true`, `ChannelRegistry`): pause and resume; offsets stay uncommitted while paused; the group lag is read by `kafka-consumer-groups.sh`. Fallback: `KafkaClientService`.
  - Six `@Incoming @Blocking` methods into one `@Transactional` projector.
  - The guarded upsert through `EntityManager.createNativeQuery`.
  - `IncomingKafkaRecordMetadata` timestamp.
  - The timer export name and whether its max survives long enough for check 15.
  - Camel single route on six topics with `kafka.TOPIC` choice.
  - Steady-state lag over 3 runs, which sets B.
- **S2 (ch 17):**
  - `xid8` default and the watermark query through a native query (cast for Java); xid assignment at the first write keeps per-stream order.
  - `DemoHold` after the insert inside the transaction, with the `pg_stat_activity` precondition.
  - The gap reproduced deterministically with the watermark off and closed with it on.
  - `@Scheduled(concurrentExecution = SKIP)` with the checkpoint `FOR UPDATE`.
  - Seed SQL time for 100k orders.
  - Replay heap (`-Xmx`) with the stream-ordered projection.
  - Bench medians at three sizes over 3 runs, which set R and T.
  - The exported metric name.
- **S3 (ch 18 store):** the `JsonNode` upcaster before typed mapping; the `schema_version` column; the `ORDER_PLACED_SCHEMA=1` release switch writing v1 rows and v1 GenericRecords.
- **S4 (ch 18 wire):**
  - Apicurio serde 3.1.7 (platform) and 3.3.4 (override) against the 3.3.4 server, in Dev Services and on compose (G0-12).
  - The Dev Services image property pinned to 3.3.4.
  - `AvroKafkaSerializer` used standalone, with the id in the payload.
  - debezium-quarkus-outbox with a `byte[]` payload and a `bytea` column (`quarkus.debezium-outbox.payload.*`). Fallback: a Panache entity on the same outbox table.
  - The Event Router plus BinaryDataConverter in the published Connect image.
  - The global versus artifact COMPATIBILITY rule via REST v3.
  - v3 refusal: HTTP status, body, the serializer exception, and the transaction rolled back.
  - BACKWARD accepts v3.
  - The specific reader resolving v1 to v2.
- **S5 (ch 18 Camel):** the Camel Kafka consumer with the Apicurio deserializer, registry URL and specific reader.
- **S6 (tooling):**
  - Console-producer lines for the six ch 16 payloads with headers.
  - Lag helpers for six groups, and the group reset while the service is stopped.
  - The Apicurio REST calls through host port 8081.
  - Memory: `docker stats` peak for the ch 18 stack (Kafka, PostgreSQL, Connect, Apicurio, LGTM and two apps).

## Steps

| Step | Work | Files | Owner | Depends | Parallel | Stack | Status |
|---|---|---|---|---|---|---|---|
| 0 | Preflight: branch, Docker clean, iteration-plan r07 in progress, r06-plan step 7 set to done, save this plan | `_plans/iteration-plan.md`, `_plans/r06-plan.md`, `_plans/r07-plan.md` | orchestrator | owner OK | none | no | done |
| 1A | Re-survey (complete the table) and source fetches: Fowler CQRS, Microsoft CQRS and Materialized View, Young CQRS Documents CQRS pages, Avro schema resolution, Apicurio 3.3.x rules, REST and serde pages, PostgreSQL xid functions, SmallRye pausable channels, Quarkus scheduler; bibliography rows (Young versioning, Fowler CQRS, Microsoft CQRS and Materialized View, Avro spec); `*-src-*` rows | `_plans/versions.md`, `_plans/sources.md`, `bibliography.md` | exec 1A | 0 | 1B, 1C | no | done |
| 1B | Spikes S1 to S6 | scratchpad | exec 1B | 0 | 1A, 1C | lock, serial | pending |
| 1C | `_plans/domain-model.md` rows 16 to 18 rewritten (below); note on `shippingPriority` values and customer ids `C-16xx` | `_plans/domain-model.md` | exec 1C | 0 | 1A, 1B | no | done |
| G0 | Gate: spikes pass or a fallback is chosen; serde pin decided; "G0 decisions" section appended to this plan | this plan | orchestrator | 1A to 1C | none | no | pending |
| 2A | Ch 16: code, tests, Containerfile, compose, README, example page, figure specs and outputs, chapter draft without numbers | `examples/16-*/**`, `_docs/16-*.md`, `_example_pages/16-*.md`, `diagrams/specs/16-*.py`, `diagrams/16-*.*` | exec 2A | G0 | with 2B, 2C | mvn under lock | pending |
| 2B | Ch 17: same set, plus `seed.sql` | `examples/17-*/**`, `_docs/17-*.md`, `_example_pages/17-*.md`, `diagrams/specs/17-*.py`, `diagrams/17-*.*` | exec 2B | G0 | with 2A, 2C | mvn under lock | pending |
| 2C | Ch 18: same set, two modules, connector JSON, `.avsc` v1/v2/v3 | `examples/18-*/**`, `_docs/18-*.md`, `_example_pages/18-*.md`, `diagrams/specs/18-*.py`, `diagrams/18-*.*` | exec 2C | G0 | with 2A, 2B | mvn under lock | pending |
| 2D | Earlier-chapter consistency: ch 15 line 178 claim backed (17-b) or reworded; grep 01, 03, 14 and 15 for sentences 16 to 18 contradict; claims rows drafted | `_docs/15-snapshots.md` (only if needed), `_plans/claims.md` | orchestrator | G0 | yes | no | pending |
| 3 | Runtime windows, serial, exclusive, 90 min cap each, order 17, 16, 18. Verify N/N twice from clean (17: three baseline runs for the bench); every experiment once; numbers filled; measured values written into `17-cost-by-size.py`; numbering frozen | as 2A to 2C | orchestrator | 2A to 2C | serial | exclusive | pending |
| 4 | Orchestrator re-run of every prose experiment, logged with the failing check numbers; grep "check N" after any renumbering | `_plans/verification-log.md` | orchestrator | 3 | serial | yes | pending |
| 5 | Final serial run 00 to 18 (fresh containers each, one flock, no `edp-*` left between runs); static gates; claims and log; per-chapter commits with `host_ids.py check` before each | claims, log | orchestrator | 4 | serial | yes | pending |
| 6 | Opus validation (max 2 repair rounds): claims audit; check-bite audit; bench consistency (the table, the figure and the prose from one bench); 7-gram originality against Young's CQRS Documents text, the Apicurio and Debezium pages and chapters 14 and 15; fresh clone; word counts; figures byte-identical | none | validator | 5 | none | yes | pending |
| 7 | Ship: PR, CI green, squash on owner OK, tag r07.0, notes-only release, delete the branch; PRD rows 16 to 18 rewritten to the domain (freight names removed), roadmap and status; iteration-plan done; CLAUDE.md decisions entry | as listed | orchestrator | 6 | none | no | pending |

Executors:

- They make no git writes.
- They do not edit `claims.md`, `verification-log.md`, `CLAUDE.md`, `examples/_infra`, `validate.py` or `domain-model`.
- `mvn verify` runs only under `flock -w 7200 <scratchpad>/stack.lock`.
- Live stacks run only in 1B, the step 3 windows and steps 4 to 6, one at a time, under `stack-guard.sh` and the flock.
- Every verify.sh starts with `docker compose down -v --remove-orphans`, then `up -d --build --force-recreate --renew-anon-volumes --wait`, and ends with `down -v` unless `KEEP=1`.
- In-run profile and release switches use `up -d --no-deps --force-recreate --wait <svc>`.
- No host identifiers in any file: `<repo>` and `~/` only. Captured logs are filtered with `host_ids.py filter` before anything is quoted.

**domain-model.md rows (1C):**

- **16:** "an order-status read model per customer in PostgreSQL (`order_view`), projected by order-service's query side from OrderPlaced, OrderCancelled, PaymentProcessed, ShipmentScheduled, ShipmentInTransit and ShipmentDelivered on their catalog topics; each column set by its owner's event; read lag made visible (`asOf`, group lag) and asserted with a paused channel; out-of-order arrival across topics guarded by a forward-only upsert; a single Camel consumer over the six topics as the comparison".
- **17:** "shipments per carrier per day (FASTLINE vs AIRCO) answered by replaying every `Order` aggregate from an append-only `events` table and by a new projection rebuilt from position 0 of the same table, measured at 1,000, 10,000 and 100,000 orders; the projection's checkpoint advances only below the oldest open transaction; decision table".
- **18:** "OrderPlaced v1 to v2 adds `shippingPriority` (STANDARD or EXPRESS, default STANDARD); stored v1 events upcast on read and never rewritten; the published event as Avro in Apicurio through the outbox, read by notification-service with the v2 schema; a BACKWARD_TRANSITIVE rule refuses a v3 that drops the default".
- Note: STANDARD and EXPRESS are values of a payload field, not an entity. No domain-model code change; the version stays 2.2.0.

## Acceptance criteria

**16**:

- verify N/N, N >= 26, from clean twice; mvn green with >= 10 tests.
- Stale reads under a paused channel are asserted (checks 11 to 14), and the lag metric is present in Prometheus.
- `PROJECTION_GUARD=off` fails check 18 (and 26) and no other check, in both the step 3 and step 4 runs.
- `PROJECTOR_DELAY_MS=50` fails check 22.
- Replay from the earliest offsets leaves the view hash unchanged.
- The Camel view hash equals the SmallRye hash.

**17**:

- verify N/N, N >= 20, from clean three times (the bench runs); mvn green with >= 10 tests, including the deterministic gap test, which asserts the bad outcome with the watermark off.
- Replay equals projection equals the SQL oracle at 1k, 10k and 100k.
- The bench values per size (replay median, projection median, rebuild, bytes) are recorded in all three runs with the method. The prose table and `17-cost-by-size` quote the median of the run medians from those same runs only.
- `PROJECTION_WATERMARK=off` fails checks 16 and 17.
- `PROJECTION_POLL=5s` fails check 14.
- Ch 15 line 178 is backed or reworded.

**18**:

- verify N/N, N >= 20, from clean twice; mvn green with >= 12 tests across both modules.
- v1 rows load as STANDARD and are byte-identical after a command.
- The v2 consumer and the Camel reader read v1 and v2 records.
- v3 is refused at the REST API and at command time, with no event row, no outbox row, no record and no new version.
- `UPCASTERS=off` fails checks 8, 10 and 19.
- `REGISTRY_RULE=BACKWARD` fails checks 14 to 16.
- The mvn Avro test proves a v3 reader fails on v1 bytes.
- The serde pin decision is recorded in versions.md with its reason.

**Every chapter**:

- Prose words <= 2,500 by `validate.py` (target about 2,000, never padded); exactly one "Explore with your agent" callout in the house form; >= 2 figures from specs (outputs never hand-edited, regenerated byte-identically).
- No em or en dashes, versions, test names, self-reference, forward references, aphorisms or "deliberate(ly)".
- `part: event-sourcing`.
- UBI 10 Containerfile (`1.24-20`) per the `Dockerfile.jvm` convention; env conventions (`KAFKA_BOOTSTRAP_SERVERS`, `CAMEL_COMPONENT_KAFKA_BROKERS`, `QUARKUS_DATASOURCE_*`, `QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT`, `MP_MESSAGING_CONNECTOR_SMALLRYE_KAFKA_APICURIO_REGISTRY_URL` in 18); `/q/health/ready` healthcheck on every app service.
- LGTM traces, metrics and logs, with exactly one Prometheus check with `service_name`.
- Topics only from `Topics`. Carriers only FASTLINE and AIRCO. No banned freight names (`validate.py`).
- Demo switches labelled as demo code in code comments, README and prose.
- Every claim row verified or cut; every prose experiment re-run in step 4 and present in the final logs.

**Iteration**:

- `python3 scripts/validate.py` ok; `scripts/voice-scan.sh` 0 ban-tier; `scripts/idiomatic-scan.sh` 0 high; `python3 scripts/host_ids.py check` 0 hits before every commit; `scripts/site-build.sh` builds.
- Final serial run 00 to 18 logged in `_plans/verification-log.md`: mvn and verify, every example green, no `edp-*` left.
- CI green (the examples workflow picks up 16 to 18; Apicurio Dev Services on the runner); tag r07.0 and release.
- PRD rows 16 to 18 in domain wording; iteration-plan and CLAUDE.md decisions updated.

## Sources to fetch and confirm (1A)

- Fowler, "CQRS" (bliki), https://martinfowler.com/bliki/CQRS.html.
- Microsoft Azure Architecture Center: CQRS pattern; Materialized View pattern.
- Young, CQRS Documents: the CQRS and read-model sections, with page numbers (PDF already in sources).
- Young, *Versioning in an Event Sourced System*: TOC confirmed in this session; record it. Body unconfirmed: no content claims.
- Apache Avro specification, Schema Resolution (defaults for missing writer fields; unknown writer fields ignored).
- Apicurio Registry 3.3.x:
  - rule reference (levels, values, the HTTP status on a violation);
  - REST API v3 (global and artifact rules, versions);
  - Kafka serde settings (auto-register, id handling, artifact resolver strategy);
  - Dev Services for Apicurio (the image property).
- Debezium Outbox Event Router, Avro payload section (confirmed here; record it); Debezium outbox Quarkus extension payload settings.
- Quarkus guides: Kafka Schema Registry Avro (confirmed here; record it), Scheduler (`concurrentExecution`), Kafka (pausable channels or SmallRye page), Hibernate ORM with Panache (projection `stream`).
- SmallRye Reactive Messaging: pausable channels page for the platform's version.
- Camel 4.22.x Kafka component: comma-separated topics, the `kafka.TOPIC` header, deserializer and additional properties.
- PostgreSQL 18: Transaction ID and Snapshot Information Functions; `SELECT ... FOR UPDATE`; `INSERT ... ON CONFLICT DO UPDATE ... WHERE`.
- Lawrence ch 5, 6, 8 and Bellemare ch 4, 7, 9, 16 (TOC rows exist; titles only).
- Kleppmann and Riccomini DDIA 2e: book level only.

## Risks

| Risk | Detection | Response |
|---|---|---|
| SmallRye pausable channels absent or not keeping offsets uncommitted | S1 | `KafkaClientService.getConsumer(ch).pause()`; otherwise stop the channel by profile and assert lag from the group |
| Lag timer max decays before check 15 reads it | S1 | read within the step window, or use the histogram count above a bucket; NOTE plus the service's own `/q/metrics` |
| Burst p95 bound noisy | S1 three runs; step 3 | conservative B, or NOTE only (r06 recommendation 8 precedent); deterministic checks stay the bite |
| Six channels with distinct groups slow startup under KIP-848 | S1 (time to assignment) | one group per channel stays; precondition polls 60 s |
| `xid8` mapping or `pg_snapshot_xmin` query awkward in Hibernate | S2 | native query with `::text::bigint` casts; checkpoint stored as `bigint` |
| Watermark stalls forever behind a long-lived open transaction (for example an idle session) | S2; verify 16 to 17 | the prose names the trade-off; `idle_in_transaction_session_timeout` is one sentence if sourced |
| The gap race is flaky live | S2; P check on `pg_stat_activity` | hold of 6 s, polls of 60 s; mvn latch test is the deterministic proof |
| 100k orders (about 340k events) replay is slow or heap-heavy | S2 | stream-ordered projection; `mem_limit` raised within budget; sizes fall to 50k with the prose adjusted |
| Bench numbers drift between runs, or a second bench sneaks in | step 3 logs; validator bench audit | one `DemoBench` call per size per run, before mutating checks; values quoted only from final runs |
| Apicurio serde 3.3.4 override breaks the 3.40 extension | S4 | hold at 3.1.7 (platform), recorded with the reason; test against the 3.3.4 server |
| Outbox extension cannot carry `byte[]` | S4 | Panache entity on the same outbox table (the Event Router does not need the extension); prose unchanged |
| BinaryDataConverter missing or misbehaving in the published Connect image | S4 | it ships in Debezium core; else the `ByteArrayConverter` from Kafka Connect, recorded |
| Registry id handling differs (headers versus payload) between serde and the decode check | S4 | the python decode in verify follows the observed format; the check names the format |
| Auto-register registers v3 under BACKWARD in a test that leaks into another | mvn isolation | a fresh Dev Services registry per test profile, or a unique artifact id per test |
| Ch 18 stack memory (about 7.5 GB of limits with Apicurio and Connect) | S6 `docker stats` | limits are caps, not reservations; lower the app limits to 320m; never change host settings |
| Word budget (18 has two patterns and a refusal section; 17 has a table and the gap) | draft count at step 2 | 18 drops the Camel table to two rows; 17 keeps the gap section to one figure and one paragraph |
| Forward references (16 naming replay cost or schemas, 17 naming upcasting) | validator read | backward only; 17's oracle is not taught |
| Banned freight names via PRD copy (`Manifest` in 17) | `validate.py` | domain wording only |
| Overlap with chapters 14 and 15 | validator 7-gram | refer back; minimal copied code; no re-teaching of the store |
| Unrun or unstable experiments in prose | step 4; validator | re-run all; unstable checks are not named |
| Renumbering after the window | frozen numbering plus grep | re-map the prose and claims |
| Stack contention | flock; `stack-guard.sh` | serial windows only |
| Host identifiers in logs or the plan | `host_ids.py check` before each commit | `host_ids.py filter` on captured logs |

## Owner decisions (recommendations)

1. **Ch 16 source.**
   - Recommendation: **project from the Kafka catalog topics with SmallRye Reactive Messaging** (six `@Incoming` channels into one transactional projector).
   - Reason: the customer view combines facts from three owners that only meet on Kafka. Per-partition commit order removes the gap problem, and group lag is native.
   - Rejected: the store poller (only the owner's own events, and the gap problem, which belongs in 17); Debezium CDC of the events table (Connect plus an internal topic for nothing the public topics lack). One sentence for each in "When not to use it".
2. **Ch 16 command side.**
   - Recommendation: **the example is the query side of order-service only**. verify.sh produces the six events in their owners' record formats (key = order id, `id` and `eventType` headers), as chapter 14 did for PaymentProcessed. The prose names chapter 14 as the write side.
   - Alternative: copy chapter 14's aggregate and outbox in (+ Connect, about double the code).
3. **How read lag is measured and asserted.** Recommendation:
   - **Deterministic assertions**: a paused channel (labelled demo endpoint); group lag 1; five stale reads with `asOf` older than the record; convergence within 10 s after resume.
   - **Measured lag**: a Micrometer timer `order.view.lag` (record timestamp to commit), checked once in Prometheus. Burst p50 and p95 are NOTEs, checked only against a spike-derived bound with a 4x margin, or NOTE only if noisy.
   - `PROJECTOR_DELAY_MS` as the experiment that shows lag as arrival rate against throughput.
4. **Ch 16 ordering and idempotence.** Recommendation:
   - each column set only by its owner's event;
   - a forward-only (rank-guarded) upsert for shipment status, with CANCELLED terminal;
   - partial rows for early events;
   - no counters, so no inbox.
   - `PROJECTION_GUARD=off` (demo) is the negative witness.
5. **Ch 17 code reuse.**
   - Recommendation: **a fresh example** with a minimal copy of the chapter 14 store and aggregate (payload records are per example). Do not extend 14 (one example per chapter; frozen checks) and do not reuse 16 (no aggregates to replay).
   - Stack: PostgreSQL and LGTM only.
6. **Ch 17 projection mechanism.** Recommendation:
   - an in-process store tail: Quarkus `@Scheduled(concurrentExecution = SKIP)`, one `@Transactional` batch per tick, rows and checkpoint in the same transaction, checkpoint row `FOR UPDATE`;
   - an **`xid8` + `pg_snapshot_xmin` watermark**, so the checkpoint never passes an open transaction;
   - `PROJECTION_WATERMARK=off` plus `DEMO_HOLD_FOR` as the negative witness.
7. **Ch 17 bench.** Recommendation:
   - 1,000, 10,000 and 100,000 orders (about 3.4 events each), seeded cumulatively by SQL;
   - **one bench** per size, before any mutating check: replay median, projection median, rebuild time, bytes;
   - three runs from fresh containers, the median of the run medians with the range;
   - a ratio check R at 100k (from S2), NOTE-only if not 3/3 with a 4x margin;
   - the decision table and `17-cost-by-size` take numbers only from these runs.
8. **Ch 18 upcasting location.**
   - Recommendation: **both, each with its idiomatic mechanism**. On read from the event store, a `JsonNode` upcaster chain keyed by a `schema_version` column; rows are never rewritten and the trigger refuses a rewrite. On consume from Kafka, Avro reader-schema resolution through the Apicurio deserializer, so no hand-written upcaster runs on consume.
   - Both use the same default (STANDARD), and a test proves the two agree.
9. **Ch 18 format.**
   - Recommendation: **Avro** (`quarkus-apicurio-registry-avro`, classes from `.avsc`; PRD stack row). Avro resolution fills defaults, which is the wire-side upcast.
   - JSON Schema rejected: it is checked by Apicurio but has no resolution, so consumers would hand-roll the default.
10. **Compatibility rule and how v3 is refused.** Recommendation:
    - **BACKWARD_TRANSITIVE**, set as the global (or artifact) COMPATIBILITY rule by verify.sh through the REST API, standing in for the contract owner.
    - v3 = `shippingPriority` **without a default**. It is accepted by BACKWARD (it reads v2 data) and refused by BACKWARD_TRANSITIVE (it cannot read v1). That shows why a log that is re-read from the start needs the transitive rule.
    - The refusal is shown twice: as a REST 409 (status per S4) naming the rule, and at command time with `ORDER_PLACED_SCHEMA=3`, where the transaction rolls back and leaves no event, no outbox row, no record and no version.
    - `REGISTRY_RULE=BACKWARD` is the experiment, plus an mvn Avro test showing a v3 reader fails on v1 bytes.
11. **Ch 18 publication path.**
    - Recommendation: **the chapter 08 outbox with an Avro `bytea` payload**. The registry serializer runs in the command transaction, and the Event Router passes the bytes through with BinaryDataConverter (the Debezium-documented path).
    - Fallback if the extension cannot hold `byte[]`: a Panache entity on the same table.
    - Rejected: an Emitter after commit (dual write); Connect's AvroConverter on a JSON payload (the schema would be derived from the row, not from the contract).
12. **Apicurio serde pin.**
    - Recommendation: override the platform's 3.1.7 with **3.3.4** (serde and Java SDK, matching the server; camel-bom precedent) if S4 passes in Dev Services and on compose. Otherwise **hold at 3.1.7**, recorded with the reason.
    - Pin the Dev Services Apicurio image to `quay.io/apicurio/apicurio-registry:3.3.4` in both cases.
13. **Camel comparisons.** Recommendation:
    - **16 yes**: one Camel consumer over six topics feeding the same projector, view hash compared.
    - **17 no**: `### How Quarkus models it`, to keep the word budget for the decision.
    - **18 minimal**: a Camel reader with the Apicurio deserializer configured explicitly, compared on the same records.
14. **One example per chapter** (house pattern):
    - `examples/16-cqrs-and-projections` (order-service, package `...projections`);
    - `examples/17-aggregate-replay-or-projection` (order-service, `...replay`);
    - `examples/18-schema-evolution-and-upcasting` (order-service and notification-service, `...evolution`);
    - chapter slugs the same.
15. **Demo switches** (labelled demo code, as in r06): `PROJECTION_GUARD`, `PROJECTOR_DELAY_MS`, the pause/resume endpoint (16); `PROJECTION_WATERMARK`, `PROJECTION_POLL`, `DEMO_HOLD_FOR` and `DEMO_HOLD_MS`, the bench and rebuild endpoints (17); `ORDER_PLACED_SCHEMA=1|2|3`, `UPCASTERS`, `REGISTRY_RULE` (18).
16. **Domain.** Rows 16 to 18 are rewritten as above. `shippingPriority` takes STANDARD or EXPRESS (a payload field, no entity). Customer ids are `C-16xx`. Order ids are `O-16xx`, `O-17xxxxxx` and `O-18xx`. domain-model stays 2.2.0.
17. **Citations.**
    - Young's *Versioning in an Event Sourced System* by chapter titles only (body not fetched; owner may supply it).
    - New bibliography rows: Fowler CQRS, Microsoft CQRS and Materialized View, the Avro specification, Young's versioning book.
    - Kleppmann at book level only.
    - Bellemare by chapter title (4, 7, 9, 16); Lawrence ch 5, 6, 8.
18. **Runtime window order:** 17 (PostgreSQL and LGTM), then 16 (adds Kafka), then 18 (full stack with Connect and Apicurio).
19. **At ship:** rewrite PRD rows 16 to 18 in domain wording (they still name banned freight entities), update the roadmap and status, add a CLAUDE.md decisions entry for r07, and set r06-plan step 7 to done (housekeeping, step 0).

### Critical Files for Implementation

- `<repo>/examples/14-aggregate-root-and-event-store/order-service/src/main/java/com/patterncatalyst/shipping/aggregate/EventStore.java` and `.../OrderAggregate.java` (the store and aggregate that 17 and 18 copy minimally)
- `<repo>/examples/14-aggregate-root-and-event-store/verify.sh` and `<repo>/examples/15-snapshots/verify.sh` (helpers, preconditions, profile switches, the one-bench pattern, the console producer)
- `<repo>/examples/08-transactional-outbox/connect/order-outbox.json` (outbox connector for 18, with BinaryDataConverter)
- `<repo>/examples/_infra/compose-apicurio.yaml` (registry service, ports 8081 and 19000, in-memory)
- `<repo>/_plans/domain-model.md` (rows 16 to 18) and `<repo>/scripts/validate.py` (`BANNED_DOMAIN`, word limit, fresh-start rule)
