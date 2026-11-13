---
layout: plan
title: r08 plan
render_with_liquid: false
---

# r08 plan: chapters 19 to 21, enrich, join, aggregate (Opus planner, 2026-11-13)

Status: **draft for owner review**. Branch `release/r08`, cut from main after r07.0; r07 housekeeping committed at ffc24e0.

Planner note: this plan was made with read-only commands, the local Maven repository (the quarkus-bom and quarkus-camel-bom 3.40.1.2 poms, the kafka-streams 4.2.1 pom), Maven Central metadata, and web fetches of the Quarkus Kafka Streams guide, the Quarkus Micrometer guide, the Camel 4.22.x Aggregate and Enrich EIP pages, the Camel 4.22.x SQL component page, the Camel Quarkus SQL extension page and the Kafka Streams 4.2 DSL page. No stack was started, no container was run, nothing but this file was written and no git command changed state.

## Findings

### Repo state that binds this iteration

- **Part 4 starts here.** `_parts/04-processing-and-coordinating-streams.md` exists (`part_name: processing-and-coordinating-streams`, blurb "Stateful stream processing, windows and joins, and sagas..."). No chapter uses it yet. All three chapters take `part: processing-and-coordinating-streams`.
- **No example uses Kafka Streams yet.** CLAUDE.md already names the env convention: "SmallRye Reactive Messaging and Kafka Streams apps use `KAFKA_BOOTSTRAP_SERVERS`"; the Camel side keeps `CAMEL_COMPONENT_KAFKA_BROKERS`. The bibliography rows for Apache Kafka and Quarkus already say "Kafka Streams".
- **The broker already lists the streams rebalance protocol** (`KAFKA_GROUP_COORDINATOR_REBALANCE_PROTOCOLS=classic,consumer,streams,share` in `examples/_infra/compose-kafka.yaml`). The Streams client keeps its default group protocol; the book does not teach KIP-1071 here (1A records the 4.2 client default and status).
- **The broker auto-creates topics with 3 partitions** (`KAFKA_NUM_PARTITIONS=3`). r07 S6: `up --wait` can return before `topics-init` exits; app services depend on `topics-init: service_completed_successfully`, and host helpers wait for its exit. Kafka Streams adds a second reason: `quarkus.kafka-streams.topics` holds readiness until the listed topics exist, so readiness also covers topic creation.
- **Topics.** `Topics` (domain-model 2.2.0) has the nine catalog topics plus `SHIPPING_SHIPMENTS`, `INVENTORY_ITEM_CHANGES`, `INVENTORY_ITEMS`, `ORDERS_PLACED_DLQ`. None of the reference, enriched, joined or batched outputs these chapters need exists. `DomainModelTest` requires non-catalog topics to match `edp\.[a-z]+\.[a-z-]+` and to stay out of `ALL`. Precedent (r05): a new constant bumps domain-model (2.1.0 to 2.2.0) and every example pom is bumped and `mvn -B verify`'d at G0, because Containerfiles build domain-model from source. decision 15.
- **Carrier** is an enum (`FASTLINE`, `AIRCO`); domain-model.md states FASTLINE is road and AIRCO is air. Reference data about a carrier (its mode, its tracking URL template) is a payload record about an existing entity, like `shippingPriority` in 18: no new entity, no domain-model code change for it.
- **Domain rows in `_plans/domain-model.md`:**
  - 19: "ShipmentScheduled enriched with carrier reference data; Camel Content Enricher vs a Kafka Streams stream-table join"
  - 20: "InventoryReserved joined with PaymentProcessed per order within a window; Kafka Streams windowed join vs a Camel Aggregator with completion timeout"
  - 21: "ShipmentDelivered counts per carrier per window; a Camel Aggregator batches ShipmentScheduled into a per-carrier pickup list"
  - Row 21 names **two inputs**, while PRD Q6 says both forms run **on the same input**. decision 12 resolves it.
  - Appendix C row: "ShipmentDelivered counts per carrier per window, three ways". It stands on its own at r13 and need not change.
- **Banned names** (`BANNED_DOMAIN` in `scripts/validate.py`): `Manifest`, `Invoice`, `Settlement`, `Tariff`, `RateQuote`, `freight`, real carriers and more. A pickup list is never a "manifest", even in lower case. Never copy PRD wording.
- **Earlier prose that r08 must stay consistent with:**
  - Ch 02: the fetch-back over HTTP (`toD("{{order-service.url}}/orders/${header.orderId}")`, camel-quarkus-http) is the domain's one synchronous exception. Chapter 19's Content Enricher is the same kind of call, merged into the message instead of replacing it; one backward sentence.
  - Ch 03: `order_copy` is a local copy kept from events. A GlobalKTable is the same idea held by Kafka Streams; one backward sentence.
  - Ch 04: order holds within a partition only, keyed by order id. Chapter 20's co-partitioning refers back.
  - Ch 06: the consumer protocol (KIP-848) for plain consumers. Kafka Streams keeps its own default; no sentence may suggest otherwise.
  - Ch 12 cites CNDP chapter 6 "Stream Processing". Chapter 21 may cite it too.
  - Ch 14 line 140: "Camel's Aggregator EIP shares the word and nothing else: it combines messages and has no relation to a domain aggregate." Chapters 20 and 21 must stay consistent with it.
  - Ch 17 cites Bellemare ch 9 for reprocessing.
- **Sibling chapters to link** (References): EIP Camel ch 12 "Transformation Fundamentals" (`## Pattern: Content Enricher`), ch 13 "Structural Transformation" (`## Pattern: Aggregator`); CNDP ch 6 "Stream Processing" (a tumbling-window Kafka Streams revenue aggregation with `ofSizeWithNoGrace`; its Quarkus module is a separate stream-processor app with no tests).
- **Housekeeping:** none open. r07-plan step 7 is done and iteration-plan says r07 done (ffc24e0). Step 0 sets r08 to "in progress".

### r07 lessons carried into r08 (verification log, validation rounds)

- **Fifteen runs before "deterministic".** A timing or negative witness is called deterministic only after 15 green runs. Otherwise the prose states the measured rate. This matters most for the Camel arrival-time witnesses in 20 and 21.
- **A phase-dependent witness needs a sync step.** Kafka Streams closes a window only when stream time passes window end plus grace, and stream time moves only when a record arrives. Every window or join-expiry check is preceded by a tick record and a sync read. GlobalKTable updates are confirmed by a store read before the next record.
- **One bench per table, figure and prose number.** Each chapter's numbers come from one bench in `verify.sh`, run before any mutating check, 3 runs from fresh containers, reported as the median of run medians with the range.
- **EXPLAIN every hand-written SQL batch query.** Chapter 20's aggregation repository uses Camel's own SQL. Any hand-written SQL (verify helpers counting repository rows) is read-only and needs no plan; any hand-written batch query in app code gets an `EXPLAIN` in the spike log.
- **Never cite an experiment as final-run evidence unless the final script ran it.** Every prose experiment runs in step 4 and in step 5.
- **Every claim maps to a test, a recorded run or a fetched source** (claims.md rows).
- From r06 and r07: preconditions keep experiments from cascading; check numbering frozen after the window and "check N" grepped after any renumbering; one Prometheus check with `service_name`; timers export in milliseconds; `max_over_time` for short bursts; `--reader-property` and `--formatter-property` on the 4.3 console tools; group lag computed from one `--describe --all-groups`; `quarkus.otel.metric.export.interval=5s`; never `grep -q` under pipefail; a Camel profile repeats `%profile.camel.component.kafka.brokers`.

### Versions re-survey (preliminary, 2026-11-13; 1A completes and records in versions.md)

| Item | Found | Source | Change |
|---|---|---|---|
| quarkus-bom / quarkus-camel-bom | 3.40.1.2 (r07 survey; 1A re-reads the streams API) | https://repo1.maven.org/maven2/io/quarkus/platform/quarkus-bom/maven-metadata.xml ; https://code.quarkus.io/api/streams | none expected |
| `quarkus-kafka-streams` | 3.40.1.2, in quarkus-bom | local quarkus-bom-3.40.1.2.pom | new extension, platform-managed |
| `kafka-streams`, `kafka-streams-test-utils`, `kafka-clients` | **4.2.1** managed by quarkus-bom 3.40.1.2. Central has 4.2.2, 4.3.0 to 4.3.2 and 4.4.0 | local pom; https://repo1.maven.org/maven2/org/apache/kafka/kafka-streams/maven-metadata.xml | **decision 1**: keep 4.2.1 (not overridden, same rule as kafka-clients since r01) |
| `rocksdbjni` | 10.1.3, declared by kafka-streams 4.2.1 (not in the Quarkus BOM). Central latest 11.8.1 | kafka-streams-4.2.1.pom; https://repo1.maven.org/maven2/org/rocksdb/rocksdbjni/maven-metadata.xml | not overridden (Kafka Streams pins its store engine); S1 proves the JNI library loads on ubi10/openjdk-25 |
| `quarkus-test-kafka-companion` | 3.40.1.2 | local pom | new test dependency |
| camel-quarkus-http, -sql | 3.40.0 (quarkus-camel-bom); camel-bom 4.22.2 import first | local pom | none |
| `spring-jdbc` / `spring-tx` (pulled by camel-sql for `JdbcAggregationRepository`) | 7.0.9 (camel-bom 4.22.2 and quarkus-camel-bom both manage 7.0.9). Central metadata `latest` is 7.1.0-RC2 (prerelease) | https://repo1.maven.org/maven2/org/apache/camel/camel-sql/4.22.2/camel-sql-4.22.2.pom ; https://repo1.maven.org/maven2/org/springframework/spring-jdbc/maven-metadata.xml | none; 1A records the newest 7.0.x final |
| Caffeine | 3.2.4 (quarkus-bom) | local pom | not used (decision 6) |
| Testcontainers | 2.0.5 (quarkus-bom) | local pom | none |
| Kafka broker | 4.3.2 held (Strimzi 1.3.1); 1A re-reads Strimzi releases | https://api.github.com/repos/strimzi/strimzi-kafka-operator/releases | none expected |
| Not yet checked (1A) | Strimzi; apache/kafka digest; otel-lgtm; ubi10 openjdk-25 tag; CNPG operand and library/postgres (held 18.6 by owner, record only); Temurin 25 patch; Maven #57216; the Kafka Streams 4.2 default `group.protocol` and KIP-1071 status; whether Quarkus binds `KafkaStreamsMetrics` | as r07 | |

