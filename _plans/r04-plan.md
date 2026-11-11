---
layout: plan
title: r04 plan
render_with_liquid: false
---

# r04 plan: chapters 08 to 10, transactional outbox, Debezium in depth, connector offsets on compose (Opus planner, 2026-11-11)

Status: approved 2026-11-11 with all 13 recommendations, with two owner changes:

- Decision 1 changed: try Debezium 3.7.1.Final for debezium-quarkus-outbox first (S1 checks the mixed `debezium-api` jars against the Camel BOM's 3.6.3); fall back to 3.6.x only on a real failure, recorded in versions.md.
- Camel: the owner expects Camel 4.22.2 (the 4.22 patch released 2026-10-17). Camel Quarkus 3.40.0, the newest, is built on Camel 4.22.1 and the platform 3.40.1.2 carries 4.22.1. Spike S6 imports `org.apache.camel:camel-bom:4.22.2` ahead of the platform BOM; if every example 00 to 07 passes `mvn verify` and the final serial run, the pin moves to 4.22.2 (camel-launcher too); otherwise hold at 4.22.1 with the failure recorded.

## Findings

- Debezium line split: quarkus-camel-bom 3.40.1.2 manages Debezium 3.6.3.Final (`debezium-api`, `-embedded`, `-connector-postgres`, `-storage-*`); debezium-quarkus-outbox latest is 3.7.1.Final (3.6.4.Final exists); its `-common` depends on `debezium-api`, so importing the Camel BOM mixes 3.6.3 and 3.7.1 jars. Connect image is 3.7.1.
- Outbox extension writes inside the caller's transaction via `Event<ExportedEvent<?,?>>#fire()`. Default table `OutboxEvent` (id UUID, aggregateid, aggregatetype, type, timestamp, payload VARCHAR(8000), tracingspancontext). `quarkus.debezium-outbox.remove-after-insert` default true (insert and delete both in WAL, Debezium still emits). `additional-fields` allows a routing column. No Flyway or Quarkus-version support statement in the docs.
- camel-quarkus-debezium-postgres 3.40.0 is in the platform BOM (Q2 embedded comparison).
- EIP Camel appendix R covers Connect offsets REST with a FileStream connector and a rewind. Ch 10 goes PostgreSQL-specific: offset LSN vs slot `confirmed_flush_lsn`; server streams from max(requested, confirmed_flush), so alter skips forward but cannot rewind; reset = re-snapshot of current state; WAL retained while stopped; lost slot and recovery.
- Default Debezium topic `<prefix>.inventory.inventory_item` and heartbeat prefix `__debezium-heartbeat` break `edp.<domain>.<event>`; need router/prefix.
- Strimzi "Managing Connector Offsets" (2024-11-04): GET/PATCH/DELETE `/connectors/{c}/offsets`, alter and reset need a stopped connector; the rest (annotations, ConfigMap) is r12.
- `_infra` Postgres needs no change: `wal_level=logical`, 10 slots, `host replication` in pg_hba, `edp` superuser; `max_slot_wal_keep_size` is SIGHUP (ALTER SYSTEM + reload), removed by `down -v`.

## Approach

Same gate shape as r03: G0 spikes + source fetches (parallel), domain-model update, G1 three parallel authoring executors on disjoint files (mvn verify under lock), serial exclusive runtime windows, orchestrator re-run of every experiment the prose describes, final serial run, Opus validation, ship. Every guard has a comparison path in the same example on the same input. Connector JSON in `examples/NN/connect/*.json`, registered by verify.sh with `PUT /connectors/<name>/config`. Ch 10 Connect ops are curl in verify.sh; the app only observes.

Rejected: polling outbox with Camel SQL (EIP ch 15 teaches it; link); hand-rolled outbox entity (fallback only if S1 fails); ExtractNewRecordState in ch 09 (domain row says Camel maps the envelope; one sentence); own UBI Connect image (published image has EventRouter + PG connector); one shared 09/10 example (chapters stand alone); Strimzi half of ch 10 now (r12); Quarkus REST client wrapping Connect (curl is what operators use).

## Version re-survey (1A, record in versions.md)

1. Debezium latest final (3.7.1 or a 3.7.x patch).
2. `quay.io/debezium/connect` tag + digest (tag rebuilt 2026-11-10), bundled KAFKA_VERSION, `GET /connector-plugins` lists PostgresConnector, EventRouter loads.
3. debezium-quarkus-outbox 3.7.1 vs 3.6.x; quarkus.version each was built against.
4. quarkus-bom / quarkus-camel-bom respin after 3.40.1.2 on the 3.40 LTS stream; Debezium version the Camel BOM manages.
5. camel-quarkus-debezium-postgres version and its debezium-embedded; JVM mode on JDK 25.
6. CNPG operand 18.x tag (move library/postgres with it).
7. Maven 3.10 HOLD (#57216).
8. Kafka 4.3.2 still the newest the latest Strimzi supports.

No version in 08 to 10 prose.

## Per-chapter design

### 08 Transactional outbox (`examples/08-transactional-outbox`, `## Pattern: Transactional Outbox`)

order-service (8080; Panache, Flyway schema `orders`, debezium-quarkus-outbox, camel-quarkus-kafka) + Connect from `_infra`. Connector `order-outbox`: `table.include.list=orders.outboxevent`, pgoutput, `slot.name=edp_order_outbox`, EventRouter `route.by.field=topic`, `route.topic.replacement=${routedByValue}`, `table.expand.json.payload=true`, `table.fields.additional.placement=type:header:eventType`, key = aggregateid.

Code: `OrderEntity`; `OrderPlaced` payload record; `OrderPlacedEvent implements ExportedEvent` with additional field `topic` from `Topics.ORDERS_PLACED`; `OrderService.place()` `@Transactional` persist + fire; `OrderResource` `POST /orders` and `POST /orders/dual-write?mode=commit-first|publish-first`; `DualWriteCommitFirstRoute`, `DualWritePublishFirstRoute`; `CrashSwitch` (env `CRASH_AFTER_COMMIT_FOR=<orderId>`, `Runtime.halt` after commit before publish / before response; labelled demo code); Flyway `V1__orders.sql` (`orders.orders`, `orders.outboxevent`), Hibernate schema management off.

verify.sh (~20): infra/Connect healthy, connector RUNNING, slot active; happy path O-1001 exactly 1 record, key, `id` + `eventType` headers, payload = order, outbox table empty; duplicate POST 409, still 1 event, 1 row; control publish-first duplicate = 2 records 1 row (ghost); crash after commit O-2001 outbox: container exited, restart, row + exactly 1 event; control commit-first O-2002: row, 0 events after full wait (lost); Connect down: 5 orders 201, all 5 delivered in commit order after restart; rolled-back tx no event (if S1 shows the path); trace in Tempo, log in Loki.

Experiments: `remove-after-insert=false` (rows accumulate, count unchanged); Kafka stopped (outbox 201 and catch-up; commit-first 500 after commit, order without event); `docker kill` Connect during 200-order burst, count duplicate `id` headers (observed, not asserted; ch 11 handles it); drop `route.by.field` (default `outbox.event.*` topic, check 2 fails).

Figures: `08-dual-write-gap`, `08-outbox-wal-router`, optional `08-remove-after-insert`.

Citations: Debezium Outbox Event Router + Outbox Quarkus Extension; Richardson "Transactional outbox", "Transaction log tailing" (confirm); Bellemare ch 6; Lawrence ch 2; links to EIP Camel ch 15 and CNDP ch 4 without repeating (no polling code, no XA paragraph).

Callout names `OrderService`, `OrderPlacedEvent`, `DualWriteCommitFirstRoute`; rule: the event row commits in the order's transaction; nothing publishes from application memory.

### 09 Log tailing with Debezium (`examples/09-log-tailing-with-debezium`, `## Pattern: Transaction Log Tailing`)

inventory-service (8080; Camel; mem_limit 512m) + Connect. Legacy `inventory.inventory_item (item_sku text PK, qty_on_hand int, last_changed timestamptz)` created and seeded (5 SKUs) by verify.sh via psql; busy uncaptured `inventory.legacy_audit`; signal table `inventory.dbz_signal`. Connector `inventory-cdc`: slot `edp_inventory`, `publication.autocreate.mode=filtered`, JSON without schemas, RegexRouter to `edp.inventory.item-changes`, conforming `topic.heartbeat.prefix`, `signal.data.collection=inventory.dbz_signal`.

Code: `ChangeEnvelope`, `LegacyItemRow`, `InventoryItemDocumentRoute` (r/c/u map `after` to `InventoryItem`, send to `edp.inventory.items` keyed by sku; d tombstone; Debezium null tombstone filtered); `EmbeddedCdcRoute` (`CDC_MODE=embedded`, `debezium-postgres:`, slot `edp_inventory_embedded`, file offsets on a named volume, same mapper), switched by `autoStartup` like ch 07.

verify.sh (~24): healthy, slot + publication (only inventory_item + signal table); snapshot 5 `r` + 5 documents; update gives `u` with mapped names; `before` key-only under DEFAULT, full under REPLICA IDENTITY FULL; delete gives `d` + exactly 1 tombstone downstream; 3 updates in one tx arrive in LSN order; heartbeat witness (retained bytes grow past threshold without heartbeat while legacy_audit takes ~50 MB; fall below with heartbeat; S3 decides on `heartbeat.action.query`); incremental snapshot re-emits N `r` while a concurrent update still arrives, document count = row count; embedded mode gives the same sorted sku:qty set; LGTM trace/log.

Experiments: remove tombstone filter; DEFAULT vs FULL on delete; heartbeat off (record bytes); negative qty row (record actual behaviour; DLQ is ch 13, no forward reference); embedded offset file deleted re-snapshots.

Figures: `09-wal-publication-slot`, `09-envelope-to-document`, `09-snapshot-then-stream`, `09-connect-vs-embedded`.

Citations: Debezium PG connector (snapshot modes, replica identity, WAL disk space, heartbeats, heartbeat prefix); signals and incremental snapshots; event flattening (one line); PostgreSQL logical decoding, `pg_replication_slots`; Camel Debezium PostgreSQL component; Bellemare ch 6; Lawrence ch 9; Richardson "Transaction log tailing".

Callout names `InventoryItemDocumentRoute`, `ChangeEnvelope`, `EmbeddedCdcRoute`; rule: one slot per reader; a slot nobody advances keeps WAL forever.

### 10 Managing connector offsets (`examples/10-managing-connector-offsets`, topic chapter)

inventory-service (8080), same legacy table and seed, connector `inventory-cdc` `snapshot.mode=initial`; example compose overrides Connect `OFFSET_FLUSH_INTERVAL_MS=1000` (prose states the 60 s default). `ChangeLogRoute` writes each envelope to `inventory.item_change` (Panache `ItemChange`: op, sku, qty, lsn, received_at).

verify.sh (~24): running snapshot 5, `GET /offsets` partition `{"server": <prefix>}` with `lsn` <= slot `confirmed_flush_lsn` (S4 records the relation), offsets topic same key; PATCH while RUNNING refused, `PUT /stop` STOPPED; stopped: 100 updates, slot inactive, retained bytes grow, no rows; resume all 100 arrive, retained bytes fall; forward alter: 10 bad-batch updates skipped, 11th arrives (control = previous check); backward alter: 0 redelivered (or S4 behaviour stated); reset: exactly 5 `r` with final values, SKU-1 once (no history); lost slot (`max_slot_wal_keep_size='1MB'`, `pg_switch_wal` + CHECKPOINT, `wal_status=lost`): FAILED, restart with includeTasks/onlyFailed stays FAILED; recovery drop slot + DELETE offsets + resume gives RUNNING and fresh snapshot of 5; LGTM trace/log.

Experiments: PATCH without stop; `snapshot.mode=no_data` then reset (nothing re-delivered); incremental snapshot signal instead of reset; default flush interval then kill Connect (count redelivered, observed).

Figures: `10-offset-lifecycle`, `10-offset-vs-slot`, `10-lost-slot-recovery`.

Citations: KIP-875; Kafka Connect REST API (stop, offsets, restart); `offset.flush.interval.ms`; Strimzi "Managing Connector Offsets" (REST workflow + stopped requirement); Debezium PG connector (offsets, snapshot modes); PostgreSQL `START_REPLICATION ... LOGICAL` ("whichever is greater"), `max_slot_wal_keep_size`, `wal_status`; link EIP Camel appendix R. "Auto-restarting connectors" is r12.

Layout: The problem; Where a source connector keeps its place; Stopping, listing, altering, resetting; Offsets and the replication slot; When the slot is lost; How Quarkus and Camel fit in; Build, run, observe; When not to touch offsets; References. One callout naming `ChangeLogRoute`, `ItemChange`, `inventory-cdc`; rule: stop, then alter forward or reset; a backward LSN is clamped.

## Steps

| Step | Work | Files | Depends | Parallel | Stack | Status |
|---|---|---|---|---|---|---|
| 0 | Preflight: branch, Docker clean, iteration-plan r04 in progress, save plan | `_plans/iteration-plan.md`, `_plans/r04-plan.md` | owner OK | none | no | pending |
| 1A | Re-survey + source fetches; claim rows | `_plans/versions.md`, `_plans/sources.md`, `bibliography.md` | 0 | 1B, 1C | no | pending |
| 1B | Spikes S1 to S5, scratchpad only | scratchpad | 0 | 1A, 1C | lock, ~60 min | pending |
| 1C | domain-model 2.1.0: `INVENTORY_ITEM_CHANGES`, `INVENTORY_ITEMS` (not in `ALL`) + tests; domain-model.md rows 08 to 10 | `examples/domain-model/**`, `_plans/domain-model.md` | 0 | 1A, 1B | mvn | pending |
| G0 | Gate: spikes pass or fallback chosen; Debezium alignment; install domain-model; bump 00 to 07 poms | example poms | 1A to 1C | none | mvn | pending |
| 2A to 2C | Chapters 08, 09, 10: code, Dev Services tests, README, example page, figure specs, draft without numbers | each owns `examples/NN-*/**`, `_docs/NN-*.md`, `_example_pages/NN-*.md`, `diagrams/specs/NN-*.py`, `diagrams/NN-*.*` | G0 | 3 executors | mvn under lock | pending |
| 3 | Runtime windows, serial, exclusive, 90 min cap, order 09, 10, 08: verify N/N, every experiment once, no `edp-*` left; fill numbers | as 2 | 2 | serial | exclusive | pending |
| 4 | Orchestrator re-run of every prose experiment, logged with failing check numbers; freeze numbering, grep "check N" after any renumbering | `_plans/verification-log.md` | 3 | serial | yes | pending |
| 5 | Final serial run 00 to 10, static gates, claims + log, per-chapter commits, host_ids before each | claims, log | 4 | serial | yes | pending |
| 6 | Opus validation (max 2 repair rounds): claims audit, check-bite audit, originality vs EIP 15/R and CNDP 4, fresh clone, word counts; orchestrator re-runs after each round | none | 5 | none | yes | pending |
| 7 | Ship: PR, CI green, squash on owner OK, tag r04.0, notes-only release, delete branch; PRD rows 08 to 10 and roadmap; iteration-plan done | as listed | 6 | none | no | pending |

Executors make no git writes and do not edit claims.md, verification-log.md, CLAUDE.md, `_infra` or validate.py.

Spikes: S1 outbox extension on Quarkus 3.40.1.2 with the Camel BOM (dependency tree, 3.7.1 then 3.6.x; Flyway table vs mapping; `additional-fields` topic reaches EventRouter; remove-after-insert emits; OTel; PK violation at commit rolls back the outbox insert and maps to 409). S2 Connect image plugins, EventRouter, RegexRouter, heartbeat prefix. S3 heartbeat with pgoutput, filtered publication and a busy uncaptured table: interval alone or `heartbeat.action.query`. S4 KIP-875 on PostgresConnector: offset shape, PATCH response, forward skip, backward clamp, DELETE then snapshot, duplicates after re-snapshot, lost slot status and recovery. S5 camel-quarkus-debezium-postgres options, headers, 512m memory, coexistence with the Connect slot. S6 Camel 4.22.2 override: camel-bom 4.22.2 imported before the platform BOM in one example, `dependency:tree` shows camel-* 4.22.2 only, `mvn verify` green; then G0 applies it to 00 to 07 and runs `mvn verify` for each.

## Acceptance criteria

08: verify N/N, N >= 18, from clean; mvn green; one event per committed order (key, `id` + `eventType` headers, payload, outbox empty); duplicate 409 still 1 event; publish-first ghost (2 events, 1 row); outbox crash delivers, commit-first crash loses (row, 0 events after full wait); Connect down 5 accepted 5 delivered in per-key order; four experiments recorded.

09: verify N/N, N >= 20; 5 `r` then c/u/d mapped; `before` key-only DEFAULT, full FULL; exactly 1 tombstone per delete; heartbeat witness with threshold and precondition; incremental snapshot re-emits while streaming; embedded and Connect identical document sets.

10: verify N/N, N >= 20; PATCH while running refused; stopped retains WAL, resume delivers all 100; forward alter skips 10, next arrives; backward alter 0 replayed (or S4 behaviour stated); reset exactly 5 `r`, no history; lost slot FAILED, restart no fix, drop slot + reset recovers.

Every chapter: <= 2,500 words (target ~2,000); one callout; >= 2 figures from specs; no dashes, versions, test names, self-reference, forward references, aphorisms; `part: getting-events-out-reliably`; UBI 10 Containerfile; env conventions; `/q/health/ready`; LGTM traces/metrics/logs; every claim row verified or cut; every prose experiment has a re-run log entry.

Iteration: validate ok, voice 0 ban-tier, idiomatic 0 high, host_ids 0, site builds; final serial 00 to 10 logged; CI green; tag + release; PRD and iteration plan updated.

## Sources to fetch and confirm (1A)

Debezium: Outbox Event Router; Outbox Quarkus Extension; PostgreSQL connector in full (snapshot modes, replica identity, WAL disk space, heartbeats, heartbeat prefix, slot name, publication autocreate, tombstones, offset fields); signals; incremental snapshots; new record state extraction; any Debezium page on KIP-875 offsets. Kafka: KIP-875; Connect REST API 4.3; `offset.flush.interval.ms`. PostgreSQL 18: logical decoding concepts; `START_REPLICATION`; `pg_replication_slots`; `max_slot_wal_keep_size`; publications. Camel: Debezium PostgreSQL component + Camel Quarkus extension. Strimzi: Managing Connector Offsets; Auto-restarting connectors (r12). Richardson: Transactional outbox, Transaction log tailing. Bellemare 2e ch 6 (08, 09). Lawrence ch 2 (08), ch 9 (09).

## Risks

| Risk | Detection | Response |
|---|---|---|
| Outbox extension fails on 3.40 or mixed Debezium jars | S1 | align on 3.6.x; else keep Camel off the order-service BOM path; else Panache `OutboxEvent` in the same transaction, reason in versions.md |
| Flyway vs extension mapping mismatch | S1, `@QuarkusTest` schema validation | match the mapping |
| PK violation at commit gives 500 not 409 | S1 | assert status class + row and event counts |
| Crash switch unreachable in a transacted route | S1 | split persist and send into two steps |
| Heartbeat relation does not reproduce | S3 | rewrite around `heartbeat.action.query` or the stopped connector; state actual behaviour |
| PostgresConnector alter differs | S4 | assert observed behaviour, adjust prose and figure |
| Lost slot impractical | S4 | `pg_switch_wal` loop; else demote to a logged experiment |
| Stale GET offsets from flush lag | S4 | 1 s flush in example compose |
| Memory (Connect 2g + embedded engine) | OOMKilled | 512m for 09 |
| Leftover slots | precondition check | `down -v` on exit |
| Stack contention | lock | serial windows only |
| Unrun experiments in prose | step 4 + validator | re-run all |
| verify.sh renumbering | frozen numbering + grep | re-map |
| Copying EIP 15, EIP R, CNDP 4 | n-gram check | no XA paragraph, no polling code, no FileStream walkthrough |

## Owner decisions (recommendations)

1. Debezium alignment: outbox extension on the 3.6.x line the Camel BOM manages; 3.7.1 only if S1 shows no mixed jars; Connect stays 3.7.1; split recorded.
2. New topics `edp.inventory.item-changes` (raw envelope) and `edp.inventory.items` (compacted state documents keyed by sku); in `Topics`, not in `Topics.ALL`.
3. domain-model 2.1.0, all poms bumped at G0.
4. Ch 08 routing: outbox `additional-fields` column `topic` from `Topics.ORDERS_PLACED`, `route.by.field=topic`.
5. Ch 08 dual-write comparison: both orders as Camel routes, `CrashSwitch` labelled demo code.
6. Ch 08: no consumer service; verify.sh reads topic and headers.
7. Ch 09/10 legacy table created by psql from verify.sh with legacy column names, not Flyway.
8. Ch 09 embedded comparison in the same service, `CDC_MODE=connect|embedded`, separate slots.
9. Ch 10 lost slot: asserted checks if deterministic, otherwise an experiment.
10. Heartbeat topic prefix set to the `edp.` convention.
11. Drop Lawrence ch 6 for 08; keep ch 2.
12. Ch 10 Connect ops via curl, no REST-client wrapper.
13. Runtime window order 09, 10, 08.

## G0 decisions (2026-11-11, from spikes S1 to S6 and the 1A re-survey)

- **Outbox extension 3.7.1.Final** works beside the Camel BOM (mixed `debezium-api`/`debezium-util` 3.6.3 under it, no build, boot or runtime failure). No 3.6.x fallback.
- **Camel 4.22.2**: `org.apache.camel:camel-bom:${camel.version}` imported first in `dependencyManagement` of every Camel example; `dependency:tree` shows only 4.22.2. Applied to 00 to 07 at G0, kept only if all `mvn verify` pass.
- **Ch 08**: DDL (lower case, unquoted) `orders.outboxevent(id uuid primary key, aggregatetype varchar(255) not null, aggregateid varchar(255) not null, type varchar(255) not null, "timestamp" timestamptz not null, payload varchar(8000), tracingspancontext varchar(256), topic varchar(255) not null)`; `quarkus.hibernate-orm.database.default-schema=orders`; `quarkus.debezium-outbox.additional-fields=topic:string`; schema-management `validate` (a missing `topic` column fails boot). Duplicate PK surfaces as `RollbackException` caused by `ConstraintViolationException`: map with `@ServerExceptionMapper` on `RollbackException`, narrowed to the constraint-violation cause, to 409. Needs `camel-quarkus-direct`, `camel-quarkus-bean`, `@Named` on the bean. Commit-first crash: `direct:` route, `.bean()` on a `@Transactional` method, then the crash processor, then `toD kafka`. Headers on the event: `id`, `eventType` (plus `__debezium.context.*`).
- **Ch 09 heartbeat witness changes**: `heartbeat.interval.ms` alone does NOT release WAL (57.6 MB retained, never drops). With `heartbeat.action.query` writing a heartbeat table that is in `table.include.list`, retained bytes fall to under 4 KB after one offset flush. The witness is interval-only vs interval plus action query. `lsn.flush.mode=connector_and_driver` is a one-line alternative in prose (also measured, 192 B). Heartbeat topic: `topic.heartbeat.prefix=edp.heartbeat` gives `edp.heartbeat.edp` (the `heartbeat.topics.prefix` key listed by validate is ignored). The heartbeat table gets its own data topic; route it away or filter it.
- **Ch 09 embedded**: Camel Debezium engine is Debezium 3.6.3 (state it is the library, not the Connect image). Body is the `after` Struct (`convertBodyTo(Map.class)`); delete has null body and the before row in `CamelDebeziumBefore`. Set the Kafka key with `toD("kafka:...?key=${header.sku}")` style or verify the header approach in the test. 512m is fine for the 5-row seed; claim nothing for large tables (20k-row snapshot OOMKilled at 512m).
- **Ch 10 changes**:
  - GET offsets: `{"partition":{"server":"edp"},"offset":{"lsn","txId","ts_usec"(,"lsn_proc","lsn_commit","messageType")}}`; offset keys cited from the Debezium source at v3.7.1.Final (docs do not list them).
  - PATCH and DELETE while RUNNING: HTTP 400 "Connectors must be in the STOPPED state". Forward alter needs `{"lsn": <bigint>}` only.
  - Stopped retention: 100 small updates grow WAL only ~14 KB; use a bulk insert to make retention visible.
  - Reset with `initial`: exactly 5 `r`, no duplicate `u`. Reset with `no_data`: replays the slot's unconfirmed backlog from `confirmed_flush_lsn`; "nothing re-delivered" only without backlog. The experiment states this.
  - `offset.mismatch.strategy` (Technology Preview; default `no_validation`, PostgreSQL 15+ starts from `confirmed_flush_lsn`): one sentence beside the backward-alter check.
  - **Lost slot**: `wal_status=lost` is deterministic (3/3), but the connector then stays RUNNING with `tasks: []` and retries for about 30 minutes; it does not go FAILED. Asserted checks: `wal_status=lost`, connector delivers nothing, `restart?includeTasks=true&onlyFailed=true` changes nothing; recovery `stop`, wait for `active=f`, `pg_drop_replication_slot`, `DELETE offsets`, `resume` gives RUNNING and a fresh 5-row snapshot (3/3, ~10 s). The FAILED message ("no longer available on the server ... snapshot mode") is shown by a separate deterministic path: slot dropped while offsets are kept. Drop the slot only after `active=f`.
- **Connect image digest** moved (rebuilt 2026-11-11): `quay.io/debezium/connect:3.7.1.Final@sha256:d24bacbb16ce2e75ce42aabc6c14bf679201051eb93e4c60aa204ec5a28e1ab0`; update the `_infra` pin at G0 (bundles Kafka 4.3.1, JDK 25).
- Spike write-up: scratchpad `r04-spikes/REPORT.md` (not committed; configs above are the record).
