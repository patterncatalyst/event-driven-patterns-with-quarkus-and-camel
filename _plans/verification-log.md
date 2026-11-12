---
layout: plan
title: "Verification log"
render_with_liquid: false
---

# Verification log

Run records per chapter. Chapters carry no verification footer; this file holds the evidence. The plans collection is `output: false`, so it is not published. Each entry: date, chapter, example, what was run on a clean stack with nothing else running, pass counts, observed effects, negative witness result. Scrub with `host_ids.py` before pasting any output.

## r01

### 2026-11-10 · examples/_infra (step 7) · compose stack bring-up

Clean Docker state, nothing else running. `docker compose -f examples/_infra/compose.yaml up -d --wait`: 5/5 services healthy in 17.7 s.

| Check | Result |
|---|---|
| Kafka 4.3.2 topic create + list (EXTERNAL localhost:9092, INTERNAL kafka:9094) | ok |
| `group.coordinator.rebalance.protocols` | classic,consumer,streams,share (default lacks share); share coordinator RF 1 / min ISR 1 |
| `share.version` finalized | 1; share consumer started before produce received 1 message; started after produce timed out (share groups default to latest) |
| PostgreSQL | 18.6 (CNPG operand image), wal_level=logical, max_replication_slots=10, runs as UID 26 |
| Apicurio | 3.3.4, health UP on 19000 |
| Debezium Connect 3.7.1.Final | Kafka client 4.3.1, JDK 25; PostgresConnector and io.debezium.transforms.outbox.EventRouter loaded |
| LGTM | Grafana 13.3.0 on 3000; OTLP 4318 HTTP 200, 4317 open; Prometheus 3.16.0 on 19090; Tempo 3200 ready; Loki 3100 ready |
| OOMKilled | false for all |
| `scripts/setup-stack.sh --clean` | no edp- containers, volumes or network remain |

### 2026-11-10 · step 11 · chapters 00 and 01 from a clean Docker state

`scripts/setup-stack.sh --clean`, no edp- containers or volumes, then each example alone, one after the other.

- `examples/00-stack-check/verify.sh`: **17/17 checks passed** (all six containers healthy; valid Consignment accepted and keyed on `consignments`; wal_level logical; Apicurio ready; Connect lists PostgresConnector and EventRouter; Tempo trace, Prometheus metric and Loki log for service stack-check; negative witness: invalid Consignment gets 400 and the topic does not grow). `mvn -B verify`: 3/3.
- `examples/01-crud-vs-events/verify.sh`: **10/10 checks passed** (1 row vs 4 events; replay equals row; row shows latest only; history BOOKED, LOADED, IN_TRANSIT, CLEARED; illegal transition 409 appends nothing; UPDATE on consignment_event refused and events unchanged; Tempo trace for crud-vs-events). `mvn -B verify`: 6/6. Negative witnesses (executor run): lifecycle check removed fails the illegal-transition test; trigger removed fails the append-only test; event append removed fails 5 of 6.
- Defect found and fixed during step 10: `_infra/postgres/start.sh` lacked a `host all all all scram-sha-256` pg_hba line, so containers on the `edp` network could not connect (chapter 00 never connects as an app, so it passed). Fixed before this run.
- After both runs: no edp- containers or volumes remain.

### 2026-11-10 · final run after repair rounds 1 and 2

Clean Docker state (`scripts/setup-stack.sh --clean`), each example alone, one after the other.

