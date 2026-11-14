---
layout: plan
title: r10 plan
render_with_liquid: false
---

# r10 plan: chapters 25 to 27, observing event flows (Opus planner, 2026-11-14)

Status: **approved by the owner 2026-11-14** (decision 1: Debezium tracing with the OpenTelemetry Java agent in Connect; decision 9: trimmed standalone copy of 22 for 27; all other recommendations; merge when validation and CI pass). Branch `release/r10`, cut from main after r09.0. No version re-survey: every existing pin is frozen (CLAUDE.md, 2026-11-14). The only new pin is the OpenTelemetry Java agent (decision 2).

Planner note: read-only. Sources: the repo (CLAUDE.md, `_plans/*`, examples 08, 13, 22 to 24, `_infra`, `scripts/validate.py`); the local `debezium-quarkus-outbox` 3.7.1.Final jars (`javap`); `docker image inspect`/`history` of `quay.io/debezium/connect:3.7.1.Final` and `grafana/otel-lgtm:0.41.0`; fetched: the Debezium tracing page, the OpenTelemetry messaging-spans semconv, `docker-otel-lgtm` at 0.41.0 (rev 6fa5344: README, Dockerfile, datasource, Prometheus, Tempo, collector configs, run scripts), the Tempo API page, the Quarkus OpenTelemetry logging guide, Maven Central metadata for `opentelemetry-javaagent`; the sibling `~/Dev/otel-observability-tutorial` ch 13 (read, not copied).

## Findings that bind the design

- **The outbox extension already traces.** `debezium-quarkus-outbox` 3.7.1 with `quarkus-opentelemetry` starts an `outbox-write` span (tracer `debezium`) as a child of the active span and writes the propagator's injected context into `tracingspancontext` as `java.util.Properties` text, not a bare `traceparent`. `tracingEnabled` is build time. Every outbox table in 08, 14, 18, 22, 23 has the column.
- **The Connect image carries the OpenTelemetry API, not the SDK.** It ships OpenTelemetry API 1.23.1, `opentelemetry-kafka-clients-2.6` and Jolokia, off the classpath unless `ENABLE_OTEL=true`. The Debezium docs say the SDK comes from the OpenTelemetry Java agent, which is not in the image. With the agent, the EventRouter runs the ActivateTracingSpan steps (spans `db-log-write`, starting at the WAL write time, and `debezium-read`) and injects the context into the Kafka headers.
- **otel-lgtm 0.41.0 bundles** Grafana 13.3.0, Prometheus 3.16.0, Tempo 3.1.1, Loki 3.7.9, otelcol-contrib 0.165.0. The collector runs `--config=file:./otelcol-config.yaml` plus `OTELCOL_EXTRA_ARGS` (appended unchanged); pipelines `traces`, `metrics`, `logs`; exporters `otlp_http/metrics`, `otlp_http/traces`, `otlp_http/logs`. Prometheus has no `rule_files`, runs `--enable-feature=exemplar-storage`. Datasource uids `prometheus`, `tempo`, `loki`; Tempo has `tracesToLogsV2` into Loki on `trace_id`; Loki has `derivedFields` on `trace_id` into Tempo. Tempo's metrics generator runs `service-graphs` and `span-metrics`. Dashboard provisioning by file mount is documented (`/otel-lgtm/grafana/conf/provisioning/dashboards/custom`); alert provisioning is not (spike S3).
- **Messaging semconv (Development):** the process span "SHOULD link to the message's creation context"; "exclusively for single messages scenarios" it "MAY use the message's creation context as its parent".
- **`remove-after-insert=true` leaves the outbox table empty by design**: a row-count gauge reads 0; the backlog lives in the replication slot.
- **Chapter 16** measured lag from the Kafka record timestamp. Behind an outbox, Debezium sets that timestamp when it reads (S5 confirms), so the record timestamp hides the CDC hop. Chapter 26 refines 16 without contradicting it.
- **The sibling otel-observability-tutorial ch 13** teaches a propagation on/off toggle and fan-out of one trace to two consumer groups. r10 does not repeat either; it links instead.
- **Domain:** `Topics` 2.4.0 has every topic needed (`ORDERS_PLACED`, `ORDERS_PLACED_DLQ`, saga topics). No bibliography book has a chapter titled for observability; no book chapter is cited for it.
- **r08/r09 lessons carry over:** event times relative to now; per-run state; 15-run timing witnesses or the rate stated; every check proven to bite; claims verified or cut; at most two Opus repair rounds; no `grep -q` under pipefail; recreates wait for liveness.

## Approach

