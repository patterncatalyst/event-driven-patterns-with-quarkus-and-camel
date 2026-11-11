---
layout: plan
title: r05 plan
render_with_liquid: false
---

# r05 plan: chapters 11 to 13, idempotent consumer and inbox, exactly-once with Kafka transactions, retry and dead letter (Opus planner, 2026-11-11)

Status: draft for owner review. Nothing written to disk. Branch `release/r05` is clean at r04.0 (e655839).

Planner note: one ephemeral `docker run --rm docker.io/apache/kafka:4.3.2` was run (help output only, no stack, removed on exit). No other state was changed.

## Findings

### Repo state

- domain-model is 2.1.0 (`examples/domain-model/pom.xml`). `Topics` has the 9 catalog topics plus `SHIPPING_SHIPMENTS`, `INVENTORY_ITEM_CHANGES` and `INVENTORY_ITEMS`. `DomainModelTest` requires non-catalog topics to match `edp\.[a-z]+\.[a-z-]+`, so a DLQ name must be `edp.orders.placed-dlq`. `edp.orders.placed.dlq` fails the regex and the convention.
- `_infra/compose-kafka.yaml` already sets `KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1` and `_MIN_ISR=1`, so transactions work on the single broker without an infra change. Kafka Dev Services may still need the same container env (spike S2).
- Mode switching already has a precedent: `QUARKUS_PROFILE: ${DELIVERY_MODE:-share}` (07) and `${CDC_MODE:-connect}` (09). In-run container recreation also has one: `up -d --no-deps --force-recreate --wait <svc>` (08 and 09). Both are reused here.
- Ch 08 prose already states that Kafka transactions are scoped to one cluster and cannot enlist a database. It cites the Strimzi EOS post and says duplicate `id` headers have to be dropped by the consumer. Ch 11 picks that up as a backward reference.
- r04 lessons that apply here: the console consumer prints a KIP-848 notice, so keep record lines only. Never `grep -q` under pipefail (capture first). Use preconditions with `fail_loudly`. Freeze check numbering. Use a fresh-start block. Use a 1-partition topic wherever order is asserted.
- Word counts of r04 chapters: 08 1,999, 09 2,567 (front matter included), 10 2,099. Target about 2,000.

### Versions re-survey (preliminary, 2026-11-11; 1A completes and records)

| Item | Found | Source |
|---|---|---|
| quarkus-bom / quarkus-camel-bom | 3.40.1.2 still newest final (4.0.0.Beta3 prerelease) | https://repo1.maven.org/maven2/io/quarkus/platform/quarkus-bom/maven-metadata.xml |
| camel-bom | 4.22.2 newest 4.22.x (4.23.0 not LTS) | https://repo1.maven.org/maven2/org/apache/camel/camel-bom/maven-metadata.xml |
| Camel Quarkus | 3.40.0 newest; camel-quarkus-jpa, -jta, -sql, -microprofile-fault-tolerance all 3.40.0 in quarkus-camel-bom 3.40.1.2 | local quarkus-camel-bom-3.40.1.2.pom; Central metadata |
| SmallRye Reactive Messaging Kafka | 4.37.0 in the platform; 5.0.0-RC2 upstream is a prerelease, not used | quarkus-bom-3.40.1.2.pom |
| SmallRye Fault Tolerance | 6.11.4 in the platform (upstream 7.1.1 exists, platform-managed, not overridden) | quarkus-bom-3.40.1.2.pom |
| narayana-jta | 7.3.4.Final (platform) | quarkus-bom-3.40.1.2.pom |
| kafka-clients | platform 4.2.1; Central has 4.4.0 | Central metadata |
| Strimzi | 1.3.1 still latest (2026-11-04), so Kafka 4.3.2 stays held | GitHub releases API |
| grafana/otel-lgtm | v0.40.0 still latest | GitHub releases API |
| Not yet checked | CNPG operand 18.x tag, library/postgres 18.x, ubi10 openjdk-25 tags, Maven #57216, Debezium (unused in 11 to 13) | 1A |

### Kafka transactions: what the shipped APIs do

