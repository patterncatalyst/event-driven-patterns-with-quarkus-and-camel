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