No version appears in the 19 to 21 prose.

### What the sources say (confirmed in this session)

- **Quarkus "Using Apache Kafka Streams" guide** (fetched 2026-11-13):
  - With `quarkus-smallrye-health`, readiness checks that every topic in `quarkus.kafka-streams.topics` exists (`missing_topics` otherwise); liveness checks the `KafkaStreams.State`; DOWN unless RUNNING.
  - The pipeline starts only once the listed topics exist. `quarkus.kafka-streams.topics-timeout` defaults to 10 s.
  - "All the properties within the `kafka-streams` namespace are passed through as-is to the Kafka Streams engine" (for example `kafka-streams.commit.interval.ms`). Bootstrap servers fall back to `kafka.bootstrap.servers`.
  - Interactive queries: `streams.store(StoreQueryParameters...)`, retrying on `InvalidStateStoreException`.
  - The guide documents **no** runtime switch to disable the pipeline, and **no** test approach (no TopologyTestDriver, no Dev Services section). It does not discuss Kafka Streams beside other Kafka extensions in one app. S1 settles coexistence.
- **Quarkus Micrometer guide:** `quarkus.micrometer.binder.kafka.enabled` binds Kafka consumer and producer metrics. **Kafka Streams is not mentioned.** S1 finds whether `KafkaStreamsMetrics` is bound under `quarkus-micrometer-opentelemetry` and the exported name; fallback is a Micrometer meter in app code.
- **Kafka Streams 4.2 DSL page:**
  - "KStream-KStream joins are always windowed joins." The input topics "must have the same number of partitions" (co-partitioning).
  - Left and outer stream-stream joins emit an unmatched left record "after the specified grace period passed".
  - KStream-GlobalKTable: no co-partitioning, because "all partitions of the GlobalKTable's underlying changelog stream are made available to each KafkaStreams instance"; a KeyValueMapper "allows for non-key based joins"; "GlobalKTable has no notion of time in contrast to a KTable".
  - "Applying a grouping or a join after `selectKey` will result in re-partitioning of the records."
  - "A tumbling window is a hopping window whose window size is equal to its advance interval." `TimeWindows.ofSizeAndGrace(size, grace)` is a tumbling window.
  - `suppress(untilWindowCloses(...))` emits "nothing for a window until it closes, and then emit the final result". A 09:00 to 10:00 window with 10 minutes' grace accepts out-of-order records until 10:10.
  - Not found on the page (1A fetches elsewhere): `JoinWindows.ofTimeDifferenceAndGrace`, the statement that records after grace are dropped, the dropped-records metric, `EmitStrategy.onWindowClose()`, stream time advancing only with input, GlobalKTable bootstrapping before processing starts.
- **Camel 4.22.x Enrich EIP:** `enrich` uses a producer (request-reply, "such as invoking an external web service"); `pollEnrich` uses a polling consumer (event messages, files). The required `expression` computes the URI, for example `.enrich().simple("http:myserver/${header.orderId}/order")`. With an AggregationStrategy the original and resource exchanges are merged; without one, enrich equals `to(...)`. `aggregateOnException` defaults to false. The page's parameter order for `aggregate` is ambiguous: S2 confirms it in code.
- **Camel 4.22.x Aggregate EIP:**
  - `completionTimeout`: "Time that an aggregated exchange should be inactive before its complete", per correlation key, reset by each new exchange. `completionTimeoutCheckerInterval` defaults to 1000 ms.
  - `completionSize`, `completionPredicate`, `completionInterval` (a repeating clock period). Whichever completion triggers first wins. Only `completionTimeout` and `completionInterval` cannot be combined.
  - `eagerCheckCompletion`, `discardOnCompletionTimeout`, `forceCompletionOnStop`, `completeAllOnStop`.
  - Exchange property `CamelAggregatedCompletedBy` tells how a group completed (values not listed; S3 records them).
  - The default repository is `MemoryAggregationRepository`. Persistent repositories come from other components (SQL among them).
- **Camel 4.22.x SQL component, aggregation repository:**
  - `JdbcAggregationRepository`, and `PostgresAggregationRepository` which uses `INSERT .. ON CONFLICT .. DO NOTHING` because PostgreSQL marks the connection invalid after a 23505 inside a nested transaction.
  - Two tables, `<name>` and `<name>_completed`, columns `id varchar(255)`, `exchange` (binary), `version bigint`.
  - Needs a Spring `PlatformTransactionManager` and a `DataSource`; the exchange is serialized by `JdbcCodec`. `maximumRedeliveries` and `deadLetterUri` for recovery.
- **Camel Quarkus SQL extension:** resolves the `DataSource` from the registry; says nothing of a transaction manager for the repository. S3 settles `DataSourceTransactionManager` over the Quarkus Agroal datasource.

## Approach

Same gate shape as r07:

1. G0: re-survey, source fetches and spikes, in parallel.
2. Three parallel authoring executors on disjoint files, with `mvn verify` serialized under the stack lock.
3. Serial, exclusive runtime windows (20, then 21, then 19), including the 15-run loops.
4. The orchestrator re-runs every prose experiment.
5. Final serial run 00 to 21.
6. Opus validation (at most 2 repair rounds), then ship.

**One example per chapter, one Quarkus app that hosts both forms** (S1 confirms). The Kafka Streams topology and the Camel route run side by side in the same service, each with its own consumer group and its own output topic, so one `verify.sh` input feeds both and every divergence is visible in one run. The prose says plainly that a real service runs one of them. Each chapter ends with a decision table drawn from that run.

- **19 Enrich.** notification-service needs each ShipmentScheduled with the carrier's mode and tracking URL before it tells the customer. **Camel Content Enricher**: `enrich()` asks shipping-service over HTTP for every record (current data, but coupled to the owner's availability). **Kafka Streams**: a KStream-GlobalKTable left join against a compacted `edp.shipping.carriers` topic (local and available, but only as fresh as the topic). The two differ visibly when the reference changes and when the owner is down.
- **20 Join.** shipping-service releases an order when both InventoryReserved and PaymentProcessed have arrived. **Kafka Streams**: a windowed KStream-KStream left join on order id by **event time from the payload**, with grace; an unmatched reservation becomes PaymentOverdue when the window closes. **Camel**: an Aggregator correlated on the record key, completion predicate "both kinds present", `completionTimeout` as the window **by arrival time**, persisted in PostgreSQL (`PostgresAggregationRepository`). Two crafted pairs make the forms disagree in opposite directions.
- **21 Aggregate.** shipping-service groups ShipmentScheduled per carrier into pickup lists. **Kafka Streams**: a tumbling event-time window with grace and `suppress(untilWindowCloses)`, one final list (and count) per carrier per window, a late record dropped and counted. **Camel**: an Aggregator batch per carrier (`completionSize` plus `completionTimeout`) in arrival order. Same input, same output type, different grouping rule.

**Rejected alternatives (one line each):**

- **Two modules per example (a Streams app and a Camel app):** twice the build and compose surface for the same comparison. Used only if S1 shows the two cannot share one app.
- **Profile switch, one form at a time (ch 16 and 18 style):** Quarkus documents no runtime switch for the Kafka Streams pipeline, and every scenario would run twice. Kept as the fallback (decision 3).
- **One shared output topic with a path header:** Kafka Streams would need the Processor API (`processValues`) just to set a header, which adds noise to a DSL lesson, and consumers of the topic would see duplicates.
- **19 Camel enricher reading the compacted topic:** `pollEnrich` on Kafka takes the next record, it does not look up a key. A local map fed by the topic is a hand-rolled table.
- **19 Camel enricher reading shipping's table over JDBC:** services share no tables (domain rule).
- **19 Debezium CDC from the carrier table to the topic:** adds Connect for a lesson about joins. The prose names the outbox and CDC (chapters 08 and 09) as how the owner feeds the topic; `verify.sh` stands in for the owner, as chapter 16 did.
- **19 KTable after `selectKey(carrier)`:** correct, but it adds a repartition topic and co-partitioning for a two-row table. One sentence and an mvn test that shows the key-mismatch failure.
- **20 inner join:** an order that is never paid leaves no trace. The left join turns the absence into a PaymentOverdue.
- **20 Camel `completionSize(2)`:** a duplicate InventoryReserved would complete the group with no payment. A predicate over the kinds present is the guard; an mvn test shows the size-based bug.
- **20 and 21 Camel in-memory repository as the design:** a recreate loses open groups. It is the negative witness in 20.
- **21 hopping windows for pickup lists:** each shipment would appear in several overlapping lists. An mvn TopologyTestDriver test shows the count multiplied by size over advance.
- **21 emitting every update downstream (no suppression):** a consumer would receive partial lists. It is the demo switch `PICKUP_EMIT=updates`.
- **ksqlDB, Flink, RisingWave:** Appendix C (r13).

## Per-chapter design

Shared conventions for all three examples:

- One Quarkus module per example (decision 3). The aggregator pom pattern is kept, so 19 can carry its second module.
- Extensions: `quarkus-kafka-streams`, camel-quarkus-kafka, -jackson, -bean, -opentelemetry2, quarkus-opentelemetry, quarkus-micrometer-opentelemetry, quarkus-smallrye-health, quarkus-rest-jackson (demo and query endpoints). Tests: quarkus-junit, rest-assured, awaitility, `kafka-streams-test-utils`, `quarkus-test-kafka-companion`.
- `quarkus.kafka-streams.application-id` names the service and the chapter (`notification-enricher`, `shipping-readiness`, `shipping-pickups`). It is also the Streams group id and the prefix of its internal topics. Every store is named explicitly (`Materialized.as`, `StreamJoined.withStoreName`), so internal topic names are stable and readable.
- `quarkus.kafka-streams.topics` lists the input topics, so readiness covers them. Pass-through: `kafka-streams.commit.interval.ms=1000`, `kafka-streams.statestore.cache.max.bytes=0` (S1 confirms both are needed for prompt output), `kafka-streams.auto.offset.reset=earliest`, `kafka-streams.state.dir` under `/tmp` (writable by user 185; S1).
- **Event time** comes from the payload: each payload record carries `occurredAt` (ISO-8601 instant), and one `PayloadTimestampExtractor` (implements `TimestampExtractor`, reads the deserialized value through a small `HasEventTime` interface, falls back to the record timestamp) is set per source with `Consumed.with(...).withTimestampExtractor(...)`. Reason: the console producer cannot set record timestamps (S5 confirms), and records that go through an outbox or a replay carry the publish time, not the business time (decision 8). Camel never reads `occurredAt` for grouping: it sees arrival only.
- Topics are created by `topics-init` with **1 partition** each, so a single Streams task owns all of an example's input and one tick moves its stream time (decision 9). Chapter 20's co-partitioning rule is proven in mvn with 3 against 2 partitions.
- Sources are deserialized with `ObjectMapperSerde` (Quarkus Kafka client serde) on the Streams side and `unmarshal().json(...)` on the Camel side.
- Prometheus check (one per example) named in each design below. Fallback if S1 shows `KafkaStreamsMetrics` is not exported: a Micrometer meter in app code, named in G0.
- Demo switches are environment variables, labelled demo code in comments, README and prose.

### 19 Enrich (`examples/19-enrich`, `_docs/19-enrich.md`)

**Headings.**

- `## Pattern: Content Enricher`, with `### The problem`, `### The solution` (bold one-sentence definition), `### How Quarkus and Camel model it`.
- `## The same enrichment as a stream-table join`.
- `## When the reference changes` and `## When the owner is down` (short, one figure between them).
- Then `## Build, run, observe`, `## When not to use it`, `## References`.

**Opening.** ShipmentScheduled says which carrier was booked and the tracking number. The customer needs more: is the parcel going by road or by air, and which link tracks it. shipping-service owns that reference data; notification-service does not.

**Services (aggregator pom, two modules).**

- **notification-service** (port 8080, package `com.patterncatalyst.shipping.enrich`), the enricher with both forms.
- **shipping-service** (port 8082, package `com.patterncatalyst.shipping.enrich.carriers`): the owner of carrier reference data. Panache entity `CarrierRecord` on `shipping.carrier` (`carrier` pk, `mode`, `tracking_url_template`), seeded by Flyway (FASTLINE ROAD `https://track.fastline.example/{trackingNumber}`, AIRCO AIR `https://track.airco.example/{trackingNumber}`). `GET /carriers/{carrier}`, and `PUT /carriers/{carrier}` (labelled demo code: an operator changes a template). quarkus-rest-jackson, hibernate-orm-panache, jdbc-postgresql, flyway, health, OTel. No Kafka.
- Compose: kafka, postgres, lgtm, topics-init (`edp.shipping.scheduled`, `edp.shipping.carriers` with `cleanup.policy=compact`, both enriched output topics; 1 partition each). Domain names use `.example` hosts only.

**Payload records (per example).** `ShipmentScheduled(Shipment shipment, Instant occurredAt)`; `CarrierReference(Carrier carrier, String mode, String trackingUrlTemplate)`; `EnrichedShipmentScheduled(Shipment shipment, Instant occurredAt, String carrierMode, String trackingUrl)`, with `carrierMode` null when no reference was found.

**Code (notification-service).**

- **`ShipmentEnrichmentTopology`** (`@Produces Topology`):
  - `GlobalKTable<String, CarrierReference>` from `Topics.SHIPPING_CARRIERS`, store `carriers`.
  - `stream(Topics.SHIPPING_SCHEDULED)` `.leftJoin(carriers, (orderId, s) -> s.shipment().carrier().name(), Enrichment::merge)` `.to(Topics.SHIPPING_SCHEDULED_ENRICHED)`.
  - Left join, so a shipment whose carrier has no reference is forwarded with `carrierMode` null, never dropped.
- **`CarrierStoreResource`** (labelled demo code, used as the sync step): `GET /demo/carriers/{carrier}` reads the global store through an interactive query. `verify.sh` polls it after publishing a reference change and before the next ShipmentScheduled.
- **`ShipmentEnricherRoute`** (one RouteBuilder): `from(kafka:edp.shipping.scheduled?groupId=notification-enricher-camel&breakOnFirstError={{enricher.break-on-first-error}})`, `unmarshal().json(ShipmentScheduled.class)`, `.enrich().simple("{{shipping-service.url}}/carriers/${body.shipment.carrier}")` with `aggregationStrategy(carrierEnrichment)`, marshal, `to(kafka:edp.shipping.scheduled-enriched-camel)`. Error handler: redelivery 3 times with 1 s delay, then the exchange fails and, with `breakOnFirstError=true`, the consumer seeks back to the failed record and polls it again (S2 confirms on 4.22.2).
- **`CarrierEnrichment`** (`AggregationStrategy`): merges the HTTP response (a `CarrierReference`) into `EnrichedShipmentScheduled`. The same `Enrichment.merge` function serves both forms, so the only difference is where the reference comes from.
- **`enricher.break-on-first-error`** from `CAMEL_BREAK_ON_FIRST_ERROR` (demo, default `true`). `false` is the negative witness: the record that fails during the outage is committed and never enriched.
- Metric: `shipment.enrichments` counter, tags `path` (`kafka-streams`, `camel`) and `outcome` (`enriched`, `no-reference`), incremented in the shared merge (Prometheus `shipment_enrichments_total`), unless S1 shows a better framework metric.

**mvn tests (Dev Services: PostgreSQL 18.6, Kafka 4.3.2; TopologyTestDriver), at least 10 across both modules.**

- TTD: the left join enriches FASTLINE and AIRCO from the global table; a reference update applies to records processed after it, not before; a carrier missing from the table yields `carrierMode` null (the record is not dropped).
- TTD: an inner join drops the record with no reference (asserts the bad outcome; the reason for `leftJoin`).
- TTD: a KTable join on the record key (order id) against a carrier-keyed table matches nothing (asserts the bad outcome: a table join matches keys, not fields).
- Camel (`@QuarkusTest`, shipping-service stubbed by a test resource as in ch 02): the route enriches the same records the topology enriches; with the owner returning 503, `breakOnFirstError=true` leaves the record uncommitted and enriches it after recovery; `false` commits it without output (asserts the bad outcome).
- shipping-service: `GET` returns the seeded reference; `PUT` changes it.

**verify.sh (about 22 checks; "P" marks a precondition).**

1. P: both services ready; Streams state RUNNING (liveness); the Camel group owns its partition.
2. P: the four topics exist with 1 partition; `edp.shipping.carriers` has `cleanup.policy=compact`.
3. P: the two references are published to `edp.shipping.carriers`; `GET /demo/carriers/FASTLINE` and `/AIRCO` return them (sync).

Both forms on one input (O-1901 FASTLINE, O-1902 AIRCO):

4. The Streams output holds O-1901 with mode ROAD and the FASTLINE URL with its tracking number; O-1902 with AIR.
5. The Camel output holds the same two records, field for field (timestamps aside).

