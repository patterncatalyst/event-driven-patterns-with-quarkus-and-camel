---
layout: plan
title: r03 plan
render_with_liquid: false
---

# r03 plan: chapters 05 to 07, plus the Bellemare back-fill (Opus planner, 2026-11-10)

I read CLAUDE.md, the r02 plan and both owner reviews, domain-model.md, sources.md, versions.md, verification-log.md, the claims.md format, validate.py (word counter, VERSION_RE, callout and self-reference checks), _infra, example 04 (aggregator, compose, poms, properties), the References sections of chapters 02 to 04, the r02 validator findings, the common executor brief from r02 (scratchpad `ch-common-r02.md`), the PRD, EIP Camel appendices 32, 33 and 34 and their examples, the Camel Kafka catalog (Camel 4.22.1 through quarkus-camel-bom 3.40.1.2), the Quarkus docs search, the jars in ~/.m2, and the Kafka 4.3 producer, consumer and group config pages.

## What I found

- **Camel Kafka 4.22.1 has no share-group support.** The catalog has no share or groupType option. It does have `groupProtocol` (classic or consumer; KIP-848), `groupRemoteAssignor`, `consumersCount`, `maxPollRecords`, `fetchMinBytes`, `fetchWaitMaxMs`, `maxPartitionFetchBytes`, `maxPollIntervalMs`, `lingerMs`, `producerBatchSize`, `compressionCodec`, `requestRequiredAcks`, `enableIdempotence`, `kafkaClientFactory` and `additionalProperties`.
- **The Camel catalog contradicts itself on `lingerMs`.** The listed default is 5, but the description text says "defaults to 0". The Kafka 4.3 producer docs say the default is 5 (changed by KIP-1030). Chapter 05 must follow Kafka and must not repeat Camel's text. EIP appendix 33 also says the default is 0 and is out of date.
- **The Kafka 4.3 defaults are already safe:** `acks=all`, `enable.idempotence=true`, `linger.ms=5`, `batch.size=16384`, `compression.type=none`.
- **Quarkus 3.40.1.2 carries SmallRye Reactive Messaging Kafka 4.37.0, and that jar already has share-group support.** It contains `KafkaShareGroupSource`, `queues/ShareGroupAcknowledgement` (accept, release, reject) and the attribute `share-group`. It logs "Share group mode does not support multiple partitions. Using single consumer", which means one share consumer per channel, so you scale with replicas.
  - The Quarkus guide documents `mp.messaging.incoming.<ch>.share-group=true`, but only in the 4.0.0.Beta1 docs. It has to be proven on 3.40.1.2.
- **kafka-clients 4.2.1** (the platform version) has `KafkaShareConsumer`, `AcknowledgeType`, `share.acknowledgement.mode` and `share.acquire.mode`.
- **Kafka 4.3 share-group settings:** the group configs are `share.record.lock.duration.ms` (default 30 s), `share.delivery.count.limit` (default 5), `share.auto.offset.reset` (default latest) and `share.session.timeout.ms`. The consumer side has `share.acquire.mode` (default BATCH_OPTIMIZED).
- **Dev Services can pass broker env** with `quarkus.kafka.devservices.container-env."KEY"`, which the share-group tests need. I saw this only in the 4.0 docs, so it must be confirmed on 3.40.
- **Better sources exist than the ones in sources.md.** Strimzi has dedicated tuning posts, both HTTP 200: "Optimizing Kafka producers" (2020-10-15, /blog/2020/10/15/producer-tuning/) and "Optimizing Kafka consumers" (2021-01-07, /blog/2021/01/07/consumer-tuning/). They are not in the PRD list and fit 05 and 06 better than the "simple producer/consumer" posts.
  - Also HTTP 200: KIP-848, KIP-1030, Hohpe and Woolf Competing Consumers and Polling Consumer.
- **The PRD §5 rows for 05 to 07 still use the freight domain** (HandlingEvent, CustomsDeclaration) and have no Bellemare column entries.
- **Chapter 04's References do not cite Lawrence chapter 2** although the mapping lists it. This is outside scope; I note it only.

## Approach

**Three gates.**
- **G0: spikes and sources.** Before any chapter code, three short spikes in the scratchpad (not the repo) prove the risky mechanisms. In parallel, the docs-only work runs: the Bellemare back-fill and the source fetches.
- **G1: authoring.** Three executors work in parallel on disjoint files. They write code, tests, chapter drafts and figures, and run `mvn verify` under the lock with short holds.
- **Runtime windows.** Each executor then gets one exclusive, serial runtime window, granted by the orchestrator, to iterate `verify.sh`, run experiments and run the benchmark.
- **Final serial verify and ship.** The orchestrator runs everything serially from clean, then Opus validates (2 repair rounds at most) and the iteration ships.

