# 25 Tracing across async hops

`order-service` stores an order and its `OrderPlaced` event in one transaction through the Debezium outbox extension, which also writes the active trace context into the outbox row. Debezium Connect, running the OpenTelemetry Java agent, reads the row, adds its `db-log-write` and `debezium-read` spans and puts a `traceparent` header on the record. `inventory-service` reserves stock from the record, and its consumer span continues the order's trace. A demo switch starts a new trace linked to the order's instead.

Quarkus, SmallRye Reactive Messaging, Hibernate ORM with Panache, Flyway, `debezium-quarkus-outbox`, SmallRye Health, OpenTelemetry, PostgreSQL, Kafka, Kafka Connect and the LGTM stack. Topic names come from `Topics` in `examples/domain-model`. Idempotent consumption is chapter 11 and is not repeated here.

## Services

| Service | Port | Purpose |
|---|---|---|
| `order-service` | 8080 | `POST /orders`; sets `order.id` on the request span |
| `inventory-service` | 8082 | Reserves stock; `ReservationService` carries `order.id` on a `reserve stock` span |

| Setting | Default | Effect |
|---|---|---|
| `CONSUMER_TRACE` with `CONSUMER_TRACE_ENABLED` | `parent`, `true` | Demo code. `link` with `false` replaces the channel's consumer span with a root span linked to the record's context. `verify.sh` sets both from `CONSUMER_TRACE=link` |
| `CONNECT_TRACING` | `on` | Demo code, `verify.sh` only. `off` starts Connect without the agent and without `ENABLE_OTEL` |

Connect comes from `examples/_infra/compose-connect.yaml` merged with `compose-connect-tracing.yaml`: an init service copies the pinned agent from Maven Central, checksum verified, into a volume removed by `down -v`. Only the agent's OpenTelemetry API bridge is enabled; its default Kafka and JDBC instrumentation replaced the outbox trace with a new root and flooded Tempo. The connector configuration, `connect/order-outbox.json`, has no tracing keys.

## Run

```bash
(cd ../domain-model && mvn -B -q install)
mvn -B verify      # both services against Dev Services PostgreSQL and Kafka
./verify.sh        # compose stack
KEEP=1 ./verify.sh # leave the stack up; Grafana is on http://localhost:3000
docker compose down -v
```

`verify.sh` refuses to start while another stack is running, removes any earlier containers and volumes before it starts, and stops this stack on exit unless `KEEP=1`. It ends with `22/22 checks passed`. Tempo needs about 30 s before a trace is searchable, so the script polls. `tools/tempo.py` reads Tempo's HTTP API for it.

## What verify.sh does

1. Starts the stack, registers the connector, and checks it, the one-partition topic, the readiness and consumer group of both services, the agent banner in Connect's log and the seeded stock.
2. O-2501: stock falls by one, the record carries a `traceparent`, exactly one trace has `order.id` O-2501 with spans of three services, a structural TraceQL query finds the consumer below the request, the span ancestry is the full chain, `debezium-read` sits beside the consumer, the header holds the trace id and the `db-log-write` span id, and Prometheus holds the span-metrics series of Connect's `db-log-write` span.
3. A burst of 100 orders: every trace crosses the three services. A `NOTE` line prints the median and range of the hop through Connect.
4. inventory-service restarts in link form. O-2502 gives two traces: the order's stops at Connect, and the consumer's root span links to its `db-log-write` span.
5. Loki holds an inventory-service line for O-2501 with the trace id of its trace, and Prometheus holds an HTTP server metric of order-service with its `service_name` label.

## Layout

| Path | Purpose |
|---|---|
| `order-service/.../OrderResource`, `OrderService`, `OrderPlacedEvent` | The request, the transaction and the outbox event |
| `inventory-service/.../PlacedListener`, `ReservationService` | The consumer and the span-carrying reservation |
| `inventory-service/.../LinkedProcessing` | Demo code: the link form |
| `*/src/main/resources/db/migration` | One schema per service; the orders schema has the outbox table with `tracingspancontext` |
| `connect/order-outbox.json` | The connector over the outbox table |
| `tools/tempo.py` | Tempo search and span walking for `verify.sh` |
| `compose.yaml` | Topic and the two services |

