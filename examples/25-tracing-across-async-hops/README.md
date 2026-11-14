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

`verify.sh` refuses to start while another stack is running, removes any earlier containers and volumes before it starts, and stops this stack on exit unless `KEEP=1`. It ends with `TBD/TBD checks passed`. Tempo needs TBD(spike: 20 to 40 s) before a trace is searchable, so the script polls. `tools/tempo.py` reads Tempo's HTTP API for it.

## What verify.sh does

1. Starts the stack, registers the connector, and checks it, the one-partition topic, the readiness and consumer group of both services, the agent banner in Connect's log and the seeded stock.
2. O-2501: stock falls by one, the record carries a `traceparent`, exactly one trace has `order.id` O-2501 with spans of three services, a structural TraceQL query finds the consumer below the request, the span ancestry is the full chain, `debezium-read` sits beside the consumer, the header holds the trace id and the `db-log-write` span id, and Prometheus holds the Tempo service-graph edge from connect to inventory-service.
3. A burst of 100 orders: every trace crosses the three services. A `NOTE` line prints the median and range of the hop through Connect.
4. inventory-service restarts in link form. O-2502 gives two traces: the order's stops at Connect, and the consumer's root span links to its `db-log-write` span.
5. Loki holds an inventory-service line for O-2501 with the trace id of its trace.

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
| `CONNECT_TRACING=off ./verify.sh` (demo code) | No agent in Connect | TBD | TBD |
| `CONSUMER_TRACE=link ./verify.sh` (demo code) | The consumer starts a linked trace for the whole run | TBD | TBD |
| Remove `Span.current().setAttribute` from `OrderResource` | The search by order id finds no trace of the order's request | TBD | TBD |

Measurements to take in the window: Connect healthy TBD(spike: 10 to 13 s after start), stack up TBD(spike: 29 to 31 s), Connect memory at rest TBD(spike: 689 MiB), `db-log-write` duration TBD(spike: 200 to 700 ms).