**Measurement principle for 05 and 06.**
- verify.sh asserts only on structural relations. Each relation has a control in the same script that makes the check bite. Examples:
  - records per request, batched against unbatched;
  - batch codec and producer id in the broker's log;
  - idle members when consumers outnumber partitions;
  - partitions revoked when a member joins;
  - duplicate attempts when the poll interval is exceeded;
  - fetch count under a fixed trickle.
- Throughput, latency, drain time and lag numbers come only from a separate `bench.sh`:
  - repeated, interleaved runs (5 per profile);
  - median and min to max range;
  - settings and host class recorded;
  - written to a scrubbed results file;
  - cited in prose as relative comparisons ("about N times") with the run's settings.

**Rejected alternatives:**
- Build 05 to 07 strictly one after another: no contention, but roughly three times the elapsed time for prose, figures and tests that need no stack.
- Orchestrator runs every verify.sh while executors only write code: executors cannot iterate verify.sh, and the r02 lesson was that untried checks fail late.
- Three executors sharing the lock freely (the r02 model): this caused the hour of contention.
- One shared example for 05 and 06: chapters must stand alone, and the service sets differ (order-service publishing against notification-service consuming).
- Assert throughput or latency thresholds in verify.sh: wall clock on a shared host is not repeatable.
- An external load tool such as `kafka-producer-perf-test` for 05: it bypasses the Camel producer, which is the thing being tuned. It is used only to seed 06 and 07.
- Wait for Camel share-group support in 07: it does not exist in the pinned Camel, and "fix now" rules out deferring the chapter.
- A Camel route with an invented `groupType=share` option (EIP's illustrative sketch): it would not run.

## Per-chapter design

### 05 Optimizing publishers (`examples/05-optimizing-publishers`, Part 1)

**Services.** order-service only (port 8080), with no database. Kafka and LGTM come from _infra. A `topics-init` container creates `edp.orders.placed` with 3 partitions, as in ch04.

**Code** (one route per RouteBuilder):
- `OrderPlaced` payload record (per example), built from domain `Order`.
- Three publish routes on the same topic. This is comparison code, tested on the same input. Each route has its own `clientId`, so the metrics separate per profile:
  - `UnbatchedPublishRoute`: `lingerMs=0&producerBatchSize=0`, a deliberately bad control.
  - `DefaultPublishRoute`: no tuning options, so it runs with Kafka's defaults.
  - `TunedPublishRoute`: `lingerMs=20&producerBatchSize=131072&compressionCodec=lz4`, values from `application.properties`.
- `FlashSaleResource`, `POST /flash-sale?profile=&orders=&concurrency=`. It is a load driver that places N valid orders through `ProducerTemplate` into `direct:publish-<profile>`. It uses virtual threads with a semaphore for concurrency and returns `{orders, elapsedMs, ordersPerSecond}`. A Micrometer Timer `edp.publish.latency`, tagged by profile with percentiles, measures send-to-ack per record.
- `KafkaClientMetricsFactory`: a `DefaultKafkaClientFactory` subclass, registered as the `kafkaClientFactory` bean. It binds Micrometer's `KafkaClientMetrics` to each producer or consumer that Camel creates and closes the binder with the client. Camel has no built-in client-metrics binding, and the Quarkus Kafka binder only sees SmallRye clients. The spike confirms this; if Camel Quarkus turns out to have a binding, use it instead.
- Metrics leave through `quarkus-micrometer-opentelemetry` to LGTM.

**What is measured or shown:**
- The producer metrics `record-send-total`, `request-total` and `outgoing-byte-total` give records per request and bytes per record. `batch-size-avg`, `compression-rate-avg`, `record-queue-time-avg` and `request-latency-avg` give the explanation.
- The driver gives throughput and p50/p99 latency.
- The key deep points beyond EIP 33:
  1. Kafka's defaults already batch, are idempotent and use `acks=all`.
  2. `linger.ms` only helps when sends overlap. A single synchronous caller (concurrency 1) gets about one record per request at any linger value, and each record waits up to linger.
  3. Setting `acks=1` without explicitly setting idempotence silently turns idempotence off. This shows in the log as producerId -1. Setting it explicitly with `acks=1` is a ConfigException at startup, per the Kafka docs and the Camel catalog.
  4. On one broker, `acks=all` and `acks=1` wait for the same single replica. State this plainly and back durability claims with the docs only, not with a run.

**verify.sh, deterministic part** (about 18 checks):
1. Infra and app are healthy.
2. Each profile bursts N=20,000 orders at concurrency 200. For every profile, the topic's end offsets grow by exactly N (summed across partitions).
3. Precondition: Prometheus shows `record_send_total` equal to N for each `client_id`.
4. **Relation (the negative witness):**
   - The unbatched profile has at most 3 records per request. This is structural: one record per batch, three partitions, one broker.
   - The tuned profile has at least 5 times the unbatched records per request.
   - The check fails if the endpoint tuning is not applied, for example a wrong option name or a dropped property. That was the real failure mode in r02-style code.
5. A concurrency-1 burst on the tuned route has at most 1.5 records per request (linger does not batch a single caller).
6. Tuned bytes per record are less than default bytes per record. `kafka-dump-log.sh` on the segment shows `compresscodec: lz4` batches.
7. `kafka-dump-log.sh` shows batches with producerId at least 0 for the default profile. After a recreate with `CAMEL_COMPONENT_KAFKA_REQUEST_REQUIRED_ACKS=1` (component level, which affects only the untuned route), new batches show producerId -1.
8. One Tempo trace shows `POST /flash-sale`. A Loki log line is present.

**Experiments** (run and record each):
- Remove `producerBatchSize=0` from the unbatched route: check 4 fails or narrows; record the numbers.
- Raise the tuned route's concurrency from 1 to 200: check 5 flips.
- Set `enableIdempotence=true` explicitly with `acks=1`: startup fails with a ConfigException.
- Codec `zstd` and `gzip` in bench: bytes per record and throughput.
- `linger.ms` 0, 5, 20 and 100 at concurrency 200 in bench.

**bench.sh:**
- Warm-up burst discarded, then 5 rounds interleaved A, B, C.
- Records median and range for orders/s, p50, p99 and records per request.
- Header: N, concurrency, payload bytes, partitions, single broker, `nproc` class, load average before and after, lock held, nothing else running.
- Output to `examples/05-optimizing-publishers/results/bench-<date>.csv`, scrubbed with `host_ids.py scrub`.

**Figures:**
- `05-batch-fill`: per-partition batches fill until `batch.size` or `linger.ms`, and one request carries batches for several partitions.
- `05-sequential-vs-concurrent`: one synchronous caller never fills a batch, many callers do.
- Optional `05-acks-idempotence`: the producer id and sequence on a retry.

**Citations:**
- Strimzi "Optimizing Kafka producers" (if approved) and "Developing Kafka client applications: A simple producer" (confirmed).
- Kafka 4.3 producer configs.
- KIP-1030 (linger default).
- Bellemare ch 11, Basic Producer and Consumer Microservices.
- Camel Kafka component.
- No Hohpe and Woolf pattern fits; cite none.

**Layout:** a topic chapter, no `## Pattern:` heading:
- `## The problem`: the flash sale.
- `## How the producer sends`.
- `## Batching: linger.ms and batch.size`.
- `## Compression`.
- `## acks and idempotence`.
- `## How Quarkus and Camel model it`.
- `## Measuring a publisher`: the method, then a results table with median and range.
- `## Build, run, observe`.
- `## When not to tune`.
- `## References`.

Exactly one callout, naming `TunedPublishRoute`, `FlashSaleResource` and `KafkaClientMetricsFactory`.

### 06 Optimizing subscribers (`examples/06-optimizing-subscribers`, Part 1)

**Services:**
- notification-service (8080), with Panache and Flyway. A `notifications.notification` table has a unique `(group_id, topic, partition, offset)` and an `attempts` counter, upserted with `ON CONFLICT ... attempts = attempts + 1`, plus `consumer` (Kafka client id) and `processed_at`.
- A second replica `notification-2`, unpublished, under a compose profile `scale`.
- `topics-init` creates three catalog topics with 3 partitions each: `edp.orders.placed`, `edp.payments.processed`, `edp.shipping.scheduled`, so 9 partitions.
- A `seeder` (the apache/kafka image running `kafka-producer-perf-test.sh --payload-file`) writes JSON payloads from committed `seed/*.jsonl`. It runs once, before the consumer starts.

**Code:**
- `NotificationConsumeRoute`: `from("kafka:" + three topics + "?groupId={{notify.group}}")`. Every tuning option comes from component properties set by `CAMEL_COMPONENT_KAFKA_*` env: `maxPollRecords`, `fetchMinBytes`, `fetchWaitMaxMs`, `maxPollIntervalMs`, `consumersCount`, `groupProtocol`.
- Processing: a `NotificationWriter` writes the row. `NOTIFY_WORK_MS` adds a fixed per-record cost (a stand-in for template rendering, stated as a stand-in).
- `NOTIFY_GROUP` is set per run, so every run re-drains the identical seeded backlog from earliest. Input is identical across runs, which makes runs repeatable.
- Kafka consumer metrics come through the same `KafkaClientMetricsFactory`. A Timer `edp.notification.event.age` measures now minus the record timestamp.

**What is measured or shown:**
- `fetch-total`, `records-per-request-avg`, `fetch-size-avg` and `records-lag-max` from the client metrics.
- LAG from the broker's view with `kafka-consumer-groups.sh --describe`.
- Drain time from the rows.
- Deep points beyond EIP 32:
  1. `max.poll.records` bounds a poll, not a fetch. The consumer buffers fetched data.
  2. Fetch sizing matters when the consumer has caught up and traffic trickles, not for a backlog.
  3. Concurrency is capped by partitions. Each `consumersCount` thread is a separate group member, so extra members sit idle.
  4. If work per poll exceeds `max.poll.interval.ms`, the member is evicted and the batch is re-delivered (duplicates).
  5. The classic protocol with Camel's default RangeAssignor revokes every partition when a member joins. `groupProtocol=consumer` (KIP-848) moves only the partitions that change owner.

**verify.sh, deterministic part** (about 20 checks):
1. Seeded N per topic. Precondition: LAG before start equals 3N.
2. Default run: rows equal 3N, final LAG is 0, and every row has 1 attempt.
3. `CONSUMERS_COUNT=12`: the members listing shows 12 members and exactly 3 with no partitions. `CONSUMERS_COUNT=3`: rows were processed by exactly 3 distinct client ids.
4. **Negative witness, the poll-interval guard:**
   - Settings: `NOTIFY_WORK_MS=50`, `MAX_POLL_INTERVAL_MS=5000`, small N (600).
   - With `MAX_POLL_RECORDS=500`, some rows have more than 1 attempt and the log shows the poll-timeout leave.
   - With `MAX_POLL_RECORDS=20`, every row has 1 attempt.
   - 500 × 50 ms = 25 s against a 5 s interval, so the margin is large.
5. Fetch sizing: the seeder trickles at a fixed rate (300 records/s for 20 s) while the consumer is caught up (precondition: LAG under 300 at the start).
   - Precondition and relation: the `fetch_total` delta with `FETCH_MIN_BYTES=65536, FETCH_WAIT_MAX_MS=500` is at most one third of the delta with the defaults. One fetch per 500 ms against many small fetches.
6. KIP-848:
   - Setup: drain with `NOTIFY_WORK_MS=5` and start `notification-2` mid-drain.
   - Precondition: notification-2 gets partitions.
   - `kafka-groups.sh --list` shows type Consumer for the consumer-protocol run and Classic for the classic run.
   - Partitions revoked from replica 1, counted from its client log: 9 under classic, fewer than 9 under consumer.
7. Trace, metric and log in LGTM.

**Experiments:**
- `consumersCount` 1, 3, 9 and 12, drain time in bench.
- `max.poll.records` 50 against 500 at fixed work, in bench.
- Classic with `partitionAssignor=CooperativeStickyAssignor`: revocations fall without KIP-848. Record it.
- Remove `MAX_POLL_RECORDS=20` from the fixed profile: check 4 fails.

**bench.sh:**
- Drain time and median event age per profile, 5 repeats with a fresh group each.
- The processing gap on replica 1 during scale-out, classic against consumer protocol.
- Same header and scrub rules as 05.

**Figures:**
- `06-fetch-poll-process`: fetch buffer, poll, process, and where `max.poll.interval.ms` bites.
- `06-concurrency-cap`: 12 threads on 9 partitions, 3 idle.
- `06-eager-vs-incremental`: classic eager revoke-all against KIP-848 incremental.

**Citations:**
- Strimzi "Optimizing Kafka consumers" (if approved) and "Developing Kafka client applications: A simple consumer" (confirmed).
- Kafka 4.3 consumer configs.
- KIP-848.
- Bellemare ch 11.
- Hohpe and Woolf Polling Consumer and Competing Consumers.
- Camel Kafka component (`groupProtocol`, `consumersCount`).

**Layout:** a topic chapter like 05. One callout naming `NotificationConsumeRoute`, `NotificationWriter` and the rule "concurrency ≤ partitions; size `max.poll.records` to `max.poll.interval.ms`".

### 07 Queues for Kafka (`examples/07-queues-for-kafka`, Part 1, `## Pattern: Competing Consumers` with share groups)

**Services:**
- notification-service as three worker replicas `worker-1` to `worker-3`; worker-1 publishes 8080, the others are unpublished, as in ch04.
- PostgreSQL for the delivery log. Jobs are seeded by verify.sh with `kafka-console-producer` (keyed by order id) onto `edp.orders.placed` (3 partitions).

**Code:**
- `EmailGateway`, an in-process stand-in for a slow email provider:
  - customers `C-SLOW*` take `GATEWAY_SLOW_MS` (8000);
  - `C-RETRY*` fail on the first attempt (transient);
  - `C-BOUNCE*` fail permanently.
- `ShareDeliveryWorker` (SmallRye, `@Incoming("delivery-jobs")` with `share-group=true`, `ShareGroupAcknowledgement`):
  - accept on success, `release()` on transient failure, `reject()` on bounce;
  - consumer settings `max.poll.records=1` and `share.acquire.mode=record_limit`, so a worker holds one job at a time.
- `GroupDeliveryRoute` (Camel, `groupId=notification-workers`): the consumer-group comparison, tested on the same input.
- `DELIVERY_MODE=share|group` enables one path. The SmallRye channel uses `enabled`; the Camel route uses `autoStartup`.
- `DeliveryRow` (Panache): order id, worker, partition, offset, delivery count (from the record's `deliveryCount()` if SmallRye exposes it; otherwise counted per order), started_at, finished_at, outcome.
- Group configs come from an init step with `kafka-configs.sh --entity-type groups`: `share.auto.offset.reset=earliest`, `share.record.lock.duration.ms=30000`.

**verify.sh, deterministic part** (about 22 checks). Seed 30 jobs. One `C-SLOW` job sits early in one partition, with at least 5 later jobs in the same partition (precondition, from the offsets).
- **Share mode:**
  - every non-bounce job is delivered with outcome `sent`;
  - the slow job took at least `GATEWAY_SLOW_MS` (precondition);
  - **HOL relation:** at least one later-offset job in the slow job's partition finished before the slow job finished;
  - jobs ran on 3 distinct workers;
  - the `C-RETRY` job has delivery count 2 and outcome sent;
  - the `C-BOUNCE` job has 1 attempt, outcome rejected, and no redelivery after waiting past the lock duration;
  - `kafka-share-groups.sh --describe` shows 3 members.
- **Group mode (control, and the negative witness that makes the share check bite):** the same seed under `DELIVERY_MODE=group`, and every later-offset job in the slow partition finished after the slow one. Setting `DELIVERY_MODE=group` for the share run makes the HOL check fail.

**Experiments:**
- `share.record.lock.duration.ms=5000` with an 8 s job: the lock expires, the slow job is delivered twice (delivery count 2, two `sent` rows). At-least-once, so delivery needs idempotence.
- `share.acquire.mode=batch_optimized` with a larger `max.poll.records`: jobs acquired with the slow one wait behind it on the same worker.
- A fourth worker on 3 partitions: idle in group mode, busy in share mode.
- `share.delivery.count.limit=2` with a job that always fails transiently: archived after 2 deliveries.

**Figures:**
- `07-head-of-line`: a consumer group, where the slow job blocks its partition.
- `07-share-acquire-ack`: acquire, lock, accept, release or reject, delivery count, lock expiry.

**Citations:**
- KIP-932 (confirmed).
- Strimzi "Using Queues for Apache Kafka with Strimzi" (confirmed).
- Kafka 4.3 group configs and consumer configs (share.*).
- Hohpe and Woolf, Competing Consumers.
- SmallRye Reactive Messaging Kafka docs, share groups section.
- Camel Kafka component: state only that the pinned component has no share-group option. That claim is backed by the catalog, without a version in prose.
- No Bellemare chapter title names queues, so none is cited.

**Layout:** the `## Pattern:` spine. "How Quarkus and Camel model it" explains why the share side is SmallRye and the group side is Camel. One callout naming `ShareDeliveryWorker`, `EmailGateway` and `GroupDeliveryRoute`, with the rule "share groups when jobs are independent and slow; consumer groups when order per key matters".

### Bellemare back-fill (02 to 04), References only, no new prose claims

- **02:** Bellemare, *Building Event-Driven Microservices*, Second Edition, O'Reilly, 2025: Chapter 2, Fundamentals of Events and Event Streams; Chapter 5, Designing Events; Chapter 17, Integrating Event-Driven and Request-Response Microservices.
- **03:** Chapter 4, Schemas and Data Contracts; Chapter 5, Designing Events; Chapter 7, Denormalization and Eventification.
- **04:** Chapter 2, Fundamentals of Events and Event Streams.
- **05 and 06:** Chapter 11, Basic Producer and Consumer Microservices.
- **bibliography.md Bellemare Focus:** "Events and event streams, event design, schemas and data contracts, integrating existing systems, denormalization, stateful and deterministic stream processing, workflows, producer and consumer microservices, eventual consistency, bad data, testing, deployment". This is title-faithful; drop "reprocessing", which no title names.
- **claims.md:** one row per citation, evidence "sources.md Bellemare TOC, owner-confirmed 2026-11-10, fit by title", status verified.

## Design decisions

1. **Share groups: SmallRye Reactive Messaging (`quarkus-messaging-kafka`, `share-group=true`) for 07's share side.** It is the idiomatic Quarkus path, the pinned SmallRye 4.37.0 has it, and Camel 4.22.1 has no share option. Fallback if the spike fails on 3.40: a `KafkaShareConsumer` from kafka-clients in an `@ApplicationScoped` worker with a managed virtual thread, `share.acknowledgement.mode=explicit`. The fallback still teaches the acknowledgement API.
2. **The consumer-group comparison in 07 stays a Camel route.** The book is Quarkus and Camel, and each side uses the tool that supports it.
3. **Job source in 07: `edp.orders.placed` directly.** It needs no new topic and stays inside the catalog. The alternative, a new topic, is an owner decision.
4. **Client metrics: a Camel `kafkaClientFactory` bean binding Micrometer `KafkaClientMetrics`.** It is a documented Camel extension point plus a provided Micrometer binder, so nothing is hand-rolled, and the metrics reach LGTM over OTLP.
5. **verify.sh asserts only on relations with a control and a precondition, never on wall-clock time.** This addresses the r02 lesson about checks that pass when the behaviour breaks.
6. **Throughput, latency, lag and drain numbers come only from bench.sh:** 5 interleaved repeats, median and range, settings header, scrubbed CSV committed under `results/`. Prose states relative comparisons from the recorded run.
7. **05 uses three publish routes in one app (unbatched control, defaults, tuned), each with its own `clientId`.** Interleaved repeats in one JVM avoid restart and JIT variance, and the options sit side by side. acks=1 is the one recreate.
8. **06 uses env-driven settings with a fresh group per run.** Consumer settings are per member, and re-draining the same backlog gives identical input across runs.
9. **05 and 06 do not share an example.** Chapters stand alone, and the service sets differ.
10. **06 uses three catalog topics, not all nine.** 9 partitions are enough to show the cap and revocations, and the seed stays small. Update the remap row text.
11. **Load driver `FlashSaleResource` inside order-service.** It drives the Camel producer under test. External perf tools would bypass it.
12. **Lock schedule: parallel authoring, then serial exclusive runtime windows granted by the orchestrator, then an orchestrator serial final run.** Executors still iterate their own verify.sh, and no two stacks contend. mvn Dev Services runs use `flock -w 900` with short holds.
13. **No versions in 05 to 07 prose.** Share-group availability is stated without versions, because the feature exists on the pinned broker. Defaults are named as values ("the default linger is 5 ms"), which validate.py allows.

## Steps

**0. Preflight** (orchestrator).
- Branch `release/r03`; Docker clean (no `edp-*`).
- Set r03 to "in progress" in `_plans/iteration-plan.md` and write `_plans/r03-plan.md` from this plan after owner approval. Use `<repo>` paths, never home paths, and run host_ids.

**Group 1** (parallel, disjoint files):
- **1A Sources and back-fill** (docs executor, no stack). Files: `_docs/02-*.md`, `_docs/03-*.md`, `_docs/04-*.md` (References lines only), `bibliography.md`, `_plans/sources.md`, `_plans/domain-model.md` (remap rows 05 to 07 refined: three topics in 06, the 07 job source).
  - Fetch and record: KIP-848, KIP-1030, Kafka 4.3 producer, consumer and group config pages, the Kafka docs page on share groups and queues, the Strimzi tuning posts (if approved) and a re-read of Strimzi #2, #3 and #5 for the specific claims, Hohpe and Woolf Competing Consumers and Polling Consumer, the SmallRye Kafka share-group docs, and the Camel Kafka component page (`groupProtocol`, `consumersCount` group-id note).
  - Return claim rows. No claims.md edits.
- **1B Spikes** (one executor, scratchpad only, under the lock, about 45 min). Return a findings report.
  - S1: SmallRye share group on Quarkus 3.40.1.2 with Dev Services Kafka 4.3.2 plus `container-env` (share protocols and share-coordinator RF 1). Prove accept, release and reject, delivery count access, and `share.acquire.mode=record_limit` with `max.poll.records=1`.
  - S2: the Camel `kafkaClientFactory` with `KafkaClientMetrics`. The metric names, and the `client_id` label as it appears in LGTM Prometheus.
  - S3: Camel `groupProtocol=consumer` with `consumersCount=3` on the compose broker. `kafka-groups.sh` type output, and the revocation log lines under both protocols.

**Gate G0** (orchestrator): the spikes pass, or the pre-agreed fallback is chosen (decision 1 fallback; for S2, report metrics through the driver JSON and note it). Ask the owner only if both share paths fail.

**Group 2: authoring** (parallel; depends on G0; each executor owns only `examples/NN-*/**`, `_docs/NN-*.md`, `_example_pages/NN-*.md`, `diagrams/specs/NN-*.py` and its own `diagrams/NN-*.{svg,excalidraw}`):
- 2A = chapter 05, 2B = 06, 2C = 07.
- Code, tests (Dev Services, pinned images, `container-env` for 07), README, example page, figure specs (generate only their own; never run build-diagrams.sh), and a chapter draft with result placeholders kept out of the file. Numbers are added after the window.
- `mvn -B -ntp verify` under `flock -w 900 <scratchpad>/stack.lock`, with short holds.
- Forbidden: claims.md, verification-log.md, CLAUDE.md, _infra, validate.py, git.
- Reuse the r02 common brief, updated with the r02 validator lessons: no aphorisms, no self-reference, no unbacked sentences, drain the full wait in tests, every check needs a control.

**Step 3: runtime windows** (serial; each executor holds the lock exclusively for its window, 90 min cap, granted FIFO by readiness, 07 first if tied):
- iterate verify.sh to N/N from clean;
- run every experiment and record the failing check numbers;
- run bench.sh once (5 repeats) and scrub the results;
- leave no `edp-*` containers.

After the window, the executor writes the measured relative comparisons into the prose (no lock needed) and returns: files, mvn and verify lines, experiments, word count, claim rows, and the acceptance status.

**Step 4: final serial verify** (orchestrator, from clean, under the lock):
- domain-model install;
- `scripts/verify-all.sh` plus `mvn -B verify` for 00 to 07, one at a time;
- bench.sh once for 05 and 06 in quick mode (3 repeats), only to confirm that each relation has the same direction as the recorded run;
- static gates: validate.py plus self-test, voice-scan, idiomatic-scan, host_ids, site-build, the freight-name grep from r02.
- Merge claims (rows for 05 to 07 and the Bellemare rows; all verified or cut) and the log entries (pass counts, experiments, bench settings and medians).
- Commits:
  - `docs(§05)` with `demo-05`, and the same for 06 and 07;
  - `docs(r03.x): Bellemare citations for 02 to 04` for the back-fill.

**Step 5: Opus validation** (lgtm-relay validator, 2 repair rounds at most):
- static gates and a curl of each new page;
- claims audit, line by line;
- aphorism and self-reference sweep;
- check-bite audit: for every verify.sh relation, name its control and precondition;
- originality n-gram check against EIP 32, 33 and 34 (no 10-word shared runs) and a check that 05 to 07 add the deep points listed above;
- fresh clone with an empty Maven repository;
- word counts.

The orchestrator re-runs affected verify.sh serially after each repair round.

**Step 6: Ship** (lgtm-github):
- PR `release/r03` to main. CI green (content, examples, Pages).
- Merge when validation and CI pass (the owner pre-authorised this). Squash, tag `r03.0`, notes-only release, delete the branch.
- Notion PRD: §5 rows 05 to 07 rewritten to the order-to-delivery domain with Bellemare ch 11 for 05 and 06; Bellemare titles in the column for 02 to 04; §3 Strimzi tuning posts if approved; §10 r03 shipped.
- `iteration-plan.md`: r03 done.

## Acceptance criteria

**05:**
- verify.sh N/N with N of 16 or more, from clean; `mvn verify` green.
- Each profile publishes exactly N.
- Unbatched records per request is at most 3, and tuned is at least 5 times unbatched.
- Concurrency 1 on the tuned route gives at most 1.5 records per request.
- Tuned bytes per record are below the default, and dump-log shows lz4.
- producerId is at least 0 under the defaults and -1 after acks=1.
- All four experiments are recorded.
- The bench CSV has 5 repeats per profile, a settings header, and is scrubbed.
- Prose numbers are relative and match the CSV medians.

**06:**
- verify.sh N/N with N of 18 or more.
- Rows equal 3N and final LAG is 0.
- 12 members with exactly 3 idle; 3 distinct client ids.
- Poll-interval witness: attempts above 1 with 500, all 1 with 20.
- Trickle `fetch_total` relation holds with its precondition.
- KIP-848 group type is Consumer, and revoked partitions under consumer are fewer than 9, which equals classic.
- Experiments and bench recorded.

**07:**
- verify.sh N/N with N of 20 or more.
- HOL relation holds in share mode and fails in group mode, with the precondition asserted.
- Retry delivered on the second delivery; bounce rejected with no redelivery; 3 workers used.
- The lock-expiry duplicate experiment is recorded.
- The prose states that the pinned Camel Kafka component has no share-group option and why SmallRye (or the client) is used.

**Every chapter 05 to 07:**
- At most 2,500 words (target about 2,000).
- Exactly one callout of the house shape, naming the example's own types.
- Two or more figures from specs.
- No dashes, versions, test names, self-reference, forward references or aphorisms.
- `part: events-between-services`.
- Containerfile, compose and env conventions met (`CAMEL_COMPONENT_KAFKA_BROKERS`, `QUARKUS_*`, `/q/health/ready`, mem_limit 384m).
- Traces, metrics and logs in LGTM.
- Every claim row verified or cut.

**Back-fill:** 02, 03 and 04 References carry the Bellemare lines above; bibliography Focus updated; claim rows present.

**Iteration:**
- validate.py ok, voice 0 ban-tier, idiomatic 0 high, host_ids 0, site builds, freight grep 0.
- The verification log has the final serial run 00 to 07 and the bench confirmations.
- CI green, tag and release exist, PRD and iteration plan updated.

## Risks (and detection)

- **SmallRye share groups do not work under Quarkus 3.40** (the docs show the feature only for 4.0). Detected by S1 at G0; fallback is the plain `KafkaShareConsumer`.
- **The Dev Services broker lacks share protocols** (`container-env` missing on 3.40). Detected by S1; fallback is a `@QuarkusTestResource`-free test, with share behaviour tested only in verify.sh, and the gap stated in the log.
- **`deliveryCount` is not exposed through SmallRye metadata.** Detected by S1; count attempts per order in `DeliveryRow` instead.
- **Prometheus label names for Kafka client metrics differ** (`client_id` against `client.id`, or a missing `_total`). Detected by S2; verify.sh uses the names S2 records.
- **Shared-host noise flips a bench relation in the final run.** Relations sit in verify.sh only where they are structural. Bench differences are reported, not asserted, and a flipped bench direction is logged and investigated, not hidden.
- **Natural batching makes the "defaults" profile look tuned under concurrency 200.** This is expected and taught. The control is the unbatched route, not the defaults.
- **Camel's eviction behaviour after `max.poll.interval.ms`** (commit failure handling) may differ from plain Kafka. Detected by the 06 witness during the window; if no duplicates appear, report the actual behaviour and rewrite the witness (for example, assert the leave-group log plus the rebalance count).
- **Log-based revocation counting is brittle across protocols.** S3 fixes the exact log lines. Fallback: the members and assignment snapshot before and after, from `kafka-consumer-groups --describe --members --verbose`.
- **Lock-window overrun.** 90-minute cap; the orchestrator grants the next window, and an unfinished executor returns with a report.
- **Memory.** 07 runs three workers plus infra, and 06 runs two replicas. mem_limit 384m; check for OOMKilled in the log.
- **Copying EIP 32 to 34 structure or tables** (for example tuning-profile tables). Caught by the n-gram check and the validator's "adds depth" review.
- **Prose repeats the Camel catalog's stale "linger defaults to 0".** Caught by the claims audit against the Kafka 4.3 docs.
- **Bench files leak host details.** `host_ids.py scrub` on `results/` and `check` in CI. CPU is given only as a class.
- **Word budget pressure in 05 and 06** (many knobs). Topic layout with one subsection per knob and no knob tables copied from docs; validate.py word count.

## Owner decisions needed (each with a recommendation)

1. **Share-group implementation in 07:** SmallRye `share-group=true` (rec), falling back to the plain `KafkaShareConsumer` only if the spike fails. Camel is not possible on the pin.
2. **07 job source:** consume `edp.orders.placed` directly (rec). The alternative is a new `edp.notifications.requested` topic carrying `Notification` jobs, which would also need a domain-model.md change.
3. **Add the Strimzi "Optimizing Kafka producers" (2020-10-15) and "Optimizing Kafka consumers" (2021-01-07) posts** to sources and the PRD (rec yes). They are the tuning posts proper; both return HTTP 200 today.
4. **05 structure:** three publish routes in one app with interleaved bench runs (rec), rather than one route switched by env and restarts. This slightly exceeds "one or two routes" and is justified as comparison code.
5. **06 scope:** three catalog topics with 9 partitions (rec), rather than all nine topics. Update the remap text.
6. **Bench results committed** as scrubbed CSV under `examples/NN/results/` (rec), rather than only in verification-log.md.
7. **Lock schedule:** parallel authoring, then serial exclusive runtime windows, then an orchestrator serial final run (rec).
8. **Client metrics through a Camel `kafkaClientFactory` with Micrometer `KafkaClientMetrics`** (rec), unless S2 finds a built-in binding.

### Critical Files for Implementation
- <repo>/examples/04-ordering-and-partitioning/compose.yaml (shape for replicas, topics-init, env)
- <repo>/examples/_infra/compose-kafka.yaml (share protocols, coordinator RF 1)
- <repo>/scripts/validate.py (word count, VERSION_RE, callout, self-reference)
- <repo>/_plans/sources.md (Bellemare mapping, new source rows)
- <repo>/_docs/04-ordering-and-partitioning.md (register, References format, Build section)
- ~/.m2/repository/io/smallrye/reactive/smallrye-reactive-messaging-kafka/4.37.0/ (share-group classes: KafkaShareGroupSource, queues/ShareGroupAcknowledgement)


## Owner review 2026-11-10

Approved with all eight recommendations. Also in the back-fill: chapter 04 References gain Lawrence chapter 2 (Anatomy of Message-Driven Systems), which the mapping lists but r02 omitted.