Same gate shape as r09, without a re-survey: (1) G0 sources, spikes and infra in parallel; (2) three authoring executors on disjoint files; (3) serial runtime windows 25, 27, 26; (4) orchestrator re-runs every experiment; (5) final serial run of 25 to 27 only; (6) Opus validation, then ship.

- **25** carries trace context through the outbox the way the tools intend: the outbox extension writes `tracingspancontext`, the Connect container gets the OpenTelemetry Java agent so the EventRouter emits Debezium's spans and a `traceparent` header, and the SmallRye consumer continues the trace as a child. One Tempo trace runs from `POST /orders` to the inventory consumer. A demo switch shows the span-link shape beside it.
- **26** measures four signals where each can be seen: outbox backlog as replication-slot lag (Micrometer gauge); consumer lag and DLQ growth from the broker (kafkametrics receiver in the bundled collector); event age from the event time stamped at commit, against record age. A provisioned Grafana dashboard and one Grafana-managed alert, both asserted through HTTP APIs.
- **27** is a trimmed standalone copy of the chapter 22 saga with Connect tracing. The order id is set on spans and in log context; `verify.sh` follows one order through Tempo and Loki both ways, including the sweeper's separate trace.

Rejected:
- Re-survey or bumps: pins frozen; no valid reason.
- Own Connect image with the agent baked in: book-built images start from UBI 10 and the published image comes first; a run-scoped volume filled by an init container keeps the published image.
- `traceparent` as an extra outbox column placed as a header: reimplements what the extension and EventRouter provide and hides the Debezium hop. Kept as the S1 fallback.
- Notification fan-out in 25: the sibling tutorial teaches it.
- Debezium JMX for the backlog: needs a JMX bridge; its streaming metrics describe the last event read (S5 records it during a pause).
- kafka-exporter or kminion for lag: a new component; the pinned LGTM collector has `kafkametrics`.
- A Prometheus rule file: means replacing the image's whole `prometheus.yaml`; a Grafana-managed rule is one provisioning file.
- Reusing `examples/22-saga` for 27: examples stand alone, and 22 with its gate, restart and bench checks is too large to read alongside the correlation code.
- Exemplars and OTel baggage: extra parts no check needs; named once in prose at most.

## Owner decisions needed