## Tests and checks in detail

The Maven build runs 11 tests with an in-memory span exporter against PostgreSQL and Kafka from Dev Services. In `order-service` (4): the order id is on the request span, `outbox-write` is its child, the outbox row's `tracingspancontext` holds the request's trace id, and two requests carry two traces. In `inventory-service` (7): the consumer span is a child of the context in a record's header; the `reserve stock` span carries the order id in the same trace and under the consumer span; a record without a header starts a new trace; the reservation takes the stock; two records without headers give two traces; and in link form the processing is a new trace with one link to the header's trace and span, and the record's trace holds no consumer span.

`verify.sh` reads the effects through Tempo, Loki and Prometheus HTTP APIs, never the UI.

## Experiments

| Switch | Effect | Failing checks | Measured result |
|---|---|---|---|
| `CONNECT_TRACING=off ./verify.sh` (demo code) | No agent in Connect | 6, 7, 8, 9, 10, 11, 12, 13, 16, 20, 21 (11/22 pass) | No `traceparent` on the record; the reservation is a trace of its own, so the order id finds two traces, the span-metrics series of Connect is absent and the link check has no Connect span to point at |
| `CONSUMER_TRACE=link ./verify.sh` (demo code) | The consumer starts a linked trace for the whole run | 7, 8, 9, 10, 11, 12, 16, 21 (14/22 pass) | Two traces per order; the link checks 19 and 20 pass |
| Remove `Span.current().setAttribute` from `OrderResource` | The request span loses the order id | 9, 19, 20 (19/22 pass) | The trace is still found through the reservation span, which carries the order id; the structural query from the request finds nothing, and the link phase finds one trace instead of two |

Measurements, from the runs: the order's trace was searchable in Tempo 30 to 32 s after the order was accepted in all 15 loop runs (15 of 15 complete; the loop is checks 7 and 8, repeated 15 times from a fresh stack, three of them inside full runs). Across the 15 logged bursts of 100 orders the hop through Connect, from the end of `outbox-write` to the consumer span, had a median between 247 and 766 ms (single orders 176 to 829 ms). In one run `db-log-write` lasted 855 ms and `debezium-read` 378 ms. After one burst Connect used 752 MiB of its 2 GiB limit (one sample, no baseline without the agent). Check 7 also changed meaning: it now requires the request span and the consumer span to be searchable before it counts the traces, because with Connect tracing off the consumer's trace could be searchable first and pass as the only one (it did once, in a repair run). Check 13 reads the span-metrics series `traces_spanmetrics_calls_total{service="connect",span_name="db-log-write"}`. It first read the service-graph series of requests to order-service, which Tempo draws from the user to order-service edge with or without Connect tracing, so it passed in both experiments. Connect's spans are internal, so Tempo draws no connect edge; the span metrics are the observable that exists only when Connect traces. Check 22 reads an application metric with its `service_name` label.

Three checks failed in about one run in sixteen without a defect in the example: check 20 once in an unmodified run (21/22), check 12 once in the 10-repeat link loop, and checks 12 and 21 together in another unmodified run (20/22). Tempo's search API returns trace ids without their leading zeros, and one id in sixteen starts with a zero; the checks compared that id with the full 32-digit id of the `traceparent` header, the Loki line and the span link. `tools/tempo.py` now pads the ids it returns to 32 digits. The failures are attributed to this: the pattern fits (checks 8 to 11 passed on the same trace in the 20/22 run, and the observed rate is 1 in 16), but no run has hit a trace id with a leading zero since the fix, so it is not reproduced. On a check 20 failure `verify.sh` prints the order trace id, the consumer trace id, the services of the order trace, the links of the consumer trace and the `db-log-write` span id of the last attempt. The link phase (inventory-service recreated in link form, one order, checks 19 and 20) was repeated 10 times on one stack before the fix: checks 19 and 20 passed 10 of 10, with two traces searchable after 32 to 33 s each time.