- `examples/00-stack-check/verify.sh`: **17/17 checks passed**. `mvn -B verify`: 4/4.
- `examples/01-crud-vs-events/verify.sh`: **12/12 checks passed** (adds TRUNCATE refused and events unchanged). `mvn -B verify`: 9/9.
- Experiments recorded: ch00 removing `onException` gives 16/17 (check 16 fails; mvn: expected 400 but was 500); renaming the topic gives 15/17 (checks 8, 15). ch01 `Lifecycle.allows` true gives 9/12 (checks 6, 7, 9); dropping the UPDATE/DELETE trigger gives 11/12 (check 8); dropping V2 gives 10/12 (checks 10, 11); removing `appendEvent` gives 7/12 (checks 2, 3, 5, 7, 9).
- Fresh-clone run by the re-validator (domain-model not preinstalled): 00 17/17, 01 10/10 before round 2.
- Gates: validate.py ok (self-test 5/5), voice 0 ban-tier, idiomatic 0, host_ids 0 hits, site builds.
- After the runs: no edp- containers or volumes remain.

### r02 G1 (2026-11-10) · chapters 00 and 01 in the order-to-delivery domain

Orchestrator serial run from a clean Docker state, each step alone, one after the other.

- `examples/domain-model` 2.0.0 install: **79 tests** pass.
- `examples/00-stack-check`: `mvn -B verify` **4/4**; `./verify.sh` **17/17 checks passed**.
- `examples/01-crud-vs-events`: `mvn -B verify` **11/11**; `./verify.sh` **19/19 checks passed**.
- Static gates: `validate.py` ok, `host_ids.py check` 0 hits, voice scan 0 ban-tier, site build ok, grep for the freight names (Consignment, HandlingEvent, weight, real carriers) 0 hits in chapters and examples (the `FEDEX` literal in `DomainModelTest` is the negative witness for an unknown carrier).
- Experiments recorded:
  - ch00: removing `onException` gives **16/17** (check 16 fails); renaming the producer topic gives **15/17** (checks 8 and 15 fail).
  - ch01: `Lifecycle.allows` always true gives **16/19** (checks 11, 12, 15); removing the UPDATE/DELETE trigger gives **17/19** (checks 14, 16); removing V2 gives **17/19** (checks 17, 18); dropping `appendEvent` gives **14/19** (checks 6, 8, 10, 12, 15); removing the unique constraint fails the stale-version test (**10/11** mvn).
- Sources fetched while rewriting the claims table: PostgreSQL SQL key words appendix (ORDER reserved), CREATE TRIGGER (TRUNCATE only FOR EACH STATEMENT), ALTER TABLE (DISABLE TRIGGER), Docker Engine install and post-install pages.
- `claims.md` rewritten against the current prose: Consignment rows replaced by Order rows, new rows for the running-example section; domain facts only realised in later chapters are marked design.

### r02 final serial run (2026-11-10)

Orchestrator, clean Docker, under the stack lock, one at a time.

- `examples/domain-model`: **79 tests** pass.
- `examples/00-stack-check`: mvn **4/4**; `verify.sh` **17/17**.
- `examples/01-crud-vs-events`: mvn **11/11**; `verify.sh` **19/19**.
- `examples/02-event-notification`: mvn **6+4**; `verify.sh` **19/19**.
- `examples/03-event-carried-state-transfer`: mvn **8+7**; `verify.sh` **30/30**.
- `examples/04-ordering-and-partitioning`: mvn **4+6**; `verify.sh` **27/27**.
- All BUILD SUCCESS.
- Experiments recorded by the executors on 2026-11-10:
  - ch02: removing `breakOnFirstError` gives **17/19** (checks 12, 14 fail); dropping the key header makes 3 of the 6 order-service tests fail.
  - ch03: `ORDER_EVENTS_CARRY_STATE=false` gives **14/30**; `ORDER_EVENTS_ATTACHMENT=inline` gives **22/30**; inline with max-request-size 1048576 gives **20/30**; removing the status guard makes one `OrderCopyTest` fail.
  - ch04: `SHIPPING_PARTITION_KEY=event-type` for run 1 gives **25/27** (checks 3, 5 fail; 6, 7, 9 are run-dependent); `RESEQUENCE=true` on all runs gives **24/27** (checks 14, 15, 16 fail).