1. **Trace context across the outbox.** (a) Debezium's own tracing: the extension column, plus `ENABLE_OTEL=true` and the OpenTelemetry Java agent in Connect; (b) an app-written `traceparent` additional field placed as a Kafka header (no Connect change, Connect hop invisible); (c) both, compared. **Recommendation: (a)**, (b) as fallback if S1 fails; the chapter states (b) leaves a gap with no spans.
2. **New component `io.opentelemetry.javaagent:opentelemetry-javaagent:2.33.1`** (Apache-2.0; newest final on Central, 2026-10-26), pinned once with a versions.md row. Delivered by an init service on the pinned `ubi10/openjdk-25:1.24-20` running `mvn -B -ntp dependency:copy` into a run-scoped named volume (removed by `down -v`), mounted read-only by Connect. **Recommendation: adopt.** Alternative: fallback (b), no new component.
3. **Connect tracing override location.** **Recommendation:** `examples/_infra/compose-connect-tracing.yaml`, merged via `include: - path: [../_infra/compose-connect.yaml, ../_infra/compose-connect-tracing.yaml]` (orchestrator, 1D), because 25 and 27 both use it. Alternative: a copy per example.
4. **Parent-child or span links.** **Recommendation:** parent-child for the single-record path (what the Quarkus and Camel instrumentations do; S2 confirms), so Tempo shows one trace. Links taught as the rule for batches and later work: 25 demo switch `CONSUMER_TRACE=link`; 27 the sweeper's cancellation trace links to the saga's starting trace (decision 9).
5. **Camel and SmallRye forms.** **Recommendation:** 25 and 26 SmallRye only (25's comparison is parent vs link); 27 keeps chapter 22's split (SmallRye order and inventory, Camel payment), so one trace and one log query cross both libraries. Alternative: a Camel inventory route in 25 on its own group.
6. **Outbox backlog source.** **Recommendation:** a Micrometer gauge in order-service, `pg_wal_lsn_diff(pg_current_wal_lsn(), confirmed_flush_lsn)` for the connector's slot, in bytes; Connect worker `OFFSET_FLUSH_INTERVAL_MS=1000` and the connector's `heartbeat.interval.ms` (S5) keep it honest. The empty outbox table shown as the wrong source (`BACKLOG_SOURCE=table`, demo). Alternatives: Debezium JMX; the collector's `sqlquery` receiver.
7. **Event age.** **Recommendation:** two Micrometer timers in the consumer against the outbox `timestamp` column: `order.flow.event.age` (now minus the event time, carried as header `occurredAt` via `table.fields.additional.placement`, S5; fallback payload field) and `order.flow.record.age` (now minus the Kafka record timestamp). With the connector paused, event age shows the stall and record age does not.
8. **Consumer lag and DLQ rate.** **Recommendation:** kafkametrics receiver in the bundled collector via an example-local extra config through `OTELCOL_EXTRA_ARGS` (new pipeline `metrics/kafka`, no base list overwritten); broker-side group lag, DLQ growth from topic end offsets; the client metric `kafka_consumer_fetch_manager_records_lag_max` shown going blind when the consumer stops.
9. **Chapter 27 example.** **Recommendation:** a smaller standalone copy of the 22 saga, `examples/27-correlated-logs-and-traces`: three services, three outboxes, one connector, the sweeper and the payment refusal of a cancelled order; without the cancellation gate, restart checks, burst bench, race test or `PAYMENT_CANCEL_GUARD`. README says what was trimmed and links to 22. Added: `order.id` span attribute, `orderId` log context key, a `traceparent` column on `order_saga` so the sweeper's trace links back (`SWEEPER_LINK=off` is the witness).
10. **Dashboard and alert provisioning.** **Recommendation:** files in example 26 (`grafana/dashboards/order-flow.json`, `grafana/dashboards.yaml`, `grafana/alerting/order-flow.yaml`) mounted read-only by an example-local `compose-lgtm-provisioning.yaml` override; no `_infra` change. One alert, "order outbox backlog", on the backlog gauge (fires while stuck; event age is only seen on arrival). `ALERT_ON=age` is the Try bullet that fires late.
11. **Domain-model.** **Recommendation:** no change, no bump: no pom sweep, no earlier-chapter mvn runs.
12. **Ports:** 8080 order, 8082 inventory, 8084 payment (27 only). No new host port.
13. **Slugs and packages:** `25-tracing-across-async-hops` (`...tracing`), `26-metrics-for-event-flows` (`...metrics`), `27-correlated-logs-and-traces` (`...correlation`); order ids `O-25xx`, `O-26xx`, `O-27xx`.
14. **More than one Prometheus check in 26.** **Recommendation:** an exception for the metrics chapter, recorded in the decisions log; 25 and 27 keep the single-check pattern.
15. **Runtime window order:** 25, then 27 (reuses 25's Connect tracing), then 26 (alert 15-run loop last).

## Per-chapter design

Shared: extensions `quarkus-opentelemetry`, `quarkus-micrometer-opentelemetry`, `quarkus-smallrye-health`, `quarkus-rest-jackson`, `quarkus-messaging-kafka`, Panache, Flyway, `jdbc-postgresql`, `debezium-quarkus-outbox` 3.7.1.Final where needed; in 27 payment uses `camel-quarkus-kafka`, `-jackson`, `-bean`, `-opentelemetry2`. BOM order camel-bom 4.22.2, quarkus-bom, quarkus-camel-bom 3.40.1.2. Topics from `topics-init`, 1 partition; consumers `session.timeout.ms=10000`, `heartbeat.interval.ms=3000`; demo switches as env vars labelled demo code; fresh start, liveness waits, no fixed sleeps, event times relative to now. Tempo: `GET :3200/api/search?q=<TraceQL>` with structural `>>`, `GET :3200/api/v2/traces/<id>` parsed by a `python3 -I` helper (traceId, spanId, parentSpanId, links, service.name), endpoints confirmed against Tempo 3.1.1 in S1. Loki: `GET :3100/loki/api/v1/query_range`, LogQL on `service_name` and structured metadata `trace_id` (S7). Prometheus `GET :19090/api/v1/query`; Grafana `/api/dashboards/uid/...`, `/api/prometheus/grafana/api/v1/rules`, `/api/datasources/uid/...` (S3). No UI clicks.

### 25 Tracing across async hops (`examples/25-tracing-across-async-hops`, `_docs/25-tracing-across-async-hops.md`)

Headings: `## Pattern: Trace context in the message`, `## Through the outbox and Debezium`, `## Parent or link`, `## What the trace shows and hides`, Build, run, observe, When not to use it, References.

Problem: an HTTP call carries its trace in a header; an order crossing a commit, a WAL reader and a topic has no request to ride on, so the consumer starts a new trace and the path is lost.

Services: order-service (8080) `POST /orders` writes the order and OrderPlaced through the outbox (08 shape) and sets `order.id` on the server span. inventory-service (8082) `@Incoming` reservation (Panache `InventoryItem`, `SKU-25`) sets `order.id`; `CONSUMER_TRACE=link` (demo) sets `tracing-enabled=false` on the channel and starts a root span with `setNoParent().addLink(<extracted context>)`. Connect via the `_infra` tracing override: `ENABLE_OTEL=true`, `KAFKA_OPTS=-javaagent:/otel/opentelemetry-javaagent.jar`, `OTEL_SERVICE_NAME=connect`, `OTEL_EXPORTER_OTLP_ENDPOINT=http://lgtm:4318`, `OTEL_PROPAGATORS=tracecontext`, `OTEL_METRICS_EXPORTER=none`, `OTEL_LOGS_EXPORTER=none`, Kafka-instrumentation settings per S1; `CONNECT_TRACING=off` (demo) drops agent and `ENABLE_OTEL`. Connector JSON: 08's plus S1's tracing keys.

mvn (>= 8, in-memory exporter, Dev Services): outbox row `tracingspancontext` holds the request trace's `traceparent` (`%test` `remove-after-insert=false`); `outbox-write` child of the REST span; consumer span child of a header context (KafkaCompanion); link mode gives a new trace id plus a link with the header's span id; no header gives a root; `order.id` on both sides.

verify.sh (~20): preconditions (ready, connector RUNNING, agent banner in Connect's log, 1 partition, stock 10); O-2501 reserved (stock 9); the record on `edp.orders.placed` has a `traceparent` header; TraceQL `{ span.order.id = "O-2501" }` returns exactly one trace; that trace has order-service, connect and inventory-service spans; structural `{ resource.service.name="order-service" && name="POST /orders" } >> { resource.service.name="inventory-service" }` matches; parsed ancestry inventory span <- debezium-read (or producer) <- db-log-write <- outbox-write <- `POST /orders`; header trace id equals Tempo trace id; NOTE outbox-write to consumer gap (CDC hop); Tempo service-graph series connect -> inventory-service in Prometheus (the one Prometheus check; S1 confirms name); bench (before link phase) burst of 100, all traces complete, NOTE CDC-hop median and range over 3 runs; link phase: inventory-service recreated with `CONSUMER_TRACE=link`, O-2502 reserved, the order trace has no inventory span, exactly one inventory trace with `order.id=O-2502` whose root links to the order trace and a connect span; Loki has inventory-service logs.

Experiments: `CONNECT_TRACING=off` (header absent, inventory span a new root; expected failing set frozen in the window: the header, trace-shape and service-graph checks) is the main negative witness; `CONSUMER_TRACE=link` for the whole run (trace-shape checks fail); bite (temporary edit) removing `order.id` fails the TraceQL check.

Figures: `25-context-carriers`, `25-trace-waterfall` (CDC gap marked), `25-parent-or-link`. Decision table: parent vs link (one record, batch, later work, fan-in, what Tempo shows, what the semconv says). Callout names `OrderResource`, `tracingspancontext`, `CONSUMER_TRACE`, `edp.orders.placed`; rule: the context travels in the record, every relay copies it, one record continues as a child, many or later records are links.

Sources (1A): Debezium tracing page; OpenTelemetry messaging spans; W3C Trace Context; OpenTelemetry Java agent configuration; Quarkus OpenTelemetry guide (Kafka tracing, `tracing-enabled`); Tempo API and TraceQL structural operators; Strimzi tracing post (confirmed row); backward links 08, 09; the sibling tutorial ch 13.

### 26 Metrics for event flows (`examples/26-metrics-for-event-flows`, `_docs/26-metrics-for-event-flows.md`)

Headings: `## Pattern: Measure the flow, not the service`, `## Backlog before the topic`, `## Lag at the broker`, `## Event age, not record age`, `## Dead letters as a rate`, `## A dashboard and one alert`, Build, run, observe, When not to use it, References.

Problem: every service looks healthy while orders wait in a paused relay or a stopped group; counters inside each service cannot see work between services.

Services: order-service with the outbox and the `outbox.backlog` gauge (bytes, slot from config; `BACKLOG_SOURCE=table` demo counts rows). inventory-service with SmallRye `@Incoming`, `failure-strategy=dead-letter-queue` to `edp.orders.placed-dlq` (chapter 13), timers `order.flow.event.age` and `order.flow.record.age`. lgtm override mounting `grafana/` and `otelcol/kafka-metrics.yaml`, `OTELCOL_EXTRA_ARGS=--config=file:/otel-lgtm/extra/kafka-metrics.yaml` (kafkametrics on `kafka:9094`, 5 s, pipeline `metrics/kafka`). Connect: plain `_infra` file plus worker `OFFSET_FLUSH_INTERVAL_MS=1000` via the example's override (S5).

Dashboard `order-flow.json` (uid `edp-order-flow`): backlog bytes; group lag by group; event age and record age (max, p95); DLQ records per minute; DLQ ratio to `edp.orders.placed`. Alert (Grafana-managed, folder `edp`, uid `edp-outbox-backlog`): backlog above the S5 threshold for 20 s, evaluation 10 s.

mvn (>= 8): gauge reads slot lag on Dev Services PostgreSQL with `wal_level=logical` (S5), 0 after the slot confirms; table source reads 0 with `remove-after-insert`; timers record from the header and from the record timestamp; a malformed record reaches the DLQ with no event-age sample; provisioning files parse, every panel names an exported metric, the alert references the dashboard's backlog query.

verify.sh (~24): preconditions (services and connector up, dashboard loaded, alert rule loaded and Normal, a kafkametrics series exists, backlog under threshold at rest); 40 orders O-2601..O-2640 reserved, broker lag 0 for `inventory-placed`; stop inventory-service, 20 orders; broker lag reaches 20 within 30 s; inventory-service's client lag metric has no sample in the last 15 s; start it, lag back to 0; pause the connector, 20 orders; outbox table holds 0 rows; backlog gauge above threshold; alert Firing within 60 s of the pause (15-run witness); no new record on the topic while paused; resume after 30 s: backlog under threshold, alert Normal within 90 s (15 runs); event age max of the drained 20 >= 25 s; record age max < 2 s (S5 confirms the record timestamp source); 3 malformed records: DLQ grows by exactly 3; `increase(kafka_partition_current_offset{topic="edp.orders.placed-dlq"}[5m]) >= 3`; DLQ ratio query above 0; every panel query returns a series (exprs extracted from the provisioned JSON); bench burst of 500, NOTE peak lag, time to lag 0, event-age p95, 3 runs; Tempo has an order-service trace; Loki has logs.

Experiments: `BACKLOG_SOURCE=table` (backlog never rises, alert never fires; stated set frozen in the window); `ALERT_ON=age` (no firing during the pause, firing only after resume); bite: event age from the record timestamp fails the event-age check.

Figures: `26-where-each-signal-lives`, `26-stall-timeline`. Decision table: signal, where measured, what it misses, alert or graph only. Callout names `outbox.backlog`, `order.flow.event.age`, `edp.orders.placed-dlq`, `edp-order-flow`; rule: backlog at the relay, lag at the broker, age from the event's own time, alert on what grows while the flow is stuck.

Sources (1A): kafkametricsreceiver README at v0.165.0; PostgreSQL `pg_replication_slots` and `pg_wal_lsn_diff`; Debezium PostgreSQL connector (heartbeat, slot growth) and outbox EventRouter (additional placement, `table.field.event.timestamp`); Quarkus Micrometer guide; SmallRye Kafka failure strategy; Grafana alerting file provisioning and HTTP API; docker-otel-lgtm README; Kafka 4.3 consumer group docs (confirmed); Bellemare ch 18 by title for dead letters (confirmed); backward links 08, 09, 13, 16.

### 27 Correlated logs and traces (`examples/27-correlated-logs-and-traces`, `_docs/27-correlated-logs-and-traces.md`)

Headings: `## Pattern: Correlation identifier across signals`, `## A trace id in every log line`, `## One order, more than one trace`, `## From a log to its trace, and back`, Build, run, observe, When not to use it, References.

Problem: a saga for one order crosses three services and, on timeout, more than one trace. An operator holds an order id, not a trace id. Logs without the trace id cannot reach the trace; traces without the order id cannot be found.

Services (trimmed copy of 22, package `...correlation`): order-service (8080), inventory-service (8082), payment-service (8084, Camel route); three outboxes, one connector, Connect via the `_infra` tracing override. Additions: `Span.current().setAttribute("order.id", ...)` at every entry point; `MDC.put("orderId", ...)` around every handler (exported as an OTLP log attribute, S7; fallback order id in message text plus a LogQL line filter); console format shows `traceId` and `spanId`; `order_saga.traceparent` recorded at placement and the sweeper's span links to it. Demo switches `SWEEPER_LINK=off`, `ORDER_ID_IN_LOGS=off`, `SAGA_PAYMENT_TIMEOUT` (default 15s), `PAYMENT_DECLINE_ABOVE`.

mvn (>= 12): order id on spans and in MDC per handler; sweeper span links to the stored `traceparent`; declined path compensates once; sweeper cancels an expired saga; payment refuses a cancelled order; the Camel route log record carries the active trace id (S7).

verify.sh (~24): preconditions (ready, connector RUNNING, stock `SKU-27` = 10); O-2701 PAID; `{ span.order.id = "O-2701" }` returns exactly one trace with order, connect, inventory and payment spans and order-service's PaymentProcessed consumer; Loki lines with orderId O-2701 from all three services, each with that trace id; Loki to Tempo (a payment-service line's trace id resolves to a trace containing `POST /orders`); Tempo to Loki (`| trace_id="<id>"` returns lines from three services); O-2703 declined, CANCELLED, stock released, release consumer span in the single trace; payment-service stopped, O-2704 cancelled by the sweeper (15 to 18 s, 15-run witness); `{ span.order.id = "O-2704" }` returns exactly two traces; the sweeper trace's root links to the first; Loki by orderId O-2704 returns lines with both trace ids; payment-service restarted: no payment for O-2704; Grafana's Loki datasource has the `trace_id` derived field and Tempo has `tracesToLogsV2` (API); Prometheus `saga_outcomes_total{outcome="compensated",service_name="order-service"} >= 2`.

Experiments: `SWEEPER_LINK=off` (only the link check fails; order-id correlation still holds, which is the lesson); `ORDER_ID_IN_LOGS=off` (the Loki order-id checks fail); bite: order-id attribute removed in payment-service fails the trace-content check.

Figures: `27-one-order-many-signals`, `27-timeout-two-traces`, `27-log-trace-roundtrip`. Callout names `order.id`, `orderId`, `order_saga.traceparent`, `SagaSweeper`; rule: trace id in every log line, business id on every span and log, link any trace a timer starts to the trace it continues.

Sources (1A): Hohpe and Woolf, Correlation Identifier (fetch); Loki OTLP ingestion (structured metadata) and LogQL; Quarkus OpenTelemetry guide (MDC keys); Quarkus scheduler tracing; Camel OpenTelemetry 2 page; Grafana Tempo and Loki datasource docs; Lawrence ch 7 and Bellemare ch 10 by title (confirmed rows); backward link 22.

## Build, run, observe rule (unchanged from r09)

One command block, one or two sentences ending with the pass count, at most three one-sentence Try bullets, 250 words soft cap, no check numbers, test lists or run statistics. 25 and 27 add one sentence: the Connect container downloads the agent on each fresh start.

## Steps

| Step | Work | Files | Owner | Depends | Parallel | Stack | Status |
|---|---|---|---|---|---|---|---|
| 0 | Preflight: branch, iteration-plan r10 in progress, save plan | `_plans/r10-plan.md`, `_plans/iteration-plan.md` | orchestrator | owner OK | none | no | done |
| 1A | Sources and bibliography rows; versions.md row for the agent 2.33.1 (new pin only); claims rows drafted | `_plans/sources.md`, `bibliography.md`, `_plans/versions.md` | exec 1A | 0 | 1B | no | pending |
| 1B | Spikes S1 to S7 (scratchpad) | scratchpad | exec 1B | 0 | 1A | lock, serial | pending |
| 1C | Domain: none (decision 11); domain-model.md rows 25 to 27 reworded to the final designs | `_plans/domain-model.md` | orchestrator | 1B | yes | no | pending |
| 1D | `examples/_infra/compose-connect-tracing.yaml` (agent init service, volume, env), `_infra/README.md` row | `examples/_infra/*` | orchestrator | S1 | after S1 | lock | pending |
| G0 | Gate: spikes pass or fallbacks chosen; owner confirms decisions 1, 2, 6 with spike data; "G0 decisions" appended | this plan | orchestrator | 1A to 1D | none | no | pending |
| 2A | Ch 25: modules, tests, connector, compose, Containerfiles, README, example page, figure specs and outputs, draft without numbers | `examples/25-*/**`, `_docs/25-*.md`, `_example_pages/25-*.md`, `diagrams/specs/25-*.py`, `diagrams/25-*.*` | exec 2A | G0 | with 2B, 2C | mvn under lock | pending |
| 2B | Ch 26: same set plus `grafana/`, `otelcol/` | `examples/26-*/**`, `_docs/26-*`, `_example_pages/26-*`, `diagrams/*26-*` | exec 2B | G0 | with 2A, 2C | mvn under lock | pending |
| 2C | Ch 27: same set (trimmed copy of 22) | `examples/27-*/**`, `_docs/27-*`, `_example_pages/27-*`, `diagrams/*27-*` | exec 2C | G0 | with 2A, 2B | mvn under lock | pending |
| 2D | Consistency: backward links (08, 09, 13, 16, 22); 16's record-timestamp lag and 26 agree; chapter 00 setup names the agent pin; CLAUDE.md decisions draft | `_docs/00-*.md`, `_plans/claims.md` | orchestrator | G0 | yes | no | pending |
| 3 | Runtime windows, serial, exclusive, 150 min cap each, order 25, 27, 26: N/N twice from clean, 3 bench runs, every experiment once, 15-run loops (26 alert firing and recovery; 27 sweeper; 25 trace completeness), bite proofs, numbering frozen | as 2A to 2C | orchestrator | 2A to 2C | serial | exclusive | pending |
| 4 | Orchestrator re-run of every prose experiment, logged with failing check numbers | `_plans/verification-log.md` | orchestrator | 3 | serial | yes | pending |
| 5 | Final serial run of 25 to 27 only (00 to 24 not re-run: no domain-model change); fresh containers; no `edp-*` left; static gates; claims and log; per-chapter commits after `python3 scripts/host_ids.py check` | claims, log | orchestrator | 4 | serial | yes | pending |
| 6 | Opus validation (at most 2 repair rounds): claims audit, check-bite audit, timing witnesses, 7-gram originality against the sibling otel-observability-tutorial (ch 10, 11, 13), quarkus-observability, the DDD observability workshop and the Debezium tracing page; licence audit (agent, LGTM components); fresh clone; word counts; figures byte-identical | none | validator | 5 | none | yes | pending |
| 7 | Ship: PR, CI green, squash on owner OK, tag r10.0, notes-only release; PRD rows 25 to 27; roadmap; iteration-plan done; CLAUDE.md r10 decisions (agent pin, the 26 Prometheus exception, provisioning pattern) | as listed | orchestrator | 6 | none | no | pending |

Executor rules as in r09: no git writes; no edits to `claims.md`, `verification-log.md`, `CLAUDE.md`, `examples/_infra`, `validate.py`, `domain-model`; mvn only under `flock -w 7200 <scratchpad>/stack.lock`; step 2 runs no compose; logs through `host_ids.py filter`; paths written `<repo>` and `~/`.

## Spikes (1B, scratchpad, under the lock, fresh containers; about 260 minutes)

- **S1 Connect tracing (70 min).** The `include: path: [base, override]` merge on compose 5.7; the init service fills the volume with agent 2.33.1 and Connect starts with `-javaagent` via `KAFKA_OPTS` on its JDK; `ENABLE_OTEL=true` puts the bundled API on the classpath; `db-log-write` and `debezium-read` reach Tempo parented to `outbox-write`; a `traceparent` header lands on the record; Connect's internal-topic traffic does not flood Tempo (agent Kafka instrumentation on or off; noise spans per minute); which EventRouter tracing keys apply; Tempo 3.1.1 answers `/api/search` with structural TraceQL and `/api/v2/traces/<id>` with `links`; the service-graph metric name; Connect start time and memory. Pass: one trace from `POST /orders` to a consumer. Fallback: decision 1(b).
- **S2 Consumer span shape (30 min).** SmallRye Kafka in Quarkus 3.40.1.2 makes the process span a child of the header context (or links), with and without a `traceparent`; `tracing-enabled=false` per channel; the manual link span; camel-opentelemetry2 on a Kafka consumer continues the trace, and its span names. Fallback: if SmallRye already links, state it and invert the switch.
- **S3 Grafana provisioning (35 min).** Dashboards and alert rules load from mounted files in otel-lgtm 0.41.0 (alerting under `/otel-lgtm/grafana/conf/provisioning/alerting/` or `GF_PATHS_PROVISIONING`) with datasource uid `prometheus`; `/api/prometheus/grafana/api/v1/rules` shows Pending, Firing, Normal; minimum evaluation interval; time from threshold to Firing. Fallback: a Prometheus rule file via a mounted `prometheus.yaml` copy plus `rule_files` (`:19090/api/v1/alerts`).
- **S4 kafkametrics (30 min).** `OTELCOL_EXTRA_ARGS` merges a second config with pipeline `metrics/kafka` without dropping OTLP metrics; metric names and labels (`kafka_consumer_group_lag`, `kafka_partition_current_offset`); interval and staleness. Fallback: a lag gauge from the Admin client (22 `CancellationLag` shape) and an app DLQ counter.
- **S5 Backlog and age (45 min).** The `edp` role can read `pg_replication_slots` and call `pg_current_wal_lsn()`; slot lag noise at rest with and without `heartbeat.interval.ms` and `OFFSET_FLUSH_INTERVAL_MS=1000` (threshold above measured noise); growth while paused and drain after resume; the Debezium record timestamp source (outbox time or read time; `table.field.event.timestamp`); `timestamp:header:occurredAt` placement and format; JMX `MilliSecondsBehindSource` via Jolokia during a pause (record only); Dev Services PostgreSQL with `wal_level=logical`. Fallback: event time as a payload field.
- **S6 Metric names (15 min).** Micrometer timer and gauge names, units and histogram series through `quarkus-micrometer-opentelemetry` into Prometheus.
- **S7 Logs (35 min).** OTLP log records from Quarkus carry trace and span ids into Loki structured metadata (`trace_id`); MDC entries export as attributes and their Loki names; LogQL filter syntax; Camel route logs carry the active context; the Quarkus scheduler span setting for the sweeper; TraceQL `span.order.id`. Fallback: order id in message text with a line filter.

Spike timings are marked `TBD(spike: value)` and re-measured in the windows.

## Acceptance criteria

- **25:** verify N/N, N >= 15, twice from clean; mvn >= 8 tests; one Tempo trace for O-2501 contains order-service, connect and inventory-service spans with the inventory span descending from `outbox-write`; `CONNECT_TRACING=off` and `CONSUMER_TRACE=link` fail exactly their stated sets; the link check reads a real `links` entry from the trace JSON.
- **26:** verify N/N, N >= 18, twice from clean; mvn >= 8; the alert reaches Firing during the pause and Normal after the resume in 15 of 15 runs, or the README states the rate; every panel query returns data; `BACKLOG_SOURCE=table` and `ALERT_ON=age` fail exactly their stated sets; dashboard and rule provisioned from example files, no `_infra` change.
- **27:** verify N/N, N >= 15, twice from clean; mvn >= 12; exactly one trace for O-2701 and two for O-2704, the second linked to the first; every Loki line for O-2701 carries that trace id; the sweeper timeout 15 of 15 or rated; README lists what was trimmed from 22.
- **Every chapter:** prose at most 2,500 words; exactly one callout; at least 2 figures from specs; `part: observing-event-flows`; no dashes, versions, test names, self-reference, forward references; UBI 10 Containerfiles; `/q/health/ready` healthchecks; env conventions; demo switches labelled; every claim backed by a check, test, log or `sources.md` row, or cut.
- **Iteration:** validate.py, voice-scan, idiomatic-scan, host_ids, site build green; no domain-model change and no earlier example touched except doc backlinks; versions.md agent row with reason; final run of 25 to 27 logged; CI green; tag r10.0.

## Risks

| Risk | Silent failure | Detection | Response |
|---|---|---|---|
| Agent and bundled API 1.23.1 conflict, or agent fails on Connect's JDK | Connect starts, spans never arrive | S1; banner precondition; trace-service check | fallback 1(b) |
| Connect internal topics flood Tempo | noisy search, slow polls | S1 noise count | disable agent Kafka instrumentation, rely on EventRouter injection |
| Instrumentation already links, not parents | "one trace" claim false | S2; trace-shape checks | restate; invert switch |
| Tempo ingestion lag | flaky trace checks | 15-run loop on 25 | poll 90 s; rate stated |
| Slot lag noise from unrelated WAL | alert fires at rest | S5; "under threshold at rest" precondition | heartbeat config; threshold above noise |
| Record timestamp is outbox time, not read time | the age lesson inverts | S5 | restate as measured |
| Extra collector config drops OTLP metrics | every other metric check fails | S4; precondition | separate pipeline; S4 fallback |
| Grafana 13.3 alert provisioning path differs | rule absent; "never fires" looks like a pass | "rule loaded and Normal" precondition | `GF_PATHS_PROVISIONING`; Prometheus rule fallback |
| MDC not exported to Loki | order-id log checks pass on message text only | S7; bites | state the mechanism used |
| Agent download each run | slow or failing start | init exit-code precondition | rate recorded; Maven checksum verification |
| Vacuous negative (switch not wired) | witness passes for the wrong reason | bite runs logged | as r08/r09 |
| Overlap with the sibling tutorial | copied framing or code | validator 7-gram | own scenario; link |
| Chapter 26 over 2,500 words | | draft count | DLQ ratio prose to README, then table rows |
| Host identifiers in captured Tempo or Loki JSON | | `host_ids.py check` | filter; scrub `host.name` |

Critical files: `examples/08-transactional-outbox/` (connector JSON, `OrderService.java`, `V1__orders.sql` with `tracingspancontext`); `examples/22-saga/` (compose, verify.sh helpers, `SagaSweeper.java`, `PaymentRoute.java`); `examples/13-retry-and-dead-letter/inventory-service/` (DLQ failure strategy); `examples/_infra/compose-connect.yaml`, `compose-lgtm.yaml`; `scripts/validate.py`.