Reference change (the owner changes FASTLINE's template):

6. `PUT /carriers/FASTLINE` with the new template on shipping-service; nothing published yet. ShipmentScheduled O-1903: Camel gives the **new** URL, Streams the **old** one.
7. The new reference is published to `edp.shipping.carriers`; sync until `GET /demo/carriers/FASTLINE` shows it. ShipmentScheduled O-1904: both give the new URL.
8. O-1903 on the Streams output still holds the old URL (an output is not revised).

Owner down:

9. P: shipping-service stopped (`docker stop`), health unreachable.
10. ShipmentScheduled O-1905: the Streams output holds it, enriched, within 10 s.
11. For 20 s the Camel output has no O-1905, and the Camel group has lag 1.
12. shipping-service started again: the Camel output holds O-1905, enriched, within 30 s, and the Camel group has lag 0. Nothing was lost.

Missing reference (AIRCO's record deleted from the topic by a tombstone, then synced):

13. ShipmentScheduled O-1906 AIRCO: Streams forwards it with `carrierMode` null; Camel enriches it (the owner still has AIRCO). The left join keeps the record; the prose says what a missing reference costs each way.

Restart (local copy rebuilt):

14. notification-service recreated (`up -d --no-deps --force-recreate`, fresh container, empty state dir). After ready, `GET /demo/carriers/FASTLINE` returns the new template (the global store is rebuilt from the compacted topic). ShipmentScheduled O-1907 is enriched by both.

Bench (one bench, before nothing else mutates; run right after check 5):

15. 500 ShipmentScheduled (O-19100 to O-19599, both carriers) in one producer call: both outputs hold 500 enriched records within 60 s. NOTE: time from the producer call to the last output record per path, and shipping-service request count (500 lookups for Camel, 0 for Streams). Three runs, median of medians.

LGTM:

16. Prometheus `shipment_enrichments_total{path="camel",service_name="notification-service"}` is at least 500 (the one Prometheus check; name per S1).
17. Trace in Tempo for notification-service (the Camel enrich span calling shipping-service).
18. Log in Loki.

**Experiments:**

- `CAMEL_BREAK_ON_FIRST_ERROR=false`: the record that fails during the outage is committed without output. Expected failures: 12 (O-1905 never appears on the Camel output, lag 0). The main negative witness.

**Figures.**

- `19-enricher-vs-table-join`: one ShipmentScheduled; left, the Camel route calls shipping-service per record and merges; right, the topology joins against the local copy of `edp.shipping.carriers`. The owner sits at the top with its table and the topic it feeds.
- `19-reference-change-and-outage`: a timeline. The template changes in the owner's table, then on the topic; O-1903 differs between the paths, O-1904 agrees. The owner stops: Camel stalls with lag 1, Streams carries on; the owner returns, Camel catches up.

**Decision table (prose; numbers from the one bench).** Rows: where the reference lives; freshness (the lookup sees the owner's current row; the join sees the topic, measured as the publication delay); behaviour with the owner down (stall and catch up, or continue); cost per record (one HTTP call, measured; a local store read); memory (none; a full copy on every instance); what a missing reference does.

**Callout.** Names `ShipmentEnricherRoute`, `CarrierEnrichment`, `ShipmentEnrichmentTopology` and `edp.shipping.carriers`. Rule: look up per record when the reference must be current and the owner is reliably up; keep a local table fed by the owner's events when availability and cost per record matter more than freshness; a GlobalKTable suits small reference data that every instance needs.

**Citations.** Hohpe and Woolf, Content Enricher (1A fetches); Camel Enrich EIP; Kafka Streams DSL (GlobalKTable join); Quarkus Kafka Streams guide (interactive queries, health); Lawrence ch 9 "Composing Data from Different Sources"; Bellemare ch 7 "Denormalization and Eventification" and ch 8 "Stateful Event-Driven Microservices"; EIP Camel ch 12 (Content Enricher). Backward links to 02 (fetch-back), 03 (local copy), 08 and 09 (how the owner feeds the topic).

### 20 Join (`examples/20-join`, `_docs/20-join.md`)

**Headings.**

- `## Pattern: Stream-stream join` (EIP has no join pattern of this kind; the Camel form is the Aggregator), with problem, solution and how Quarkus and Camel model it.
- `## Event time and arrival time`.
- `## When one side never arrives`.
- `## State that survives a restart`.
- Then build, run and observe; when not to use it; references.

**Opening.** shipping-service may book a carrier only when the stock is reserved and the payment is taken. The two facts come from two services on two topics, keyed by the same order id, and either can arrive first.

**Service.** shipping-service (port 8080, package `com.patterncatalyst.shipping.join`). Compose: kafka, postgres, lgtm, topics-init (`edp.inventory.reserved`, `edp.payments.processed`, both readiness outputs; 1 partition each). Flyway V1 creates `shipping.readiness` and `shipping.readiness_completed` for the Camel repository (the layout Camel's SQL page gives, `exchange bytea`).

**Payload records.** `InventoryReserved(String orderId, String sku, int quantity, Instant occurredAt)`; `PaymentProcessed(Payment payment, Instant occurredAt)`; outputs `ReadyToShip(orderId, reservedAt, paidAt)` and `PaymentOverdue(orderId, reservedAt)`, one sealed `Readiness` type with a `type` field in the payload, on one topic per path.

**Code.**

- **`ReadinessTopology`** (`@Produces Topology`):
  - Two streams with `PayloadTimestampExtractor`.
  - `reserved.leftJoin(paid, Readiness::of, JoinWindows.ofTimeDifferenceAndGrace(Duration.ofMinutes(30), Duration.ofMinutes(5)), StreamJoined.with(...).withStoreName("readiness"))`.
  - A matched pair gives `ReadyToShip`; an unmatched reservation, emitted after window and grace, gives `PaymentOverdue`.
  - `.to(Topics.SHIPPING_ORDER_READINESS)`. The record type travels in the payload on both paths, so the two outputs compare field for field (decision 4).
- **`ReadinessRoute`** (one RouteBuilder):
  - `from(kafka:edp.inventory.reserved,edp.payments.processed?groupId=shipping-readiness-camel)`.
  - Unmarshal by `CamelKafkaTopic`.
  - `aggregate(header(KafkaConstants.KEY), readinessStrategy)` with `completionPredicate` (both kinds present), `completionTimeout({{readiness.timeout}})` (10 s), `aggregationRepository(repository)`.
  - `choice()` on `CamelAggregatedCompletedBy`: `timeout` gives PaymentOverdue, the predicate gives ReadyToShip.
  - `to(kafka:edp.shipping.order-readiness-camel)`.
- **`ReadinessRepository`** (`@Produces @Named`): `PostgresAggregationRepository("readiness", DataSourceTransactionManager(dataSource), dataSource)` (S3). `AGGREGATION_REPOSITORY=memory` (demo) returns a `MemoryAggregationRepository` instead: the negative witness for restart.
- **`READINESS_TIMEOUT_MS`** (default 10000) is configurable for tests.
- Metric: `order.readiness` counter, tags `path`, `outcome` (`ready`, `overdue`).

**The two crafted divergences (the chapter's core).** Event-time window 30 minutes, Camel timeout 10 s of arrival:

- **O-2003, close in event time, far apart in arrival:** reserved at 10:00, paid at 10:02 (event time), produced 20 s apart. Streams: ReadyToShip. Camel: PaymentOverdue at about 10 to 11 s, then the payment opens a new group that times out alone (S3 records exactly what Camel emits for the lone payment; the route ignores a lone payment, see S3).
- **O-2004, far apart in event time, together in arrival:** reserved at 10:00, paid at 11:30 (a replayed payment), produced back to back. Streams: no match; PaymentOverdue once the window closes (after a tick). Camel: ReadyToShip at once.

**mvn tests, at least 12.**

- TTD: match within the window in either order; a payment 31 minutes later does not match; an unmatched reservation yields PaymentOverdue only after stream time passes window end plus grace (and not before: asserts nothing is emitted one millisecond earlier); an out-of-order payment inside the grace still matches; a record older than stream time minus grace is dropped.
- TTD: the two crafted pairs give the Streams outcomes above.
- Co-partitioning (`@QuarkusTest`, test profile with Dev Services topic partitions 3 and 2): the Streams instance does not reach RUNNING and liveness is DOWN; the log names the partition mismatch (S3 records the exact message).
- Camel: the predicate completes on both kinds; a duplicate reservation does not complete the group; a `completionSize(2)` variant completes it with no payment (asserts the bad outcome); timeout yields PaymentOverdue; the PostgreSQL repository holds an open group in `shipping.readiness` and removes it on completion.
- Restart (`@QuarkusTest` restart is awkward; the restart is a live check).

**verify.sh (about 24 checks).**

1. P: service ready; Streams RUNNING; the Camel group owns both partitions.
2. P: four topics with 1 partition each; repository tables empty.

Happy path (O-2001):

3. InventoryReserved then PaymentProcessed (event times 10:00 and 10:01): both outputs hold one ReadyToShip for O-2001 within 10 s.
4. Payment first (O-2002): both outputs hold ReadyToShip.

Divergence:

5. O-2003 (close in event time, 20 s apart in arrival): the Streams output holds ReadyToShip.
6. The Camel output holds PaymentOverdue for O-2003. (Timing witness: 15-run rule.)
7. O-2004 (90 minutes apart in event time, back to back): the Camel output holds ReadyToShip at once.
8. P: tick (InventoryReserved for `O-2099` with event time 12:00), then sync until the Streams output holds PaymentOverdue for O-2004.
9. The Streams output holds no ReadyToShip for O-2004.

Never paid (O-2005):

10. Reserved only: Camel gives PaymentOverdue within 10 to 15 s of arrival (measured, NOTE).
11. Streams gives nothing before the tick (checked for 15 s), and PaymentOverdue after a tick past window end plus grace. The prose: an idle stream never closes its windows.

State survives a restart (O-2006):

12. InventoryReserved O-2006 only; P: the Streams store holds it (changelog topic end offset grew) and `shipping.readiness` holds one open group.
13. Service recreated with `--force-recreate` (empty state dir): after ready, PaymentProcessed O-2006: both outputs hold ReadyToShip. Streams restored the window store from its changelog; Camel read the group from PostgreSQL.
14. The Streams log shows the restoration of the `readiness` stores (S3 names the line).

Bench (runs after check 4, before any mutation):

15. 200 orders, reservations then payments in two producer calls, event times inside the window: both outputs hold 200 ReadyToShip within 60 s and no PaymentOverdue. NOTE: per path, the time to the last output; peak open groups in `shipping.readiness`; Streams changelog records. Three runs.

LGTM:

16. Prometheus `order_readiness_total{outcome="overdue",path="camel",service_name="shipping-service"}` at least 2 (the one Prometheus check).
17. Trace in Tempo.
18. Log in Loki.

**Experiments:**

- `AGGREGATION_REPOSITORY=memory`: the open group is lost on recreate, and the Camel consumer had already committed the reservation. Expected failure: 13 (Camel side; Streams side passes). Main negative witness.
- `READINESS_TIMEOUT_MS=30000`: O-2003 now matches on the Camel path and O-2005's overdue comes later. Expected failure: 6 (and 10 at its bound). Shows the timeout is a guess at arrival spread, not a business window. Kept in prose only if 15 runs agree.

**Figures.**

- `20-join-window`: an event-time axis with a reservation, the 30-minute window and grace; a payment inside matches; one outside does not; the tick closes the window and the overdue leaves.
- `20-event-vs-arrival-time`: O-2003 and O-2004 on two axes (event time above, arrival time below); Streams decides on the upper axis, Camel on the lower.
- `20-state-where`: Streams state in RocksDB plus a changelog topic; Camel state in `shipping.readiness`; a recreate wipes the container and both come back.

**Decision table.** Rows: time base (event time from the payload; arrival); unmatched side (left join after window plus grace, needs stream time to move; timeout per key, needs only the clock); out-of-order and replayed records; where state lives and how it comes back; partitioning (co-partitioned topics required; none, one consumer reads both topics); extra store (changelog topics; two tables).

**Callout.** Names `ReadinessTopology`, `ReadinessRoute`, `PayloadTimestampExtractor` and `readiness`. Rule: join by event time when the business window is about when things happened and records can arrive late or replayed; an aggregator with a completion timeout measures arrival and suits a short, live correlation; keep correlation state where a restart finds it.

**Citations.** Kafka Streams DSL (joins, JoinWindows, co-partitioning), Kafka Streams core concepts (time, stream time) and developer guide config (`processing.guarantee` only if stated); Camel Aggregate EIP and SQL component (`PostgresAggregationRepository`); Hohpe and Woolf, Aggregator (1A fetches); Bellemare ch 8 and ch 9; Lawrence ch 9; EIP Camel ch 13 (Aggregator). Backward links to 04 (keys and partitions), 11 (a duplicate event), 14 (the word "aggregate").

### 21 Aggregate (`examples/21-aggregate`, `_docs/21-aggregate.md`)

**Headings.**

- `## Pattern: Windowed aggregation`, with problem, solution and how Quarkus and Camel model it.
- `## Final results, not updates` (suppression).
- `## Late records` (grace, drop, the metric).
- `## Batches by size and time` (the Camel Aggregator).
- Then build, run and observe; when not to use it; references.

**Opening.** Each carrier collects parcels from the warehouse in pickup slots. For every slot, the warehouse needs one list per carrier of the shipments scheduled in it, and the number to expect.

**Input and output (decision 12).** One input, ShipmentScheduled on `edp.shipping.scheduled`. One output type, `PickupList(Carrier carrier, Instant from, Instant to, List<String> shipmentIds, int count, String closedBy)`. Streams writes `edp.shipping.pickup-lists`; Camel writes `edp.shipping.pickup-lists-camel`. The count per carrier per window is the Streams list's `count`.

**Service.** shipping-service (port 8080, package `com.patterncatalyst.shipping.pickups`). Compose: kafka, postgres, lgtm, topics-init (3 topics, 1 partition). Flyway: `shipping.pickups` and `shipping.pickups_completed` for the Camel repository (decision 14).

**Code.**

- **`PickupTopology`**: stream with `PayloadTimestampExtractor`; `groupBy(carrier)` (a repartition, named `by-carrier`; 1A and S4 record that this is the one repartition in the book so far); `windowedBy(TimeWindows.ofSizeAndGrace(Duration.ofMinutes(15), Duration.ofMinutes(5)))`; `aggregate(PickupList::empty, PickupList::add, Materialized.as("pickup-lists"))`; `suppress(Suppressed.untilWindowCloses(unbounded()))`; map to `PickupList` with window bounds and `closedBy="window"`; `to(Topics.SHIPPING_PICKUP_LISTS)`.
  - `PICKUP_EMIT=updates` (demo): no suppression, so every update leaves.
  - `PICKUP_CHANGELOG=off` (demo): `Materialized.withLoggingDisabled()`, so the open window is lost on recreate.
- **`PickupBatchRoute`**: `from(kafka:edp.shipping.scheduled?groupId=shipping-pickups-camel)`, unmarshal, `aggregate(simple("${body.shipment.carrier}"), pickupStrategy)`, `completionSize({{pickup.batch-size}})` (5), `completionTimeout({{pickup.timeout}})` (10 s), `aggregationRepository(pickupsRepository)`; `closedBy` from `CamelAggregatedCompletedBy`; `to(kafka:edp.shipping.pickup-lists-camel)`.
- Metric: Prometheus check on the Kafka Streams dropped-records metric for the late record (S1 and S4 confirm the name, for example `kafka_stream_task_dropped_records_total`). Fallback: an app counter `pickup.batches{closed_by}` on the Camel side, and the late drop asserted from the output only.

**The scenario.** Event times are set in the payload. Slots of 15 minutes from a fixed base (`2026-11-13T09:00Z`); grace 5 minutes.

- Slot 09:00: FASTLINE 7 shipments, AIRCO 3, produced in one call.
- One FASTLINE shipment with event time 09:10 produced after the 09:15 slot's records (out of order, inside grace).
- Tick to 09:21 (closes 09:00 to 09:15 plus grace).
- One FASTLINE record with event time 09:05 produced after the close (late, past grace).

**mvn tests, at least 10.**

- TTD: one final list per carrier per window under suppression; the out-of-order record inside grace lands in its own slot; the late record is dropped and the dropped-records metric increments; without suppression the window emits more than once (asserts the update stream); hopping windows (15 minutes, advance 5) count each record three times (asserts the reason for tumbling).
- TTD: nothing is emitted for an idle stream however long the wall clock runs (`advanceWallClockTime` does not close an event-time window).
- Camel: size completion at 5; timeout completion for the remainder; a batch per carrier never mixes carriers; the repository holds the open batch.

**verify.sh (about 22 checks).**

1. P: service ready; Streams RUNNING; Camel group assigned.
2. P: topics with 1 partition.

Slot 09:00:

3. After the 10 records and a tick, the Streams output holds exactly one list for FASTLINE 09:00 (count 7) and one for AIRCO 09:00 (count 3).
4. Camel: FASTLINE gives one batch of 5 closed by size and one of 2 closed by timeout; AIRCO one batch of 3 closed by timeout. (Timing witness: 15-run rule.)
5. The union of shipment ids is the same on both outputs, and each id appears exactly once per output.

Out of order and late:

6. The 09:10 FASTLINE record produced late but within grace is in the Streams 09:00 list (count 8 after the window closes; the scenario sends it before the tick, S4 orders the steps).
7. On the Camel output it is in whatever batch was open at arrival (the batch's other ids are from the 09:15 slot).
8. The record past grace appears on no Streams list.
9. It appears on a Camel batch.
10. The service log or `/q/health` shows no error for the dropped record (it is dropped by design, not failed).

Restart (slot 09:30):

11. Three FASTLINE records for slot 09:30; P: changelog end offset grew; one open Camel batch row.
12. Service recreated (empty state dir). Two more FASTLINE records for 09:30, then a tick: the Streams list for 09:30 has count 5.
13. The Camel output holds the 5 ids too (3 from the restored batch, closed by size at 5 with the next two).

Bench (after check 5, before any mutation):

14. 1,000 ShipmentScheduled over four slots and both carriers in one producer call, then a tick: Streams emits exactly 8 lists whose counts equal the oracle (computed by python from the generated input); Camel emits batches whose sizes sum to 1,000 per carrier split. NOTE: lists and batches per path, batches closed by size and by timeout, time from tick to the last Streams list, time from the producer call to the last Camel batch. Three runs.

LGTM:

15. Trace in Tempo.
16. Log in Loki.
17. Prometheus dropped-records check (or the fallback metric).

**Experiments:**

- `PICKUP_EMIT=updates`: several lists per carrier per slot. Expected failures: 3 (exactly one list), 14 (exactly 8). Main negative witness.
- `PICKUP_CHANGELOG=off`: the Streams 09:30 list has count 2 after the recreate. Expected failure: 12.

**Figures.**

- `21-tumbling-vs-hopping`: one slot axis; tumbling puts each shipment in one list; hopping in three.
- `21-window-close-and-grace`: the 09:00 slot, its grace, the in-grace record admitted, the tick closing the window, the late record dropped and counted.
- `21-window-vs-batch`: the same arrivals grouped by slot (Streams) and by size and timeout (Camel).

**Decision table.** Rows: grouping rule (event-time slot; size or quiet time at arrival); when a result leaves (window end plus grace in stream time; size or timeout on the clock); late records (dropped and counted; joins whatever is open); updates (one final result with suppression; one batch per completion); state (changelog; repository table); the measured bench numbers.

**Callout.** Names `PickupTopology`, `PickupBatchRoute`, `PICKUP_EMIT` and `pickup-lists`. Rule: group by event-time windows when the slot is a business fact and lateness must be bounded; batch by size and time when the goal is fewer, fuller messages and arrival order is good enough; never emit window updates to a consumer that expects one answer per window.

**Citations.** Kafka Streams DSL (windowing, suppression, grace), Kafka Streams metrics (dropped records), core concepts (stream time); Camel Aggregate EIP; Hohpe and Woolf, Aggregator; Bellemare ch 8 and ch 9; Lawrence ch 9; CNDP ch 6 "Stream Processing"; EIP Camel ch 13. Backward links to 19 (the GlobalKTable), 20 (event time, the repository).

## Claims rows to plan (claims.md, status pending until evidence)

Chapter 19:

| id | Claim | Evidence |
|---|---|---|
| 19-a | Both forms produce the same enriched record from the same input | verify 4, 5; mvn |
| 19-b | The lookup sees a reference change at once; the table join only after the change reaches the topic | verify 6, 7 |
| 19-c | An output is not revised when the reference changes later | verify 8 |
| 19-d | With the owner down the table join continues and the enricher stalls with lag, then catches up with nothing lost | verify 9 to 12 |
| 19-e | Without `breakOnFirstError` the failed record is committed and lost | experiment; mvn |
| 19-f | A left join keeps a record with no reference; an inner join drops it | verify 13; mvn TTD |
| 19-g | A table join matches keys, not fields | mvn TTD |
| 19-h | A global table is rebuilt from the compacted topic in a fresh container | verify 14 |
| 19-i | The bench numbers (method stated) | verify 15 NOTE, 3 runs |
| 19-src-* | Hohpe Content Enricher; Camel Enrich EIP; Kafka Streams GlobalKTable join; Quarkus interactive queries and health; Lawrence ch 9; Bellemare ch 7, 8 | sources.md |

Chapter 20:

| id | Claim | Evidence |
|---|---|---|
| 20-a | Both forms join a pair in either arrival order | verify 3, 4; mvn |
| 20-b | Close in event time but far apart in arrival: Streams joins, Camel times out | verify 5, 6 (15 runs) |
| 20-c | Far apart in event time but together in arrival: Camel joins, Streams does not | verify 7 to 9 |
| 20-d | Streams emits an unmatched record only after stream time passes window end plus grace; an idle stream emits nothing | verify 10, 11; mvn TTD |
| 20-e | Join inputs must be co-partitioned; otherwise the instance does not run | mvn |
| 20-f | Both forms keep a half-matched order across a recreate (changelog; PostgreSQL repository) | verify 12 to 14 |
| 20-g | An in-memory repository loses it | experiment `AGGREGATION_REPOSITORY=memory` |
| 20-h | A size-based completion completes on a duplicate | mvn |
| 20-i | The Camel timeout is a guess at arrival spread | experiment `READINESS_TIMEOUT_MS` (15 runs or measured rate) |
| 20-j | Bench numbers | verify 15 NOTE |
| 20-src-* | Kafka Streams joins, co-partitioning, time; Camel Aggregate, SQL repository; Hohpe Aggregator; Bellemare ch 8, 9; Lawrence ch 9 | sources.md |

Chapter 21:

| id | Claim | Evidence |
|---|---|---|
| 21-a | Suppression gives one final list per carrier per window | verify 3; experiment `PICKUP_EMIT=updates` |
| 21-b | The Camel batch closes by size or by timeout, per carrier | verify 4 (15 runs) |
| 21-c | Both forms account for every shipment once | verify 5, 14 |
| 21-d | An out-of-order record within grace lands in its own window; Camel puts it in the open batch | verify 6, 7 |
| 21-e | A record past grace is dropped and counted; Camel keeps it | verify 8 to 10, 17; mvn |
| 21-f | Hopping windows count a record size/advance times | mvn TTD |
| 21-g | An idle stream closes no window | mvn TTD; verify tick steps |
| 21-h | The changelog restores an open window in a fresh container; without it the window restarts empty | verify 11 to 13; experiment `PICKUP_CHANGELOG=off` |
| 21-i | Bench numbers | verify 14 NOTE |
| 21-src-* | Kafka Streams windowing, suppression, grace, dropped-records metric; Camel Aggregate; Hohpe Aggregator; CNDP ch 6 | sources.md |

## Spikes (1B, scratchpad only, under the stack lock, Dev Services first; budget about 210 minutes serial)

All spike code under `<scratchpad>/r08-spikes/`. Each live spike starts with `down -v`, then `up --force-recreate --renew-anon-volumes`, and ends with `down -v`.

- **S1 (foundation, 45 min):**
  - One Quarkus 3.40.1.2 app with `quarkus-kafka-streams`, camel-quarkus-kafka (camel-bom 4.22.2), SmallRye Health and `quarkus-micrometer-opentelemetry`: both start and consume; readiness waits for `quarkus.kafka-streams.topics`; liveness reflects the Streams state.
  - Whether Kafka Streams metrics are exported (and the exact Prometheus name in LGTM, for example the task `dropped-records-total`); if not, whether binding `KafkaStreamsMetrics` in an `@Observes KafkaStreams` method is the idiom, or an app meter.
  - Whether a Topology producer guarded by `@LookupIfProperty` disables the pipeline at runtime (only for the fallback of decision 3).
  - `kafka-streams-test-utils` 4.2.1 TopologyTestDriver with `ObjectMapperSerde` and a custom extractor in a plain JUnit test; a `@QuarkusTest` with KafkaCompanion and Dev Services Kafka 4.3.2.
  - In the UBI 10 container as user 185: the state dir under `/tmp` is writable; the RocksDB JNI library loads; peak memory under a 384m cap.
  - `commit.interval.ms=1000` and `statestore.cache.max.bytes=0`: time from input to output.
  - Graceful stop commits; a `--force-recreate` restores from the changelog (log line and duration).
- **S2 (ch 19, 30 min):** GlobalKTable from a compacted topic with a key extractor; leftJoin; interactive query on the global store (`QueryableStoreTypes.keyValueStore()` on a global store name); the delay from producing a reference to the store showing it; a tombstone removes it; Camel `.enrich().simple(...)` with camel-quarkus-http and an AggregationStrategy (confirm the argument order); `breakOnFirstError=true` with redelivery against a stopped owner: the record is retried until the owner returns and nothing is lost, and `false` commits and loses it (3 runs each).
- **S3 (ch 20, 50 min):** `JoinWindows.ofTimeDifferenceAndGrace` with a left join and the payload extractor: unmatched emission only after window plus grace and a tick; the two crafted pairs; the co-partitioning failure with 3 and 2 partitions (exception text, Streams state, health); `PostgresAggregationRepository` in Camel Quarkus with `DataSourceTransactionManager` over the Agroal datasource (no JTA in the app), Flyway-created tables with `bytea`; the `CamelAggregatedCompletedBy` values; completion timeout after a recreate (does the aggregator rebuild its timeout map from the repository keys); what a lone PaymentProcessed does on the Camel path (the route must not emit for it; decide the guard); Camel timeout firing spread over 15 runs.
- **S4 (ch 21, 40 min):** `groupBy` repartition naming; `ofSizeAndGrace` with suppression; the order of steps that makes check 6 deterministic; the late record dropped and the metric value visible in Prometheus within the step; `withLoggingDisabled` loses the open window on recreate; Camel size and timeout batches with the PostgreSQL repository; the 1,000-record bench timing.
- **S5 (tooling, 20 min):** whether the 4.3 console producer can set a record timestamp (expected no; it decides decision 8); topics-init with `--config cleanup.policy=compact`; a tombstone from the console producer (`null.marker`); reading outputs with headers and keys (`--formatter-property print.key=true`); the Streams changelog end offset by `kafka-get-offsets.sh`; the oracle script for the 21 bench; memory peaks of each stack with `docker stats`.

If S1 fails coexistence, decision 3 takes the two-module fallback before any executor starts.

## Steps

| Step | Work | Files | Owner | Depends | Parallel | Stack | Status |
|---|---|---|---|---|---|---|---|
| 0 | Preflight: branch release/r08 created, r07 housekeeping committed (ffc24e0); iteration-plan r08 in progress; save this plan | `_plans/iteration-plan.md`, `_plans/r08-plan.md` | orchestrator | owner OK | none | no | done (branch, housekeeping); plan save pending |
| 1A | Re-survey (complete the table) and source fetches (list below); bibliography rows (Hohpe Content Enricher, Aggregator; Kafka Streams docs pages if the existing Kafka row is not specific enough); `*-src-*` rows | `_plans/versions.md`, `_plans/sources.md`, `bibliography.md` | exec 1A | 0 | 1B, 1C | no | pending |
| 1B | Spikes S1 to S5 | scratchpad | exec 1B | 0 | 1A, 1C | lock, serial | pending |
| 1C | domain-model 2.3.0: the new `Topics` constants and their test; `_plans/domain-model.md` rows 19 to 21 rewritten (below), topic notes, version line | `examples/domain-model/**`, `_plans/domain-model.md` | exec 1C | 0 | 1A, 1B | mvn under lock | pending |
| G0 | Gate: spikes pass or a fallback is chosen; install domain-model 2.3.0; bump every example pom 00 to 18 to 2.3.0 and `mvn -B verify` each under the lock; "G0 decisions" section appended to this plan | this plan, example poms | orchestrator | 1A to 1C | none | mvn under lock | pending |
| 2A | Ch 19: code, tests, Containerfiles, compose, README, example page, figure specs and outputs, chapter draft without numbers | `examples/19-*/**`, `_docs/19-*.md`, `_example_pages/19-*.md`, `diagrams/specs/19-*.py`, `diagrams/19-*.*` | exec 2A | G0 | with 2B, 2C | mvn under lock | pending |
| 2B | Ch 20: same set, Flyway repository tables | `examples/20-*/**`, `_docs/20-*.md`, `_example_pages/20-*.md`, `diagrams/specs/20-*.py`, `diagrams/20-*.*` | exec 2B | G0 | with 2A, 2C | mvn under lock | pending |
| 2C | Ch 21: same set, plus the bench oracle script | `examples/21-*/**`, `_docs/21-*.md`, `_example_pages/21-*.md`, `diagrams/specs/21-*.py`, `diagrams/21-*.*` | exec 2C | G0 | with 2A, 2B | mvn under lock | pending |
| 2D | Earlier-chapter consistency: grep 02, 03, 04, 06, 12, 14 and 17 for sentences 19 to 21 contradict (list in Findings); claims rows drafted | `_plans/claims.md`, earlier `_docs` only if needed | orchestrator | G0 | yes | no | pending |
| 3 | Runtime windows, serial, exclusive, 120 min cap each, order 20, 21, 19. Verify N/N twice from clean, then three bench runs; every experiment once; the 15-run loop for each timing witness (20: checks 6, 10; 21: check 4; 19: check 11) from fresh containers, failures counted; numbers filled; numbering frozen | as 2A to 2C | orchestrator | 2A to 2C | serial | exclusive | pending |
| 4 | Orchestrator re-run of every prose experiment, logged with the failing check numbers; grep "check N" after any renumbering | `_plans/verification-log.md` | orchestrator | 3 | serial | yes | pending |
| 5 | Final serial run 00 to 21 (fresh containers each, one flock, no `edp-*` left between runs), including each prose experiment of 19 to 21; static gates; claims and log; per-chapter commits with `python3 scripts/host_ids.py check` before each | claims, log | orchestrator | 4 | serial | yes | pending |
| 6 | Opus validation (max 2 repair rounds): claims audit; check-bite audit; timing-witness audit (15 runs or rate stated); bench consistency (table, figure, prose from one bench); 7-gram originality against the Kafka Streams and Camel pages, EIP Camel ch 12 and 13, CNDP ch 6; fresh clone; word counts; figures byte-identical | none | validator | 5 | none | yes | pending |
| 7 | Ship: PR, CI green, squash on owner OK, tag r08.0, notes-only release, delete the branch; PRD rows 19 to 21 rewritten to the domain, roadmap and status; iteration-plan done; CLAUDE.md decisions entry | as listed | orchestrator | 6 | none | no | pending |

Executors:

- They make no git writes. The orchestrator commits, with `python3 scripts/host_ids.py check` first.
- They do not edit `claims.md`, `verification-log.md`, `CLAUDE.md`, `examples/_infra`, `validate.py` or `domain-model` (1C only).
- `mvn verify` runs only under `flock -w 7200 <scratchpad>/stack.lock`. Step 2 executors run no compose and no `verify.sh`.
- Live stacks run only in 1B, the step 3 windows and steps 4 to 6, one at a time, under `stack-guard.sh` and the flock.
- Every `verify.sh` starts with `docker compose down -v --remove-orphans`, then `up -d --build --force-recreate --renew-anon-volumes --wait`, and ends with `down -v` unless `KEEP=1`. In-run recreates use `up -d --no-deps --force-recreate --wait <svc>`.
- No host identifiers in any file: `<repo>` and `~/` only. Captured logs pass through `host_ids.py filter` before anything is quoted.

**domain-model 2.3.0 (1C).**

- `Topics` constants, none in `ALL`, each with Javadoc:
  - `SHIPPING_CARRIERS = "edp.shipping.carriers"`: compacted carrier reference records keyed by carrier, published by shipping-service.
  - `SHIPPING_SCHEDULED_ENRICHED = "edp.shipping.scheduled-enriched"` and `SHIPPING_SCHEDULED_ENRICHED_CAMEL = "edp.shipping.scheduled-enriched-camel"`.
  - `SHIPPING_ORDER_READINESS = "edp.shipping.order-readiness"` and `SHIPPING_ORDER_READINESS_CAMEL = "edp.shipping.order-readiness-camel"`.
  - `SHIPPING_PICKUP_LISTS = "edp.shipping.pickup-lists"` and `SHIPPING_PICKUP_LISTS_CAMEL = "edp.shipping.pickup-lists-camel"`.
- Test `streamOutputTopicsAreOutsideTheCatalog` (regex `edp\.[a-z]+\.[a-z-]+`, not in `ALL`, distinct).
- `_plans/domain-model.md`: version line 2.3.0; a catalog note naming the seven topics and saying that the `-camel` twins exist only because both forms run side by side in chapters 19 to 21; a note that Kafka Streams internal topics (changelog, repartition) are named by the application id and are not in `Topics`; a note that `CarrierReference` (mode ROAD or AIR, tracking URL template on a `.example` host), ReadyToShip, PaymentOverdue and PickupList are payload records about existing entities.

**domain-model.md rows (1C):**

- **19:** "ShipmentScheduled enriched with the carrier's mode and tracking URL by notification-service; shipping-service owns the carrier reference data (its table, its lookup endpoint and the compacted `edp.shipping.carriers` topic); a Camel Content Enricher looks it up per record, a Kafka Streams left join against a GlobalKTable keeps a local copy; a reference change and an owner outage show the difference; decision table".
- **20:** "shipping-service releases an order when InventoryReserved and PaymentProcessed have both arrived: a Kafka Streams left join by event time within 30 minutes plus grace, and a Camel Aggregator correlated by order id with a completion timeout by arrival time, its state in PostgreSQL; an unmatched reservation becomes PaymentOverdue; two crafted pairs where the forms disagree; state kept across a restart; decision table".
- **21:** "ShipmentScheduled grouped per carrier into pickup lists from one input: Kafka Streams tumbling 15-minute event-time windows with grace and suppression (one final list and count per carrier per slot, late records dropped and counted) and a Camel Aggregator batching by size and completion timeout; decision table".

## Acceptance criteria

**19**:

- verify N/N, N >= 18, from clean twice; mvn green with >= 10 tests across both modules, including the TTD inner-join and key-mismatch tests asserting the bad outcome.
- Checks 6 and 7 show the lookup and the table join disagreeing on O-1903 and agreeing on O-1904, each after a sync read.
- With the owner stopped, the Streams output grows and the Camel group shows lag 1 for 20 s; after the owner returns, the Camel output holds O-1905 and lag is 0.
- `CAMEL_BREAK_ON_FIRST_ERROR=false` fails check 12 and no other, in step 3 and step 4.
- Check 11 green in 15 of 15 runs, or the prose states the measured rate.

**20**:

- verify N/N, N >= 18, from clean twice; mvn green with >= 12 tests, including co-partitioning and the `completionSize(2)` duplicate bug.
- The two crafted pairs give opposite outcomes per path (checks 5 to 9). Check 6 green in 15 of 15 runs, or the prose states the measured rate.
- PaymentOverdue on the Streams path appears only after a tick (check 11 asserts silence before it).
- Both paths deliver ReadyToShip for O-2006 across a `--force-recreate`; `AGGREGATION_REPOSITORY=memory` fails check 13 on the Camel side only.
- The chapter states plainly that the Camel form approximates the window by arrival time, not event time.

**21**:

- verify N/N, N >= 16, from clean twice; mvn green with >= 10 tests, including hopping and idle-stream tests.
- Exactly one Streams list per carrier per slot (check 3, and 8 lists in the bench); `PICKUP_EMIT=updates` fails checks 3 and 14.
- The late record is on no Streams list, the dropped-records metric is at least 1 in Prometheus, and the record is on a Camel batch.
- The open 09:30 window survives a recreate (count 5); `PICKUP_CHANGELOG=off` fails check 12.
- Check 4 green in 15 of 15 runs, or the prose states the measured rate.

**Every chapter**:

- Prose words <= 2,500 by `validate.py` (target about 2,000, never padded); exactly one "Explore with your agent" callout in the house form; >= 2 figures from specs (outputs never hand-edited, regenerated byte-identically).
- A decision table whose numbers come only from the chapter's one bench in the final-run logs (3 runs, median of medians with the range).
- No em or en dashes, versions, test names, self-reference, forward references, aphorisms or "deliberate(ly)". No "manifest" anywhere.
- `part: processing-and-coordinating-streams`.
- UBI 10 Containerfile (`1.24-20` or the 1A tag) per the `Dockerfile.jvm` convention; env conventions (`KAFKA_BOOTSTRAP_SERVERS` for Kafka Streams, `CAMEL_COMPONENT_KAFKA_BROKERS`, `QUARKUS_DATASOURCE_*`, `QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT`); `/q/health/ready` healthcheck on every app service.
- LGTM traces, metrics and logs, with exactly one Prometheus check with `service_name`.
- Topics only from `Topics` (2.3.0). Carriers only FASTLINE and AIRCO; reference URLs on `.example` hosts. No banned names (`validate.py`).
- Both forms run on the same input in the same run, and the chapter says a real service runs one.
- Every timing or negative witness named in prose: 15 of 15 green runs logged, or its measured rate stated.
- Demo switches labelled as demo code in code comments, README and prose.
- Every claim row verified or cut; every prose experiment re-run in step 4 and present in the final logs; no experiment cited unless the final script ran it.

**Iteration**:

- `python3 scripts/validate.py` ok; `scripts/voice-scan.sh` 0 ban-tier; `scripts/idiomatic-scan.sh` 0 high; `python3 scripts/host_ids.py check` 0 hits before every commit; `scripts/site-build.sh` builds.
- Every example pom 00 to 21 on domain-model 2.3.0 and `mvn -B verify` green (CI selects all examples because domain-model changed).
- Final serial run 00 to 21 logged in `_plans/verification-log.md`: mvn and verify, every example green, no `edp-*` left.
- versions.md records the Kafka Streams decision (decision 1) with its reason.
- CI green; tag r08.0 and release; PRD rows 19 to 21 in domain wording; iteration-plan and CLAUDE.md decisions updated.

## Sources to fetch and confirm (1A)

- Hohpe and Woolf: Content Enricher (`https://www.enterpriseintegrationpatterns.com/patterns/messaging/DataEnricher.html`), Aggregator (`.../Aggregator.html`); problem and solution sentences recorded, not copied into prose.
- Kafka Streams 4.2 docs: DSL (joins, `JoinWindows.ofTimeDifferenceAndGrace`, GlobalKTable bootstrapping, windowing, suppression, `EmitStrategy.onWindowClose` as the alternative); core concepts (event time, stream time, out-of-order handling, what happens after grace); `TimestampExtractor`; metrics (`dropped-records-total` and its level); `kafka-streams-test-utils` (TopologyTestDriver, `advanceWallClockTime`); the default `group.protocol` and KIP-1071 status; config `processing.guarantee` default.
- Quarkus guides: Kafka Streams (confirmed here; record it), Micrometer (Kafka binder; Streams coverage unconfirmed), Kafka Dev Services (topic partitions), KafkaCompanion testing page.
- Camel 4.22.x: Enrich EIP and Aggregate EIP (confirmed here; record them), SQL component aggregation repository (confirmed; record), Kafka component `breakOnFirstError` and `KafkaConstants.KEY`; Camel Quarkus SQL and HTTP extension pages.
- Lawrence ch 9 and Bellemare ch 7, 8, 9 (TOC rows exist; titles only).
- EIP Camel ch 12 and 13, CNDP ch 6 (sibling links, URLs from the sibling sites).

## Risks

| Risk | Detection | Response |
|---|---|---|
| Kafka Streams and a Camel Kafka consumer conflict in one Quarkus app (classpath, health, startup order) | S1 | two modules per example (decision 3 fallback), same verify shape |
| Kafka Streams metrics not exported under micrometer-opentelemetry | S1 | bind `KafkaStreamsMetrics` on the `KafkaStreams` startup event, or an app meter; the Prometheus check names what S1 found |
| RocksDB JNI fails on ubi10/openjdk-25 or exceeds the memory cap | S1 | in-memory stores (`Stores.inMemory...`) with the changelog kept; the restart checks still hold; record the reason |
| `PostgresAggregationRepository` needs JTA or Spring wiring that clashes with Quarkus | S3 | `JdbcAggregationRepository` with an explicit `DataSourceTransactionManager`; else hold the Camel side on the memory repository with `completeAllOnStop`, prose states the loss and 20-f becomes Streams-only |
| Camel completion timeout after a recreate does not rearm from the repository | S3 | the check waits for the next arrival or the recovery task; prose states the behaviour observed |
| Camel arrival-time witnesses flaky (checker interval 1 s, producer timing) | S3, S4; 15-run loop | wide margins (20 s apart against a 10 s timeout); preconditions read the open groups before the competing input; otherwise the measured rate in prose |
| Stream time not advancing as planned (cache, commit interval, task assignment) | S1, S3, S4 | 1 partition per topic, cache 0, commit 1 s, a tick plus a sync read before every window check |
| GlobalKTable update race in 19 check 7 | S2 | sync read of the global store before the next ShipmentScheduled |
| `breakOnFirstError` semantics differ in Camel 4.22.2 (commit or seek) | S2 | follow the observed behaviour; if it loses the record anyway, the guard becomes a dead letter route and the witness changes accordingly |
| 15-run loops take too long (about 5 min per run, three chapters) | step 3 budget | loops run in background under the flock with fresh containers; 120 min cap per window; if a cap is hit, the prose states the measured rate from the runs done |
| Word budget (two forms, a decision table and four short sections per chapter) | draft count at step 2 | the Camel or Streams code shown once, the other described in a table row; no restated mechanics |
| Domain-model bump breaks an older example build | G0 sweep of 00 to 18 | fixed at G0 before step 2 |
| Banned names via PRD copy (`Manifest`) | `validate.py` | domain wording only |
| Overlap with EIP Camel ch 12 and 13 and CNDP ch 6 | validator 7-gram | own scenario and code; link the siblings |
| Forward references (19 naming windows; 20 naming pickup lists) | validator read | backward only |
| Host identifiers in logs or the plan | `host_ids.py check` before each commit | `host_ids.py filter` on captured logs |
| Stack contention | flock; `stack-guard.sh` | serial windows only |

## Owner decisions (recommendations)

1. **Kafka Streams version.** Recommendation: **keep the platform's 4.2.1** (`kafka-streams`, `kafka-streams-test-utils`, matching kafka-clients 4.2.1) against the 4.3.2 broker; RocksDB 10.1.3 as Kafka Streams declares it. Same rule as kafka-clients since r01: the book does not override platform-managed client libraries without a defect. Alternative: override to 4.3.2 to match the broker (no feature these chapters need).
2. **Test approach.** Recommendation: **TopologyTestDriver for every event-time, window, grace and join-expiry claim** (deterministic, no broker, wall clock irrelevant), **`@QuarkusTest` with Dev Services and KafkaCompanion** for the wiring and the Camel routes, and `verify.sh` for the live behaviour (restart, outage, divergence by arrival).
3. **One app per example.** Recommendation: **one Quarkus service hosting the topology and the Camel route side by side**, each with its own group and output topic, if S1 passes. Fallback: two modules (a Streams app and a Camel app) on the same compose stack. Profile switching is the last resort.
4. **Output topics.** Recommendation: **one output topic per form** (`...` and `...-camel`), so the Streams code stays pure DSL and each output is checked on its own. Alternative: one topic with a path header (Processor API on the Streams side; duplicate records for consumers).
5. **Ch 19 reference data location.** Recommendation: **shipping-service owns it** (PostgreSQL table, `GET /carriers/{carrier}`, and the compacted `edp.shipping.carriers` topic). The Camel Content Enricher looks it up over HTTP per record; Kafka Streams joins a GlobalKTable built from the topic. `verify.sh` stands in for the owner's publication to the topic (chapter 16 precedent); the prose names the outbox or CDC as the production feed.
6. **Ch 19 cache.** Recommendation: **no cache** in the example (a Caffeine-cached lookup is a third freshness model and a third experiment). If the owner wants it, add `@CacheResult` on a REST client bean with a TTL as one experiment and one table row.
7. **Ch 19 join type.** Recommendation: **KStream-GlobalKTable left join** with a key extractor from `shipment.carrier`. No co-partitioning, full copy per instance, fine for two rows. KTable with `selectKey` rejected (a repartition for nothing); mvn shows that a key-only table join matches nothing.
8. **Event time.** Recommendation: **event time from the payload** (`occurredAt`) through one `PayloadTimestampExtractor`, because records can be published late (outbox, replay) and the console producer cannot set timestamps (S5). Alternative: producers set the record timestamp to the business time and Streams uses the default extractor.
9. **Partitions.** Recommendation: **1 partition per topic** in 19 to 21, so one tick moves stream time for the whole example; co-partitioning proven by an mvn test with 3 and 2 partitions.
10. **Ch 20 join and window.** Recommendation: **left join, 30-minute event-time window, 5-minute grace**; an unmatched reservation becomes **PaymentOverdue** after window plus grace. Camel: Aggregator on the record key with a **completion predicate over the kinds present** (not `completionSize(2)`), `completionTimeout` 10 s, completed-by timeout gives PaymentOverdue. The two crafted pairs (O-2003, O-2004) show the disagreement both ways.
11. **Ch 20 aggregation repository.** Recommendation: **`PostgresAggregationRepository`** (camel-sql) on the example's PostgreSQL, with Flyway-created tables, so both forms survive a recreate. `AGGREGATION_REPOSITORY=memory` is the negative witness.
12. **Ch 21 input.** Recommendation: **one input, ShipmentScheduled, for both forms**, producing the same `PickupList` type: Streams per carrier per 15-minute event-time slot (its `count` is the per-carrier, per-window count), Camel per carrier by size 5 or 10 s of quiet. This meets Q6 (same input). The domain row is rewritten; ShipmentDelivered counts stay with Appendix C. Alternative: keep row 21 as written (Streams on ShipmentDelivered, Camel on ShipmentScheduled), which breaks the "same input" rule.
13. **Ch 21 windows and emission.** Recommendation: **tumbling** (`ofSizeAndGrace`, 15 min, grace 5 min) with **`suppress(untilWindowCloses(unbounded()))`**; hopping shown only in an mvn test; `PICKUP_EMIT=updates` and `PICKUP_CHANGELOG=off` as demo switches. `EmitStrategy.onWindowClose()` noted as the alternative if 1A confirms it in 4.2.
14. **Ch 21 Camel repository.** Recommendation: the **same PostgreSQL repository as 20** (refer back), so the restart check covers both forms. Alternative: memory repository and one sentence pointing back to 20.
15. **Topics and domain-model.** Recommendation: **domain-model 2.3.0** with the seven constants above (none in `ALL`), every example pom bumped at G0 (r05 precedent). Alternative: example-local constants, which breaks "topics only from `Topics`".
16. **Observability.** Recommendation: one Prometheus check each: 19 `shipment_enrichments_total{path="camel"}`; 20 `order_readiness_total{outcome="overdue",path="camel"}`; 21 the Kafka Streams dropped-records metric (name from S1 and S4), else an app counter. OTel traces and logs as in earlier examples.
17. **Demo switches** (labelled demo code): `CAMEL_BREAK_ON_FIRST_ERROR` and `PUT /carriers/{carrier}`, `GET /demo/carriers/{carrier}` (19); `AGGREGATION_REPOSITORY`, `READINESS_TIMEOUT_MS` (20); `PICKUP_EMIT`, `PICKUP_CHANGELOG` (21).
18. **Examples and slugs.** `examples/19-enrich` (notification-service, shipping-service; package `...enrich`), `examples/20-join` (shipping-service; `...join`), `examples/21-aggregate` (shipping-service; `...pickups`); chapter slugs the same. Order ids `O-19xx`, `O-20xx`, `O-21xx`.
19. **Citations.** Hohpe and Woolf Content Enricher and Aggregator pages (new bibliography rows); Lawrence ch 9; Bellemare ch 7, 8, 9 by title; Kafka Streams, Quarkus and Camel docs as the API authority; EIP Camel ch 12 and 13 and CNDP ch 6 as sibling links.
20. **Runtime window order:** 20 (most moving parts), then 21, then 19.
21. **At ship:** rewrite PRD rows 19 to 21 in domain wording, update the roadmap and status, add a CLAUDE.md decisions entry for r08.

### Critical files for implementation

- `<repo>/examples/16-cqrs-and-projections/verify.sh` (helpers: produce with headers, group lag from one describe, preconditions, LGTM checks)
- `<repo>/examples/16-cqrs-and-projections/compose.yaml` and `pom.xml` (topics-init, env, camel-bom import, health)
- `<repo>/examples/02-event-notification/notification-service/src/main/java/com/patterncatalyst/shipping/eventnotification/notification/OrderPlacedNotificationRoute.java` (the HTTP fetch-back that 19's enricher builds on)
- `<repo>/examples/domain-model/src/main/java/com/patterncatalyst/shipping/Topics.java` and its test
- `<repo>/_plans/domain-model.md` (rows 19 to 21) and `<repo>/scripts/validate.py` (`BANNED_DOMAIN`, word limit, fresh-start rule)