- Opus validation round 1: fresh-clone build with an empty Maven repository of domain-model plus 02, 03 and 04 `package`: BUILD SUCCESS.
- Repair round 1 re-runs follow.

## r02 repair round 1 re-run (2026-11-10)

Orchestrator, clean Docker, under the stack lock, one at a time:

- 02-event-notification: mvn 7 + 4 tests, BUILD SUCCESS; verify.sh 21/21. Experiment, `breakOnFirstError=true` removed from `OrderPlacedNotificationRoute`: 19/21, checks 14 (offset not committed past) and 16 (B's notification appears) fail; restored, diff clean.
- Recorded by the ch02 repair executor: `camel-quarkus-opentelemetry2` removed from order-service gives 20/21 (check 7 fails); observer set to `TransactionPhase.IN_PROGRESS` makes `rolledBackOrderPublishesNothing` fail.
- 03-event-carried-state-transfer: mvn 8 + 7, BUILD SUCCESS; verify.sh 30/30.
- 04-ordering-and-partitioning: mvn 4 + 46 runs (12 methods, two `@RepeatedTest(20)`), BUILD SUCCESS; verify.sh 28/28. Recorded by the ch04 repair executor: without the `ON CONFLICT` create the new-order concurrency test errors 20/20; without the row lock the existing-order test failed 1 of 20 (probabilistic).
- 00 and 01 unchanged in code since the final serial run (17/17, 19/19).
- 02 key-header experiment re-run after repair: order-service 7 tests, 3 fail (recordCarriesOnlyTheOrderIdKeyedByOrderId, orderIsReadableWhenTheRecordIsPublished, duplicateAndInvalidOrdersPublishNothing); restored.

## r03 runtime windows (2026-11-10 to 2026-11-11)

Each chapter executor had an exclusive window under the stack lock.

- 05-optimizing-publishers: verify.sh 24/24 from clean. Experiments, one guard broken each: A fails check 12; B fails 14, 15, 16, 20; C fails 13; D fails 17; E fails 22; F fails 19; G (`REQUIRED_ACKS=1` with idempotence left on) stops at startup with a `ConfigException`. Bench 5 repeats, `results/bench-2026-11-10.csv` and summary; linger and codec sweeps recorded.
- 06-optimizing-subscribers: verify.sh 36/36 from clean. Combined experiment run 29/36: `FIXED_MAX_POLL_RECORDS=500` fails 21 to 23; `CLASSIC_ASSIGNOR=CooperativeStickyAssignor` fails 29 (4 revoked instead of 9); `TRICKLE_MIN_BYTES=1` fails 26; `SECOND_PROTOCOL=classic` fails 30 and 33. Probe: after eviction 55 of 500 rows had attempts 2. Bench 5 repeats, `results/bench-2026-11-11.csv` (merged from two invocations; `consumers-12-roundrobin` run separately with `PROFILES`).
- 07-queues-for-kafka: verify.sh 26/26 from clean (two consecutive runs). `DELIVERY_MODE=group` gives 12/26 (check 9 and 15 among the failures); `SEED_LINGER_MS=500` gives 25/26 (check 9 fails). With a 15 s record lock checks 5 and 6 failed (the 8 s job was redelivered). Not run: pause-based lock expiry, `share.delivery.count.limit=2`.

## r03 final serial run (2026-11-11)

Orchestrator, clean Docker, under the stack lock, one at a time: domain-model 79 tests; 00 4 tests, 17/17; 01 11 tests, 19/19; 02 7 + 4 tests, 21/21; 03 8 + 7 tests, 30/30; 04 4 + 46 tests, 28/28; 05 6 tests, 24/24; 06 4 tests, 36/36; 07 10 tests, 26/26. All BUILD SUCCESS. No edp-* containers left.

## r03 quick bench confirmation (2026-11-11, 3 repeats, not committed)

- 05: default/unbatched throughput 4.2x (recorded 4.9x), records per request 58x; tuned/unbatched 0.39x (recorded 0.4x); linger 0, 5, 20, 100 ms gives 41, 187, 198, 200 records per request with p50 following linger; bytes per record lz4 0.38, gzip 0.23, zstd 0.19 of none (recorded 0.38, 0.23, 0.19). Every relation has the same direction.
- 06: drain medians consumers-1 19.29 s, consumers-3 6.38, consumers-9 6.36, consumers-12 6.37, consumers-12-roundrobin 2.23, poll-50 6.37 (recorded 19.18, 6.36, 6.35, 6.36, 2.22, 6.37). Same direction and size.

## r03 repair round 1 (2026-11-11)

Under the stack lock, one at a time:

- 05: bite run with the request metric renamed to a nonexistent series gives 19/24 (checks 5, 8, 11, 18, 20 fail); restored. Final verify.sh 24/24; mvn 6 tests, BUILD SUCCESS.
- 06: final verify.sh 36/36; mvn 4 tests, BUILD SUCCESS (label-only verify.sh edit after the mvn run).
- 07: `ShareDeliveryWorker` now a `@Blocking` method: verify.sh 26/26. `DELIVERY_MODE=group`: 11/26 (4 to 15 and 19 to 21 fail; new check 11 among them). `SHARE_RECORD_LOCK_MS=15000`: 22/26 (5 to 8 fail; slow job O-7001 sent at delivery count 1, 15.8 to 23.8 s, and count 2, 32.7 to 40.7 s; seven others twice; O-7028 five times). Orchestrator run `SHARE_ACQUIRE_MODE=record_limit`: 24/26 (5 and 6 fail, one job sent twice). Final verify.sh 26/26; mvn 10 tests, BUILD SUCCESS.

## r03 repair round 2, chapter 07 (2026-11-11)

Cause of the acquire-ahead: the seed wrote bursts as one producer batch (kafka-dump-log: p1 offsets 0 to 6 in one batch), and the connector's poll retry back-off (retry-max-wait 30 s) slowed the share consumer while a record was unanswered. Fix: seed with `batch.size=0`; `retry-max-wait=1`, `poll-timeout=100`. verify.sh now 27 checks (check 9: at least half of later jobs overtake; new check 10: slow partition done within 3 s after the slow job).

- Default: 27/27 (07-r2-c2-final.log); slow job's worker held only offset 0; 9 of 12 later jobs finished first; partition done 1.0 s after the slow job.
- `RETRY_MAX_WAIT=30 POLL_TIMEOUT_MS=1000`: 25/27, checks 9 and 10 fail (slow partition 98 s).
- `DELIVERY_MODE=group`: 11/27, checks 4 to 16 and 20 to 22 fail.
- `SEED_LINGER_MS=500`: 25/27, checks 9 and 12 fail.
- `SHARE_RECORD_LOCK_MS=15000 GATEWAY_SLOW_MS=20000`: 27/27; the 20 s job was delivered once (lock renewed).
- `SHARE_ACQUIRE_MODE=record_limit` (26-check version): 26/26.
- mvn 10 tests, BUILD SUCCESS, after the last code change (07-r2-mvn-final.log).
- Orchestrator, after round 3: `SHARE_ACQUIRE_MODE=record_limit` on the 27-check verify.sh: 27/27.

## r04 chapter 10 window (2026-11-11)

- Run 1 (10-w1): 36/39. Check 4 read the offset before Connect stored it; 19 and 22 failed from the empty value. Fix: check 4 polls (30 s) and a precondition stops the run if no lsn is stored.
- Run 2 (10-w2): 37/39. Checks 20 and 21: the backward alter repeated the last change before the stop (SKU-1 qty 7777, same lsn) once. Run 3 (KEEP=1, rows dumped) showed the same; run 4 with a precondition that the offset equals confirmed_flush_lsn before the stop still repeated it. Behaviour asserted: 2 new changes plus one repeat, nothing older.
- Run 5 (10-w5): 39/39 from clean; no edp-* left.
- Experiments (10-exp.log): E1 OFFSET_FLUSH_INTERVAL_MS=60000: check 4 fails, run stops at its precondition. E2 docker kill edp-connect between flushes: 21 changes written, 21 delivered again after restart. E3 no_data config, 3 updates while stopped, DELETE offsets, resume: 0 r rows, 4 u rows (the 3 plus a repeat of the last change before the stop). PATCH while running: check 7. Incremental snapshot instead of reset: check 38.

## r04 chapter 09 window (2026-11-11)

- w1: 25/34. Root causes: kafka-console-consumer prints a KIP-848 notice on stdout before records, so the jq parsing of dumps failed (checks 5, 7, 10, 15, 18, 25 to 27); the interval-only connector shared topic.prefix=edp, a JMX name collision delayed the edp_inventory slot about 110 s (check 2). Fixes: dumps keep record lines only; interval-only prefix edp-interval-only; helper-table topics routed to conforming names; check 35 asserts every edp. topic name.
- r1 34/34, r2 35/35, r3 35/35 from clean (two consecutive N/N). No edp-* left.
- Measured (r3): burst about 55 MB; query slot 54.6 MB before release, 192 B after (r2: 0 B); interval-only slot 55.0 MB after the wait.
- Experiments: remove tombstone filter 34/35 (13); delete REPLICA IDENTITY FULL 33/35 (14, 15); remove heartbeat.action.query 34/35 (22), 55.1 MB held; negative quantity: document unchanged, "quantityOnHand must not be negative" logged, next update processed; remove the offset-file rm 34/35 (34). Details: scratchpad 09-window.md (not committed).

## r04 chapter 08 window (2026-11-11)

- First run already 26/26; script hardened anyway (record-line filter for the console consumer, no grep -q under pipefail) and the fresh-start lines added. f1 and f2: 26/26 from clean, consecutive. No edp-* left.
- Experiments: remove-after-insert=false 25/26 (8); no route.by.field 20/26 (6, 7, 9, 11, 17, 24); Kafka stopped: outbox 201 in about 10 ms, delivered after restart, commit-first 500 after 60 s with the row and no event, publish-first 500 after 60 s with no row; docker kill Connect during a 200-order burst after a stored offset: 337 records for 201 orders, 136 duplicate id headers, none lost.
- Kill before the first stored offset: one run lost 3 of 200 (B-080, B-148, B-153). Orchestrator re-ran it twice (08-nowarm.log): 201 of 201 both times; Connect log shows the restarted connector re-snapshots the empty outbox table and streams from the snapshot LSN, above the slot's latestFlushedLsn. Prose states the mechanism and that loss is timing-dependent.

## r04 final serial run (2026-11-11, fresh containers per example, Camel 4.22.2)

| Example | mvn verify tests | verify.sh |
|---|---|---|
| domain-model 2.1.0 | 80 | n/a |
| 00 | 4 | 17/17 |
| 01 | 11 | 19/19 |
| 02 | 7 + 4 | 21/21 |
| 03 | 8 + 7 | 30/30 |
| 04 | 4 + 46 | 28/28 |
| 05 | 6 | 24/24 |
| 06 | 4 | 36/36 |
| 07 | 10 | 27/27 |
| 08 | 7 | 26/26 |
| 09 | 8 | 35/35 |
| 10 | 2 | 39/39 |

Each verify.sh started with `down -v --remove-orphans` then `up --build --force-recreate --renew-anon-volumes`; no edp-* containers or volumes left. Static gates: validate ok, voice 0 ban-tier, idiomatic 0 ban-tier, host_ids 0, site builds. Words: 08 1,822; 09 2,391; 10 2,026.

## r04 repair round 1 (2026-11-11)

Opus round 1: REPAIR (M1 to M6, L1 to L14; runs and gates were already clean). Fixed by a repair executor: stale backward-alter wording (figure 10.2, example page, README); figure 9.3 tombstone note; ch 10 signal topic routed to edp.inventory.signals plus check 40; ch 08 forward reference; schema validation in every profile; unbacked no_data sentence; heartbeat topics via topic.heartbeat.name; references, bibliography, idioms (env mapping, Camel variable); ch 08 control check 27.
Live after the fixes, each from clean: 08 27/27, 09 35/35, 10 40/40 (first ch 10 run 39/40: check 36 used grep -q on docker logs under pipefail; fixed to capture first). mvn: 08 7, 09 8, 10 2 tests. Gates clean.
- Ch 08 no route.by.field re-measured with check 27 (08-noroute-r2.log): 21/27, checks 6, 7, 9, 11, 17, 24 fail, as the chapter states.

## r04 validation round 2 (2026-11-11)

Opus round 2: SHIP after one typo (ch 10 "`,:`"); all round-1 findings verified resolved; 9 regenerated figures byte-identical to specs; 7-gram overlap with EIP ch 15, appendix R, CNDP ch 4 is 0. Fixed anyway (fix-now): typo; checks 35 (09) and 40 (10) now fail when the topic listing fails and require the expected topics (09: item-changes, connector-heartbeats, interval-only-heartbeats, signals; 10: item-changes, signals); stale claims rows 09-06, 08-08, r04-r1-10; figure 9.3 update box; CrashSwitch Javadoc; ch 10 names check 39.
Live from clean after the fixes: 10 40/40, 09 35/35; 08 mvn verify green. No edp-* left.

## r05 chapter 11 window (2026-11-11)

- Three runs from clean: 28/28, 28/28, 28/28 (w11-verify1..3); mvn verify 7 tests green. No example or verify.sh change.
- Experiments: INBOX_ENABLED=false 23/28 (5, 6, 7, 12, 13 fail; preconditions 3, 9, 10, 21 pass). CAMEL_TRANSACTED=false 26/28 (23, 25). session.timeout.ms removed: 28/28, crash exit to lag 0 49.2 s vs 19.0 and 19.1 s; application.properties restored.
- TBDs filled; three untested claims cut (claims 11-l). Details: scratchpad w11-window.md (not committed).

## r05 chapter 12 window (2026-11-11)

- Five runs from clean, 20/20 each (w12-run1..5). Check 10: 14 s from the restart, 28 to 33 s from crash detection; k=7 every run. verify.sh gained NOTE echo lines only (timing, Camel per-order counts); no check changed.
- Experiments: PAYMENT_MODE=camel-transacted 13/20 (4, 5, 8, 9, 10, 12, 13 fail). TX_TIMEOUT_MS=10000, service left down: Ongoing at +11 s, CompleteAbort at +16 s, read_committed 0, read_uncommitted 7. Default isolation after restart: O-5101 to O-5107 twice. Camel phase: O-6001 to O-6007 two committed each, O-6008 to O-6010 one.
- "Within about half a minute" replaced by the measured 16 s. Details: scratchpad w12-window.md (not committed).

## r05 chapter 13 window (2026-11-11)

- Three runs from clean, 28/28 each (w13-v1..3); mvn verify 7 tests. No example or verify.sh change.
- Experiments: fail 15/28 (13 fail, offset 1 lag 5, readiness 503); RETRY_MAX=0 19/28; RETRY_MAX=2 23/28 (3 attempts); Camel DLC off 22/28 (offsets committed, lag 0); plus breakOnFirstError 21/28 (O-7001 applied 100 times, lag 6); HOLD=20 MAX_POLL_INTERVAL_MS=10000 22/28 (SmallRye no rebalance, Camel batch twice); lock hint removed 25/28 (one attempt, ~10 s wait), source restored.
- Prose trimmed by 108 words to stay under 2,500. Details: scratchpad w13-window.md (not committed).

## r05 final serial run and experiment re-runs (2026-11-12, fresh containers per run)

- domain-model install green. mvn verify and verify.sh, 00 to 13: 4/17, 11/19, 11/21, 15/30, 50/28, 6/24, 4/36, 10/27, 7/27, 8/35, 2/40, 7/28, 3/20, 7/28 (tests/checks); every verify N/N; no edp-* left between runs.
- Experiment re-runs (orchestrator, scratchpad f5-x*.log): 11 inbox off 23/28 (5, 6, 7, 12, 13); 11 Camel untransacted 26/28 (23, 25); 11 no session.timeout.ms 28/28, crash exit to lag 0 49.3 s vs 19.2 s in the baseline run; 12 camel-transacted 13/20 (4, 5, 8, 9, 10, 12, 13); 13 fail 15/28; RETRY_MAX=0 19/28; RETRY_MAX=2 23/28; Camel DLC off 22/28; plus breakOnFirstError 21/28; HOLD=20 poll 10 s 22/28; lock hint removed 25/28 (4, 19, 20; the window had 4, 5, 19). Source edits restored by the script (git checkout).

## r05 repair round 1 (2026-11-12, fresh containers per run)

- Changes: a Prometheus check in verify.sh of 11, 12 and 13 (inventory_inbox_duplicates_total, kafka_producer_txn_commit_time_ns_total, kafka_consumer_fetch_manager_records_consumed_total, each with service_name); 13 checks the SmallRye dead letter of O-7003 for bytes, source topic and offset; 12 check 11 asserts read_committed 10 and read_uncommitted 10 + k; 11 precondition 9 fails on an empty group describe and check 20 asserts four records, three keys, empty inbox; the Camel route of 12 calls a mapping bean.
- mvn verify: 11 7 tests, 12 3 tests, 13 7 tests, green. verify.sh N/N twice: 11 29/29 x2, 12 21/21 x2 (r1-t12, r1-v2-12; the earlier r1-v1-12 ran with the first form of the route change and failed precondition 15 and check 14, no exit 137 at O-6008 within 60 s, because the mapping bean was removed as unused; the route now injects it), 13 31/31 x2; no edp-* left between runs.
- Experiments (scratchpad r1-x*.log): 11 inbox off 23/29 (5, 6, 7, 12, 13, 29); Camel untransacted 27/29 (23, 25); no session.timeout.ms 29/29, crash exit to lag 0 48.6 s vs 19.1 s; 12 camel-transacted 12/21 (4, 5, 8, 9, 10, 11, 12, 13, 21); 13 fail 16/31 (4, 6, 7, 9 to 14, 16, 24 to 28); RETRY_MAX=0 22/31 (4 to 6, 14, 15, 19 to 21, 26); RETRY_MAX=2 26/31 (6, 14, 15, 21, 26); Camel DLC off 25/31 (19 to 21, 24 to 26); plus breakOnFirstError 24/31 (19, 21 to 26); HOLD=20 poll 10 s 25/31 (21 to 26); lock hint removed 29/31 (4, 19).
- Manual experiments of chapter 12: TX_TIMEOUT_MS=10000 left down, Ongoing then CompleteAbort at +16 s in both runs (r1-x12-txtimeout.log); default isolation after restart returns O-5101 to O-5107 twice (r1-x12-defaultiso.log).

## r05 validation round 2 (2026-11-12)

Opus round 2: all 15 round-1 findings resolved; numbers match the r1 logs; figure 13.1 regenerates byte-identical; mvn 12 green (3 tests); word counts 2,222 / 2,181 / 2,491. Low fixes applied: claims 13-k (dead letter content, checks 9 to 16, 27, 28) and 13-b cleaned; 13-f and 13-g note samples n=1; ch12 listing shows `.bean(mapping, "toPayment")`, "same point in the batch", check 21 wording; ch13 table cell without self-reference; KIP-939 marked not cited in sources.md.