- **SmallRye 4.37.0** (javap on the jar): `KafkaTransactions` has `withTransaction`, `withTransactionAndAwait`, `withTransactionAndAck(Message, fn)` and overloads taking `IncomingKafkaRecordMetadata` and `IncomingKafkaRecordBatchMetadata`. `TransactionalEmitter` has `send`, `markForAbort` and `isMarkedForAbort`. `PooledKafkaProducer` exists in 4.37.0.
- **Quarkus Kafka guide, sections 7.2 and 5.8** (https://quarkus.io/guides/kafka):
  - Exactly-once is "managing consumer offsets inside a transaction, together with produced messages".
  - Batch consumption is recommended. Required settings: `commit-strategy=ignore`, `failure-strategy=ignore`, and `transactional.id` plus `acks=all` on the outgoing channel.
  - On abort, the consumer seeks back to the last committed offset. With no committed offset it seeks to the beginning of the partition, even with `latest`.
  - The emitter allows one transaction at a time per producer.
  - `isolation.level=read_committed` is for readers.
- **Quarkus guide, Hibernate chaining, sections 29.7 and 29.8**: chaining orders two commits ("the database transaction (inner) will commit followed by the Kafka transaction (outer)"). The guide never claims atomicity. This backs the ch 12 scope decision below.
- **Strimzi blog** (Federico Valeri, 2023-05-03, https://strimzi.io/blog/2023/05/03/kafka-transactions/):
  - Transactions "are only supported inside a single Kafka cluster", and the outbox is suggested for external side effects.
  - The `transactional.id` is static, and an older producer with the same id is fenced as a zombie.
  - KIP-447 sends consumer group metadata with the offsets.
  - read_committed stops at the LSO.
  - Aborted messages "are never observed by the client application".
  - The throughput cost "can be significant".
  - Hanging transactions are a risk, and `kafka-transactions.sh` is the tool.
- **Kafka 4.3 configs**:
  - `transaction.timeout.ms` default 60000, after which the "coordinator proactively aborts it".
  - `isolation.level` default `read_uncommitted`. With read_uncommitted, poll returns "even transactional messages which have been aborted". With read_committed, poll returns only up to the LSO, "the one less than the offset of the first open transaction".
  - Producer configs list `transaction.two.phase.commit.enable` (KIP-939, default false).
- **KIP-939** (Support Participation in 2PC) is Accepted. Its client API (`prepareTransaction`, `completeTransaction`) is **not public** on `KafkaProducer` in kafka-clients 4.2.1 or 4.3.1 (javap: only `PreparedTxnState` is present). It is out of scope for both SmallRye and Camel, so the ch 08 sentence stands. 1A re-checks the 4.3.2 javadoc. If the API is public there, ch 08 gets a one-clause qualifier (fix now) and ch 12 one sentence.
- **Camel 4.22.2 Kafka component**:
  - `transacted` creates a `transactional.id` from the endpoint id and route id. `transactionalId` sets one explicitly.
  - `KafkaTransactionSynchronization` calls `commitTransaction`/`abortTransaction` at exchange completion.
  - **No class in camel-kafka-4.22.2.jar calls `sendOffsetsToTransaction`** (scanned every class with javap). Camel's transacted producer makes the writes of one exchange atomic, but consumed offsets stay outside the transaction, so a Camel consume-transform-produce route is at-least-once with atomic output. This is the ch 12 comparison.
  - The consumer's `isolationLevel` defaults to read_uncommitted.

### Idempotent consumer: what the shipped APIs do

- **Camel Idempotent Consumer EIP** (catalog 4.22.2): `eager` default true (the key is added before processing), `removeOnFailure` default true, `skipDuplicate` true, `completionEager` false.
- **`JdbcMessageIdRepository`** (camel-sql 4.22.1 bytecode): it builds its own Spring `TransactionTemplate` over a `DataSourceTransactionManager(dataSource)` with PROPAGATION_REQUIRED. Spring knows nothing of the Narayana JTA transaction, so under a Quarkus JTA transaction the key either commits on its own or conflicts with Agroal's enlisted connection. Spike S1 decides which. Default table `CAMEL_MESSAGEPROCESSED(processorName, messageId, createdAt)`, PK on (processorName, messageId). EIP Camel ch 15 already walks through this repository, so it is not repeated.
- **Camel Quarkus JPA extension** (https://camel.apache.org/camel-quarkus/3.40.x/reference/extensions/jpa.html): it "leverages Quarkus TX API to provide `TransactionStrategy`", and `JpaMessageIdRepository` takes the CDI `EntityManagerFactory` and `TransactionStrategy`. Whether its `add()` joins an existing JTA transaction (from `.transacted()` with camel-quarkus-jta) or starts a new one is not documented. Spike S1 decides. If it joins, the Camel alternative is a real same-transaction inbox.
- **Camel `KafkaIdempotentRepository`** (Camel Kafka page) keeps keys in a Kafka topic with a local cache and can never share a database transaction. One row in the ch 11 decision table.
- **Quarkus**: `@Transactional` on an `@Incoming` method makes it blocking automatically, and messages are acked only after processing completes (Kafka guide).

### Retry and dead letter: what the shipped APIs do

- **Failure strategies** (SmallRye "Receiving Kafka records", plus javap on 4.37.0):
  - `fail` (default) stops the application.
  - `ignore` commits and continues.
  - `dead-letter-queue` commits the offset and writes the record to `dead-letter-queue.topic` (default `dead-letter-topic-$channel`, which must be overridden).
  - `delayed-retry-topic` is marked **experimental**. Its default topics are `<channel>_retry_<ms>`, which break the naming rule.
- **DLQ headers** (javap constants): `dead-letter-reason`, `dead-letter-cause`, `dead-letter-exception-class-name`, `dead-letter-cause-class-name`, `dead-letter-topic`, `dead-letter-partition`, `dead-letter-offset`.
- **Deserialization failures**:
  - With `fail-on-deserialization-failure=true` (default), the failure is reported and the app goes unhealthy.
  - With `false`, the message carries a null value and `deserialization-failure-*` headers, including `deserialization-failure-data` with the original bytes, and the DLQ path has a `deserialization-failure-dlq` handling branch. Spike S4 confirms the DLQ value is the original bytes.
- **SmallRye Fault Tolerance `@Retry`** on `@Incoming` is documented in the Quarkus Kafka guide. The interceptor order matters: `@Transactional` outside `@Retry` would retry inside one aborted PostgreSQL transaction. The retry must wrap a call to a separate `@Transactional` bean (spike S4).
- **Camel Kafka**:
  - With the default `ERROR_HANDLER` poll strategy, exceptions go to Camel's error handler and polling continues.
  - With `breakOnFirstError=true` and the NoopCommitManager, the offset is not committed and the record is retried, which "can lead to endless processing" of a poison message.
  - So Camel's default error handler on a Kafka route logs and moves past a failed record (offset committed, record gone), while SmallRye's default stops the app. That contrast is ch 13 material that EIP ch 5 does not cover (spike S5 confirms).
- **Polling threads**: SmallRye polls on its own thread and pauses partitions under backpressure. Camel processes on the polling thread. Long in-memory redelivery therefore risks `max.poll.interval.ms` (default 300000) only in Camel. Spike S5 measures it before any claim is made.

### Overlap

- **EIP Camel ch 15**: Idempotent Receiver with memory, Redis and JDBC repositories, a repository table, the "Why event_id matters" point and a TTL warning. It also has a Transactional Client section that uses a polling outbox and states Kafka has no XA. Ch 11 links to it and repeats none of it: its subject is the inbox *in the effect's transaction*, the crash between commit and offset commit, and where a separate key store leaves a gap.
- **EIP Camel ch 5**: DLC config with backoff, `useOriginalMessage`, `onPrepareFailure` headers, the invalid-vs-dead distinction, the `.dlq` suffix convention and a guaranteed-delivery table that lists "exactly once = transactions + idempotent producer". Ch 13 links to it. Its subject is Kafka-specific: offsets after a dead letter, poison pills and partition blocking, the two frameworks' opposite defaults, and one comparison table.
- **CNDP ch 5 and 6**: "exactly-once is at-least-once plus idempotency" and the Streams `exactly_once_v2` config. Ch 12 links to ch 6 and goes deeper on the client mechanics.
- **Bellemare 2e**: sources.md says chapters 11 and 12 have no TOC title naming their topic, so cite none unless the owner confirms section titles. Ch 18 "Handling Bad Data in Event Streams" fits 13.
- **Lawrence**: PRD maps ch 2 (Anatomy of Message-Driven Systems) to 11 and 13. None for 12.

## Approach

Same gate shape as r04:

1. G0: spikes plus the re-survey and source fetches (parallel).
2. domain-model 2.2.0.
3. G1: three parallel authoring executors on disjoint files, `mvn verify` under the stack lock.
4. Serial exclusive runtime windows.
5. Orchestrator re-run of every prose experiment.
6. Final serial run 00 to 13.
7. Opus validation, then ship.

The house pattern for 11 to 13:

- One service per example and one live consumer per run of the container.
- Modes selected by `QUARKUS_PROFILE` from an env var.
- The comparison or alternative path runs in the same verify.sh on the **same input topic**, after the container is recreated with the other profile and its own consumer group. Before the container starts, verify.sh positions the new group with `kafka-consumer-groups --reset-offsets --to-earliest|--to-latest --execute` while the group is inactive. That is deterministic, with no race on `latest`.
- Every input topic in 11 to 13 has 1 partition (topics-init), so offsets and "behind the poison pill" are well defined.
- Inputs are produced by verify.sh with the console producer (headers, key, value). No order-service in 11 or 13, and no inventory-service in 12.
- Crash switches are labelled demo code, as in ch 08.

**Ch 12 honesty decision.** The domain row "writes Payment and publishes PaymentProcessed in one transaction" is wrong for Kafka transactions: they are Kafka-only (Strimzi blog, ch 08), and Quarkus' Hibernate chaining is two ordered commits, not one atomic one. Ch 12 therefore shows Kafka-only read-process-write: payment-service reads InventoryReserved and publishes PaymentProcessed carrying the `Payment` record, with its consumed offsets, in one Kafka transaction, and readers use read_committed. No database. The "When not to use it" section states the boundary and points back to ch 08 (DB to Kafka) and ch 11 (Kafka to DB, inbox). Both are backward references.

Rejected alternatives:

- **Payment row plus Kafka transaction "chained"**: it teaches a false atomicity. It could only appear as a crash experiment, which would bloat the example.
- **KIP-939 2PC**: no public client API in the shipped clients, and no framework support.
- **delayed-retry-topic as the ch 13 retry**: experimental, and its default topic names break `edp.<domain>.<event>`. It gets one table row only.
- **Payment-gateway stub for the ch 13 transient failure**: an invented dependency. A row lock past `lock_timeout` is a real transient failure of inventory-service's own database (decision 3).
- **Repeating the JDBC idempotent repository or DLC walkthroughs** (EIP 15 and 5): link instead.
- **Kafka Streams `exactly_once_v2`**: CNDP ch 6 has it, and it is Part 4's subject. No forward reference.
- **An at-least-once SmallRye path as the ch 12 negative witness**: the Camel transacted route is a stronger lesson. Its output is atomic, it is still not exactly-once, and "where Camel fits" is a PRD aim.
- **Three consumers running at once against one stock row**: their effects would mix.
- **Separate topics per mode**: topics only from constants.

## Version re-survey (1A, record in versions.md)

1. Quarkus platform respin after 3.40.1.2 on the 3.40 LTS stream (code.quarkus.io streams).
2. Camel 4.22.x patch after 4.22.2; Camel Quarkus after 3.40.0 (drop the camel-bom override if a release carries 4.22.2+).
3. SmallRye Reactive Messaging and SmallRye Fault Tolerance versions in the platform (4.37.0, 6.11.4); 5.x and 7.x not used.
4. kafka-clients in the platform (4.2.1) vs the 4.3.2 broker; KIP-939 public API in 4.3.2 javadoc (yes or no).
5. Strimzi latest release and its Kafka list (4.3.2 held unless a release supports 4.4).
6. CNPG operand 18.x and library/postgres 18.x; otel-lgtm; ubi10 openjdk-25 build and runtime tags.
7. Maven 3.10 HOLD (#57216).
8. `apache/kafka:4.3.2` tools: `kafka-transactions.sh` present; console producer `--reader-property parse.headers=true` syntax; console consumer `--isolation-level` and `--formatter-property print.headers=true`.

No version in 11 to 13 prose.

## Per-chapter design

### 11 Idempotent consumer and inbox (`examples/11-idempotent-consumer-and-inbox`, `## Pattern: Idempotent Consumer`, inbox as its form)

**Services.** inventory-service (8080) with Panache, Flyway schema `inventory`, quarkus-messaging-kafka, camel-quarkus-kafka, camel-quarkus-jpa, camel-quarkus-jta and camel-quarkus-bean. Compose includes kafka, postgres and lgtm, plus topics-init creating `edp.orders.placed` with 1 partition.

**Tables (Flyway V1).**

- `inventory.inventory_item(sku text pk, quantity_on_hand int not null check (quantity_on_hand >= 0))`, seeded SKU-1 = 100.
- `inventory.inbox(event_id uuid pk, received_at timestamptz not null default now())`.
- The Camel repository table (`camel_messageprocessed`, mapping from S1).
- Schema management `validate`.

**Input.** OrderPlaced payload record per example (`orderId`, `customerId`, `itemSku`, `quantity`, `amount`), key = order id, header `id` = UUID: the ch 08 outbox shape, named as such in prose.

**Code.**

- `InventoryItemEntity` (Panache).
- `InboxEntry` (Panache, `@Id UUID eventId`).
- `ReservationService`:
  - `@Transactional boolean reserveOnce(UUID eventId, OrderPlaced e)`: an `INSERT INTO inventory.inbox ... ON CONFLICT (event_id) DO NOTHING` row count decides "first time". The decrement happens in the same transaction.
  - `reserve(OrderPlaced)`: decrement only, used by the comparison paths.
- `OrderPlacedInbox`, the SmallRye `@Incoming("orders-placed")`:
  - Reads the `id` header from `IncomingKafkaRecordMetadata`.
  - Profile `no-inbox` (comparison) calls `reserve` directly.
  - Counts duplicates in a Micrometer counter `inventory.inbox.duplicates` and logs `duplicate event <id> skipped`.
- `IdempotentRepositoryRoute` (profile `camel-repository`): `from(kafka:edp.orders.placed?groupId=inventory-camel)`, `.transacted()`, `.idempotentConsumer(header("id"), jpaRepository)`, `.bean(reservationService, "reserve")`. JPA or JDBC repository per S1.
- `CrashSwitch` (demo): env `DEMO_CRASH_AFTER_COMMIT_FOR=<orderId>` makes `Runtime.halt(137)` after `reserveOnce` returns, before the method returns (so before the ack).
- Per profile: groups `inventory-inbox`, `inventory-no-inbox`, `inventory-camel`. In `%camel-repository` the SmallRye channel is `enabled=false`, and the route `autoStartup` is driven by the profile (07 precedent).

**mvn tests (Dev Services).**

- Same id twice: one decrement, one inbox row.
- The effect fails after the inbox insert (unknown SKU): no inbox row remains, which proves the same transaction.
- Two threads with the same id released together, repeated 20 times: exactly one decrement.
- `no-inbox` profile: double decrement.
- Camel route: same id twice, one decrement.

**verify.sh (~22 checks, frozen after window).**

Infra:

1. inventory-service ready and group `inventory-inbox` has partition 0 assigned.
2. SKU-1 = 100, inbox empty.

Duplicates (O-1101 id A qty 3 produced twice, O-1102 id B qty 2):

3. **Precondition:** the topic holds 2 records with header `id:A` and 1 with `id:B`.
4. Group lag 0.
5. SKU-1 = 95.
6. Inbox has exactly A and B.
7. The duplicate counter or log shows A skipped once.

Crash redelivery (recreate with `CRASH_AFTER_COMMIT_FOR=O-1103`, produce O-1103 id C qty 4):

8. The container exits 137.
9. **Precondition:** the committed offset of `inventory-inbox` is at most C's offset (not acknowledged).
10. **Precondition:** SKU-1 = 91 and inbox contains C (the commit happened before the crash).
11. After recreating without the switch: lag 0.
12. SKU-1 still 91.
13. Duplicate log or counter shows C skipped.

Comparison without the inbox (stock reset to 100, inbox truncated, profile `no-inbox`, group from earliest):

14. Lag 0 over the same 4 records.
15. SKU-1 = 88 (the duplicate A applied twice).
16. Inbox still empty.

Camel repository (reset, profile `camel-repository`):

17. Lag 0.
18. SKU-1 = 91.
19. The repository table holds exactly A, B, C for the route's processor name.
20. The inbox table is untouched.

LGTM:

21. Trace in Tempo for `inventory-service`.
22. Log in Loki.

**Negative witness.** Check 15 is the in-run control. The experiment `INBOX_ENABLED=false ./verify.sh` (demo switch that skips the inbox insert in the main path) makes checks 5 and 12 fail, with checks 3, 9 and 10 proving the duplicate and the redelivery happened.

**Experiments for "Build, run, observe" (each run in step 4):**

- `INBOX_ENABLED=false`: checks 5, 6, 12 fail.
- Replace `ON CONFLICT DO NOTHING` with a plain insert: the duplicate fails at commit, and under the default `fail` strategy the channel stops and readiness goes DOWN. Record the observed behaviour; the chapter keeps it only if it is deterministic.
- Camel `eager(false)` or no `.transacted()` with the crash switch moved between the key add and the effect: lost or doubled reservation depending on S1. Kept only if S1 shows a deterministic gap.
- Two replicas of the inbox path on a 3-partition copy is NOT run. Concurrency is covered by the mvn test.

**Figures.**

- `11-inbox-same-transaction`: record, then one transaction {inbox insert, stock decrement}, then commit, then ack; a duplicate makes the insert return 0 and the record is skipped.
- `11-crash-before-ack`: timeline of commit, halt, offset not committed, restart, redelivery, inbox skip.
- Optional: `11-where-the-key-lives` (inbox, Camel repository joined or not, Kafka repository).

**Decision table in prose.** Inbox in the effect's transaction; Camel JPA/JDBC repository (joined or separate per S1); `KafkaIdempotentRepository` (separate store, never transactional with the DB); natural idempotence (upsert keyed by order id). Each row is tested or sourced.

**Callout.** Names `ReservationService`, `InboxEntry`, `OrderPlacedInbox`, `IdempotentRepositoryRoute`. Rule: the processed-id row commits in the same transaction as the effect; a key stored anywhere else leaves a gap.

**Citations.** Hohpe and Woolf Idempotent Receiver; Richardson "Idempotent Consumer" (1A confirms URL and content: processed ids recorded in the same DB transaction); Camel Idempotent Consumer EIP, JPA component plus Camel Quarkus JPA extension, Transactional Client / camel-quarkus-jta; Quarkus Kafka guide (blocking `@Transactional`, ack after processing, throttled commit); PostgreSQL INSERT `ON CONFLICT DO NOTHING`; Lawrence ch 2. Link EIP Camel ch 15 and CNDP ch 5, without repeating them. No Bellemare (decision 11).

### 12 Exactly-once with Kafka transactions (`examples/12-exactly-once-with-kafka-transactions`, `## Pattern: Transactional Client` on Kafka, read-process-write)

**Services.** payment-service (8080) with quarkus-messaging-kafka, camel-quarkus-kafka and camel-quarkus-jackson. Compose includes kafka and lgtm only (no PostgreSQL). topics-init creates `edp.inventory.reserved` and `edp.payments.processed`, 1 partition each.

**Code.**

- Payload record `InventoryReserved(orderId, sku, quantity, amount)`; output payload record `PaymentProcessed(Payment payment)` using domain `Payment` with id `P-<orderId>`, status COMPLETED.
- `PaymentProcessor`, the SmallRye path:
  - `@Incoming("inventory-reserved")` with `batch=true`, `commit-strategy=ignore`, `failure-strategy=ignore`.
  - `@Channel("payments-processed") KafkaTransactions<PaymentProcessed>`, outgoing `transactional.id=payment-service-tx`, `acks=all`.
  - `withTransactionAndAck(batch, tx -> { for each: tx.send(record keyed by orderId) })`.
- `CrashSwitch` (demo): env `DEMO_CRASH_MID_BATCH_AT=<orderId>` halts inside the transaction after the records before it in the batch were sent. S2 decides the minimal wait so that they reach the broker, or whether a `linger.ms=0` producer suffices. The precondition check makes the run fail loudly if not.
- `CamelTransactedPaymentRoute` (profile `camel-transacted`, comparison): `from(kafka:edp.inventory.reserved?groupId=payment-camel)`, unmarshal, map, `to(kafka:edp.payments.processed?transacted=true)` (or an explicit `transactionalId`, per S3), with the same crash switch.

**mvn tests (Dev Services, transaction state log RF 1).**

- A batch of 5 gives exactly 5 under read_committed.
- `markForAbort` in a test profile leaves records visible to read_uncommitted and invisible to read_committed (abort path without a crash).
- The group offset is committed only with the transaction.

**verify.sh (~20 checks).**

Infra:

1. payment-service ready.
2. Both topics exist with 1 partition.

Committed batch (O-5001..O-5005):

3. read_committed holds exactly 5 records, one per order, key = order id, payment id `P-O-500n`, status COMPLETED, amount = input.
4. Group `payment-service` committed offset = 5.
5. `kafka-transactions.sh describe --transactional-id payment-service-tx` shows state CompleteCommit.

Crash mid-batch (recreate with `CRASH_MID_BATCH_AT=O-5108`, produce O-5101..O-5110 as one burst):

6. **Precondition:** the container exits 137.
7. **Precondition:** read_uncommitted holds at least 1 PaymentProcessed among O-5101..O-5107 (partial batch written by the open transaction).
8. While the service is down, read_committed holds 0 records for O-5101..O-5110 (held back at the LSO).
9. The group offset is still 5.

Restart without the switch:

10. Within 30 s (below `transaction.timeout.ms` 60000): read_committed holds exactly 10 records for O-5101..O-5110, one each. This is the exactly-once check. The time bound shows fencing aborted the old transaction, not the timeout.
11. read_uncommitted holds 10 + k records for those orders (k from check 7): the aborted copies are still in the log and invisible to read_committed.
12. Group offset = 15.
13. describe shows CompleteCommit.

Comparison, Camel transacted (group `payment-camel` reset `--to-latest` before start, profile `camel-transacted`, `CRASH_MID_BATCH_AT=O-6008`, produce O-6001..O-6010):

14. Exits 137.
15. **Precondition:** read_committed already holds records for some of O-6001..O-6007 (each exchange committed its own transaction).
16. **Precondition:** the committed offset of `payment-camel` is below O-6008's offset.
17. After restart: read_committed holds more than one record for at least one of O-6001..O-6007 (atomic writes, input re-read: duplicates committed).
18. Every O-600n has at least one record (nothing lost).

LGTM:

19. Trace.
20. Log.

**Negative witness.** The experiment `PAYMENT_MODE=camel-transacted ./verify.sh` (main phase on the Camel route) makes check 10 fail, with 6 and 7 proving the crash and the partial write. Checks 14 to 18 are the in-run control.

**Experiments:**

- `PAYMENT_MODE=camel-transacted`: check 10 fails.
- Leave the service stopped after the crash with `TX_TIMEOUT_MS=10000`: read_committed sees nothing for the batch until the coordinator aborts the transaction after about 10 s, then still nothing for the aborted copies. Record the time.
- Read the output with the console consumer's default isolation level: the duplicates of check 11 appear.
- `batch=false`: still exactly once, with one transaction per record. Count the `CompleteCommit` transitions or log lines, observed only.

**Figures.**

- `12-read-process-write`: consumer offsets and output records inside one transaction, with commit markers.
- `12-aborted-batch-visibility`: log offsets with an aborted transaction and the LSO; read_uncommitted vs read_committed views.
- Optional: `12-camel-transacted-vs-eos`.

**Prose points (each sourced or tested).**

- Idempotent producer vs transactions; `transactional.id` fencing (Strimzi, KIP-98, KIP-447).
- LSO and its cost while a transaction is open (Kafka consumer configs, Strimzi).
- Throughput cost (Strimzi, cited, not measured).
- Hanging transactions and `kafka-transactions.sh` (Strimzi).
- The guarantee covers Kafka only. The database edges are the outbox (ch 08) and the inbox (ch 11). Hibernate chaining in Quarkus orders two commits and is not atomic (Quarkus guide 29.7 and 29.8).
- No KIP-939 sentence unless 1A finds a public API.

**Callout.** Names `PaymentProcessor`, `KafkaTransactions`, `CamelTransactedPaymentRoute`. Rule: exactly-once holds only when the consumed offsets commit in the producer's transaction and every reader uses read_committed.

**Citations.** Strimzi EOS post; Kafka docs (design: message delivery semantics and transactions; producer and consumer configs); KIP-98, KIP-447 (KIP-890 only if 1A shows TV2 is visible in the run); Quarkus Kafka guide 5.8, 7.2, 29.7, 29.8; SmallRye Kafka transactions page; Camel Kafka component (`transacted`, `transactionalId`, `isolationLevel`) plus Camel source at tag camel-4.22.2 (no `sendOffsetsToTransaction`); Hohpe and Woolf Transactional Client; link CNDP ch 6. No Bellemare, no Lawrence (PRD row blank).

### 13 Retry and dead letter (`examples/13-retry-and-dead-letter`, `## Pattern: Dead Letter Channel`, retry inside it)

**Services.** inventory-service (8080) with Panache, Flyway, quarkus-messaging-kafka, quarkus-smallrye-fault-tolerance, camel-quarkus-kafka, camel-quarkus-bean and camel-quarkus-jackson. topics-init creates `edp.orders.placed` and `edp.orders.placed-dlq` (`Topics.ORDERS_PLACED_DLQ`), 1 partition each. `inventory.inventory_item` is seeded SKU-11..SKU-15 = 100. The lock timeout comes from S4: `jakarta.persistence.lock.timeout` hint on `findById(sku, PESSIMISTIC_WRITE)`, or `SET lock_timeout` via `quarkus.datasource.jdbc.new-connection-sql`.

**Code.**

- `ReservationService` `@Transactional reserve(OrderPlaced)`: locks the item row, decrements, and raises a domain validation failure for quantity <= 0 (domain `Order` validation).
- `OrderPlacedConsumer`, the SmallRye path:
  - `@Incoming("orders-placed")` with `@Retry(maxRetries=${RETRY_MAX:8}, delay=1s, retryOn=<lock-timeout type>, abortOn=IllegalArgumentException)` calling `ReservationService`, so each attempt is a new transaction.
  - Channel `failure-strategy=${FAILURE_STRATEGY:dead-letter-queue}`, `dead-letter-queue.topic=edp.orders.placed-dlq`, `fail-on-deserialization-failure=false`.
  - Deserializer: an `ObjectMapperDeserializer` subclass.
- `DeadLetterRoute` (profile `camel`, comparison):
  - `from(kafka:edp.orders.placed?groupId=inventory-camel)`.
  - `errorHandler(deadLetterChannel(kafka:edp.orders.placed-dlq).useOriginalMessage().maximumRedeliveries(8).redeliveryDelay(1000))`.
  - `onException(JsonProcessingException, IllegalArgumentException).maximumRedeliveries(0)`.
  - `unmarshal().json(OrderPlaced.class)`, then `.bean(reservationService, "reserve")`.
  - The DLC adds a reason header; the walkthrough is EIP ch 5's, so it is linked, not repeated.

**Input.**

- O-7001 valid (SKU-11 qty 1).
- O-7002 malformed bytes (`{"orderId":"O-7002",`).
- O-7003 quantity 0 (SKU-13).
- O-7004 valid on SKU-14, produced while verify.sh holds `SELECT ... FOR UPDATE` on SKU-14 in a background psql session for 8 s.
- O-7005 valid (SKU-15 qty 1).

**mvn tests.**

- Poison goes to the DLQ with original bytes and headers.
- Invalid goes to the DLQ after 1 attempt.
- Lock timeout is retried, then reserved.
- `failure-strategy=fail` stops consumption.
- Camel: poison to DLC with original body; non-retryable after 1 attempt.

**verify.sh (~22 checks).**

Infra:

1. Ready, and partition assigned for `inventory-smallrye`.
2. Both topics exist; stock 100 x5.

SmallRye phase:

3. **Precondition:** the SKU-14 row lock is held (`pg_locks` granted RowExclusive or `pg_stat_activity` shows the session) before O-7004 is produced.
4. SKU-11 = 99.
5. The DLQ holds O-7002 with the value byte-identical to the input, and `dead-letter-topic=edp.orders.placed` plus `dead-letter-offset` equal to its source offset.
6. The DLQ holds O-7003 with `dead-letter-reason` naming the quantity rule.
7. O-7003 was attempted exactly once (log count; retry metric if S4 shows it in LGTM).
8. **Precondition:** at least one lock-timeout failure for O-7004 is logged while the lock is held.
9. While the lock is held, SKU-15 is still 100: O-7005 waits behind O-7004 on the partition.
10. After release: SKU-14 = 99, and O-7004 is not in the DLQ.
11. SKU-15 = 99: consumption continued past both bad records.
12. The DLQ holds exactly 2 records.
13. Group lag 0: the offsets of the dead-lettered records were committed.

Camel phase (stock reset, lock SKU-14 again, profile `camel`, group from earliest):

14. **Precondition:** lock held.
15. SKU-11 = 99.
16. The DLQ gains O-7002 with the original bytes.
17. The DLQ gains O-7003 after one attempt.
18. Lock-timeout redelivery logged, then SKU-14 = 99.
19. SKU-15 = 99.
20. The DLQ holds exactly 4.

LGTM:

21. Trace.
22. Log.

**Negative witnesses (experiments).**

- `FAILURE_STRATEGY=fail`: the channel stops at O-7002, readiness DOWN, SKU-15 stays 100. Checks 5, 6, 11, 12, 13 fail; check 3 is the precondition.
- `RETRY_MAX=0`: O-7004 is dead-lettered instead of reserved. Check 10 fails; check 8 proves the lock happened.
- Camel route with the `errorHandler` line removed (default error handler): O-7002 and O-7003 are logged and skipped with the offset committed. Check 16 or 17 fails; the records are gone.
- Camel `breakOnFirstError=true` without DLC: the poison is retried forever and SKU-15 stays 100. Observed only.
- Hold the lock longer than `RETRY_MAX` x delay: O-7004 is dead-lettered after the last attempt (exhausted vs non-retryable).
- `max.poll.interval.ms=10000` with a 20 s lock (S5): Camel leaves the group and redelivers; SmallRye keeps polling. Kept only if S5 reproduces it 3/3.

**Comparison table (one, in prose).** Rows:

- Where retry is declared.
- How a non-retryable failure is classified.
- What lands on the DLQ (bytes, headers, source topic, partition and offset).
- Deserialization failures.
- The default with nothing configured: `fail` stops the app; Camel's error handler skips and commits.
- When the offset is committed.
- What blocks while retrying.
- A non-blocking retry option: `delayed-retry-topic`, experimental.
- `max.poll.interval.ms` exposure (if S5 shows it).

Every cell is tested by a check, an mvn test or an experiment, or sourced.

**Figures.**

- `13-failure-classes`: malformed or invalid goes to the DLQ at once; transient is retried then reserved; exhausted goes to the DLQ.
- `13-retry-blocks-partition`: O-7005 waits behind O-7004 while it retries.
- Optional: `13-poison-pill-defaults` (SmallRye fail vs Camel skip).

**Callout.** Names `OrderPlacedConsumer`, `ReservationService`, `DeadLetterRoute`. Rule: retry what can succeed later, dead-letter what never will, and keep the original bytes and offset on the dead letter.

**Citations.** Hohpe and Woolf Dead Letter Channel and Invalid Message Channel; SmallRye "Receiving Kafka records" (failure strategies, DLQ, deserialization failures, delayed retry experimental); Quarkus Kafka guide 4.4 and the Fault Tolerance retry section; Quarkus SmallRye Fault Tolerance guide (`@Retry`, `retryOn`, `abortOn`); Camel Dead Letter Channel, Error Handler, Exception Clause, Kafka component (`breakOnFirstError`, poll strategy, `maxPollIntervalMs`); Kafka consumer configs (`max.poll.interval.ms`); PostgreSQL `lock_timeout` and `SELECT FOR UPDATE`; Bellemare ch 18; Lawrence ch 2. Link EIP Camel ch 5 (and ch 15 for duplicates from redelivery). Backward reference to ch 11: a retried effect needs to be idempotent.

## domain-model 2.2.0 (step 1C)

- `Topics.ORDERS_PLACED_DLQ = "edp.orders.placed-dlq"`, Javadoc "Dead letters of edp.orders.placed: records that cannot be parsed or are invalid". Not in `ALL`.
- Test `deadLetterTopicIsOutsideTheCatalog` (regex `edp\.[a-z]+\.[a-z-]+`, not in ALL).
- No other constants: 11 and 12 use `ORDERS_PLACED`, `INVENTORY_RESERVED` and `PAYMENTS_PROCESSED`.
- `_plans/domain-model.md`:
  - Version line to 2.2.0.
  - New catalog note "Chapter 13 adds `edp.orders.placed-dlq`, not in `Topics.ALL`".
  - Failure paths gain "A malformed event: dead letter topic".
  - Rows 11 to 13 rewritten:
    - 11: inventory-service reserves stock once per OrderPlaced event id (the `id` header); the inbox row commits in the same transaction as the decrement; a duplicate without the inbox double-decrements `quantityOnHand`; Camel's idempotent consumer with a repository as the alternative.
    - 12: payment-service reads InventoryReserved and publishes PaymentProcessed (carrying the `Payment`) with its consumed offsets in one Kafka transaction; read_committed readers; no database write, because a Kafka transaction covers Kafka only (the database edges are chapters 08 and 11); a Camel transacted route as the comparison.
    - 13: a malformed or invalid OrderPlaced goes to `edp.orders.placed-dlq`; a transient failure (the stock row locked past the lock timeout) is retried and succeeds; SmallRye failure strategy vs Camel deadLetterChannel.

## Spikes (1B, scratchpad only, under the stack lock, Dev Services)

- **S1 (ch 11)**:
  - Inbox `ON CONFLICT DO NOTHING` via a native query from a `@Transactional` method called by `@Incoming`.
  - Halt after commit and before ack gives redelivery 3/3; observe the committed offset after the halt (throttled commit).
  - `JpaMessageIdRepository` with camel-quarkus-jpa's `TransactionStrategy` inside `.transacted()` (camel-quarkus-jta): does `add()` join the route's JTA transaction? Test: throw after the effect and check both the key and the effect roll back. Also check its entity and table mapping against Flyway with `validate`, and the need for `quarkus.index-dependency`.
  - `JdbcMessageIdRepository` under JTA: separate commit or error (recorded).
  - Output: the Camel alternative's form and whether a gap experiment exists.
- **S2 (ch 12)**:
  - `withTransactionAndAck` batch on 4.37.0 against `apache/kafka:4.3.2`.
  - Dev Services env needed for the transaction state log.
  - Crash switch placement so that the partial records reach the broker (precondition 3/3).
  - Restart fences and aborts in under 30 s.
  - read_committed exactly 10, read_uncommitted 10 + k.
  - Offsets committed in the transaction; `kafka-transactions.sh list/describe` output.
  - LSO hold while down; `transaction.timeout.ms` override.
  - `markForAbort` behaviour, and the poison-in-batch loop (abort, seek back, abort again): record it; if it loops, prose states it and the example avoids it.
- **S3 (ch 12)**:
  - Camel `transacted=true` route consuming from Kafka: one transaction per exchange, offsets outside it.
  - Is the generated `transactional.id` stable across restarts (fencing) or is an explicit `transactionalId` needed?
  - Crash gives committed duplicates 3/3; Camel consumer `isolationLevel` default.
- **S4 (ch 13, SmallRye)**:
  - DLQ value for a deserialization failure with `fail-on-deserialization-failure=false` (original bytes?) and its headers.
  - Validation failure goes to the DLQ with the reason.
  - `@Retry` on the `@Incoming` method around a separate `@Transactional` bean (new transaction per attempt); `retryOn`/`abortOn` exception types for the PostgreSQL lock timeout through Hibernate.
  - Lock-timeout mechanism (hint vs `SET lock_timeout`).
  - `failure-strategy=fail` behaviour and readiness.
  - FT retry metrics visible in LGTM or not.
- **S5 (ch 13, Camel)**:
  - DLC to Kafka with `useOriginalMessage` (byte-identical?).
  - `onException(...).maximumRedeliveries(0)` reaches the DLC.
  - Default error handler skips and commits.
  - `breakOnFirstError=true` loops.
  - Redelivery re-invokes the transactional bean in a new transaction.
  - `max.poll.interval.ms` exceeded during redelivery: rebalance and redelivery for Camel; none for SmallRye.
- **S6 (tooling, shared)**: console producer header syntax in 4.3.2; console consumer `--isolation-level read_committed`; `kafka-consumer-groups --reset-offsets` on an inactive group; record-line filtering with the KIP-848 notice.

## Steps

| Step | Work | Files | Depends | Parallel | Stack | Status |
|---|---|---|---|---|---|---|
| 0 | Preflight: branch, Docker clean, iteration-plan r05 in progress, save plan | `_plans/iteration-plan.md`, `_plans/r05-plan.md` | owner OK | none | no | pending |
| 1A | Re-survey + source fetches; claim rows | `_plans/versions.md`, `_plans/sources.md`, `bibliography.md` | 0 | 1B, 1C | no | pending |
| 1B | Spikes S1 to S6, scratchpad only | scratchpad | 0 | 1A, 1C | lock, about 90 min, serial | pending |
| 1C | domain-model 2.2.0 + test; domain-model.md rows 11 to 13, catalog note, failure path | `examples/domain-model/**`, `_plans/domain-model.md` | 0 | 1A, 1B | mvn | pending |
| G0 | Gate: spikes pass or fallback chosen; install 2.2.0; bump every example pom 00 to 10 to 2.2.0 and `mvn -B verify` each (Containerfiles build domain-model from source, so a stale 2.1.0 reference breaks fresh builds) | example poms | 1A to 1C | none | mvn under lock | pending |
| 2A to 2C | Chapters 11, 12, 13: code, Dev Services tests, Containerfile, compose, README, example page, figure specs and outputs, chapter draft without numbers | each owns `examples/NN-*/**`, `_docs/NN-*.md`, `_example_pages/NN-*.md`, `diagrams/specs/NN-*.py`, `diagrams/NN-*.*` | G0 | 3 executors | mvn under lock | pending |
| 3 | Runtime windows, serial, exclusive, 90 min cap, order 12, 11, 13: verify N/N twice from clean, every experiment once, no `edp-*` left; fill numbers; freeze check numbering | as 2 | 2 | serial | exclusive | pending |
| 4 | Orchestrator re-run of every prose experiment, logged with failing check numbers; grep "check N" after any renumbering | `_plans/verification-log.md` | 3 | serial | yes | pending |
| 5 | Final serial run 00 to 13 (fresh containers each), static gates, claims + log, per-chapter commits, host_ids before each | claims, log | 4 | serial | yes | pending |
| 6 | Opus validation (max 2 repair rounds): claims audit, check-bite audit, originality n-gram vs EIP 5 and 15 and CNDP 5 and 6, fresh clone, word counts, figure regeneration byte-identical; orchestrator re-runs after each round | none | 5 | none | yes | pending |
| 7 | Ship: PR, CI green, squash on owner OK, tag r05.0, notes-only release, delete branch; PRD rows 11 to 13 (fix the stale freight wording: Settlement, RateQuote, CustomsDeclaration) and roadmap; iteration-plan done | as listed | 6 | none | no | pending |

Executors make no git writes and do not edit `claims.md`, `verification-log.md`, `CLAUDE.md`, `_infra` or `validate.py`. Live stacks run only in step 1B, the step 3 windows and steps 4 to 6, one at a time, under `stack-guard.sh`. Every verify.sh starts with `down -v --remove-orphans` and then `up -d --build --force-recreate --renew-anon-volumes --wait`.

## Acceptance criteria

**11**:

- verify N/N, N >= 20, from clean twice; mvn green.
- With the inbox: one decrement per event id across a duplicate record (precondition: 2 records with the same `id`) and across a crash redelivery (preconditions: exit 137, offset not committed, effect committed before the crash).
- Without the inbox: double decrement on the same input (88).
- Camel alternative: 91, with its key table.
- An mvn test proves a failed effect leaves no inbox row; concurrency test 20/20.
- Experiments recorded.

**12**:

- verify N/N, N >= 18.
- After a crash mid-batch, read_committed holds exactly one PaymentProcessed per input, within 30 s of restart.
- read_uncommitted shows the aborted partial records (k >= 1).
- Group offset advances only with commits.
- The Camel transacted comparison commits duplicates (in-run, with preconditions).
- No database in the example; the boundary is stated with sources.

**13**:

- verify N/N, N >= 20.
- Malformed and invalid OrderPlaced records land on `edp.orders.placed-dlq` with the original bytes, source topic and offset; the invalid one after one attempt.
- The locked row is retried and reserved, with a lock-timeout precondition.
- The record behind it waits, then is processed.
- Both frameworks on the same input.
- `FAILURE_STRATEGY=fail` and `RETRY_MAX=0` experiments fail the named checks.
- Every table cell backed.

**Every chapter**:

- <= 2,500 words (target about 2,000), one callout, >= 2 figures from specs.
- No dashes, versions, test names, self-reference, forward references or aphorisms.
- `part: getting-events-out-reliably`.
- UBI 10 Containerfile; env conventions (`KAFKA_BOOTSTRAP_SERVERS`, `CAMEL_COMPONENT_KAFKA_BROKERS`, Quarkus env mapping); `/q/health/ready` healthcheck.
- LGTM traces, metrics and logs.
- Topics only from `Topics`.
- Every claim row verified or cut; every prose experiment has a re-run log entry.

**Iteration**:

- validate ok, voice 0 ban-tier, idiomatic 0 high, host_ids 0, site builds.
- domain-model 2.2.0 everywhere; final serial 00 to 13 logged.
- CI green; tag and release; PRD and iteration plan updated.

## Sources to fetch and confirm (1A)

**Kafka**:

- Design docs (message delivery semantics; transactions).
- Producer configs (`transactional.id`, `transaction.timeout.ms`, `enable.idempotence`, `transaction.two.phase.commit.enable`).
- Consumer configs (`isolation.level`, `max.poll.interval.ms`).
- KIP-98 Exactly Once Delivery and Transactional Messaging.
- KIP-447 Producer scalability for exactly once semantics.
- KIP-890 Transactions Server-Side Defense (only if cited).
- KIP-939 status plus the 4.3.2 `KafkaProducer` javadoc.
- `kafka-transactions.sh` operations doc.

**Strimzi**: "Exactly-once semantics with Kafka transactions" (re-read for the facts above).

**SmallRye 4.x docs (versioned pages if available, otherwise latest with the version caveat recorded)**: Kafka transactions and exactly-once; Receiving Kafka records (failure strategies, DLQ, deserialization failure handling, commit strategies).

**Quarkus**:

- Kafka guide (4.3, 4.4, 5.8, 7.2, 29.7, 29.8, blocking `@Transactional`).
- SmallRye Fault Tolerance guide.
- Hibernate ORM with Panache (native queries, lock modes and timeout hint).
- Transactions guide.

**Camel 4.22.x**:

- Idempotent Consumer EIP; JPA component; Camel Quarkus JPA and JTA extensions.
- Transactional Client EIP.
- Kafka component (`transacted`, `transactionalId`, `isolationLevel`, `breakOnFirstError`, `pollOnError`, KafkaIdempotentRepository).
- Dead Letter Channel; Error Handler; Exception Clause.
- Source files at tag camel-4.22.2: `KafkaProducer.java`, `KafkaTransactionSynchronization.java`.

**PostgreSQL 18**: INSERT (`ON CONFLICT`); `lock_timeout`; SELECT locking clause.

**Pattern pages**: Hohpe and Woolf Idempotent Receiver, Transactional Client, Dead Letter Channel, Invalid Message Channel; Richardson Idempotent Consumer.

**Books**: Bellemare ch 18 (13) by title; Lawrence ch 2 (11, 13).

**Sibling links**: EIP Camel ch 5 and ch 15 URLs; CNDP ch 5 and ch 6 URLs.

## Risks

| Risk | Detection | Response |
|---|---|---|
| Crash before partial records reach the broker (no visible aborted records) | S2 precondition 3/3 | small wait or `linger.ms=0` in the crash path; otherwise show the abort through `markForAbort` and keep the crash for the duplicate check only |
| Restart does not fence quickly (abort only at the 60 s timeout) | S2 timing | bound the check at timeout + margin and state the mechanism observed |
| Poison record inside a transactional batch loops forever | S2 | state it; the example feeds no poison to ch 12 |
| Camel transacted id not stable across restarts | S3 | explicit `transactionalId` |
| JPA repository does not join the JTA transaction | S1 | present it as a separate commit with the gap experiment; the table says so |
| camel-quarkus-jpa entity vs Flyway `validate` mismatch | S1, `@QuarkusTest` | match the mapping |
| `@Retry` and `@Transactional` interceptor order retries inside one aborted transaction | S4 | separate bean, verified in an mvn test |
| DLQ value for deserialization failure is not the original bytes | S4 | assert what is there (`deserialization-failure-data` header) and say so |
| Lock timing flaky | S4, two consecutive N/N | hold the lock 8 s, retry window 8 x 1 s plus margin; precondition on `pg_locks` |
| `max.poll.interval.ms` contrast not reproducible | S5 | drop the table row |
| Mode phases leak state | precondition resets (stock, inbox, repo table, group offsets) | asserted before each phase |
| domain-model bump breaks older examples | G0 mvn verify 00 to 10 | fix now |
| SmallRye latest docs describe 5.x | 1A versioned pages + javap on 4.37.0 | cite only behaviour confirmed on 4.37.0 |
| KIP-939 API public in 4.3.2 | 1A | qualifier in ch 08 (fix now) and one sentence in ch 12 |
| Copying EIP 5 and 15 | n-gram check in step 6 | no DLC builder walkthrough, no repository table walkthrough |
| Forward references (11 naming DLQ or transactions) | validator read in step 6 | backward references only |
| Unrun experiments in prose | step 4 + validator | re-run all |
| verify.sh renumbering | frozen numbering + grep | re-map |
| Stack contention | lock | serial windows only |

## Owner decisions (recommendations)

1. **Ch 12 scope.** Kafka-only read-process-write. payment-service writes no `Payment` row; `Payment` travels in the PaymentProcessed payload. Domain row 12 is rewritten as above. Prose states the Kafka-only boundary (Strimzi, Quarkus chaining is not atomic) and points back to 08 and 11. No KIP-939 unless 1A finds a public API.
2. **Ch 12 comparison.** A Camel `transacted=true` route: atomic writes, offsets outside the transaction, so duplicates after a crash. Chosen over a plain at-least-once SmallRye path.
3. **Ch 13 transient failure.** A row lock past the lock timeout in inventory-service (real, deterministic, same service as the malformed OrderPlaced), not a payment-gateway stub. Domain row 13 is amended.
4. **DLQ topic.** `edp.orders.placed-dlq` as `Topics.ORDERS_PLACED_DLQ`, not in `ALL`. domain-model 2.2.0; every example pom 00 to 13 on 2.2.0 at G0.
5. **Inputs in 11 and 13.** Produced by verify.sh with the ch 08 record shape (key = order id, `id` header UUID). No order-service in those examples.
6. **Ch 11 inbox insert.** `INSERT ... ON CONFLICT (event_id) DO NOTHING` from a native query on the Panache `InboxEntry`, so one statement decides "first time" under concurrency. Check-then-persist is rejected.
7. **Ch 11 Camel alternative.** `JpaMessageIdRepository` in a `transacted()` route if S1 shows it joins the JTA transaction. Otherwise show the repository as a separate commit and demonstrate the gap. The JDBC repository stays linked to EIP ch 15.
8. **Same-input comparisons.** In one service via Quarkus profiles and per-mode consumer groups positioned with `kafka-consumer-groups --reset-offsets` before the container starts; the container is recreated within the run. Fresh containers per run are unchanged.
9. **Partitions.** All input and DLQ topics in 11 to 13 have 1 partition, created by topics-init.
10. **Ch 13 retry.** SmallRye Fault Tolerance `@Retry` (platform-managed) in memory. `delayed-retry-topic` appears in the table as experimental and is not run.
11. **Citations.** Bellemare ch 18 for 13 only; none for 11 and 12 unless the owner supplies section titles. Lawrence ch 2 for 11 and 13; none for 12.
12. **Pattern headings.** 11 `## Pattern: Idempotent Consumer` (inbox as its form); 12 `## Pattern: Transactional Client` (Kafka read-process-write); 13 `## Pattern: Dead Letter Channel` with retry inside it.
13. **Runtime window order.** 12 (Kafka and LGTM only), 11, 13.
14. **Crash switches.** Labelled demo code as in ch 08, recreated without the env after each crash so a redelivery does not crash again.
15. **PRD rows at ship.** PRD rows 11 to 13 still carry r01 freight names (Settlement, RateQuote/Invoice, CustomsDeclaration). Rewrite them to the order-to-delivery wording at ship.

### Critical Files for Implementation

- examples/domain-model/src/main/java/com/patterncatalyst/shipping/Topics.java
- _plans/domain-model.md
- examples/08-transactional-outbox/verify.sh (verify.sh, crash switch and recreate pattern to copy)
- examples/07-queues-for-kafka/notification-service/src/main/resources/application.properties (SmallRye plus Camel profile switching)
- examples/08-transactional-outbox/pom.xml (camel-bom 4.22.2 first, platform BOMs, domain-model version)
