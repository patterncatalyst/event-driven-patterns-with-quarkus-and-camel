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

Chapter 00 and 01 runs follow in step 11.
