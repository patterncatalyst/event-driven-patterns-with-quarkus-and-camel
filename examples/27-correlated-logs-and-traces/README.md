# 27 Correlated logs and traces

An order is carried from placed to paid by three services, and one question finds everything about it: which traces, and which log lines. Every span has the attribute `order.id`, every handler puts `orderId` in the log context, every log line carries the trace id, and the sweeper's trace for a timed-out order links to the trace of the request that placed it. Tempo is searched by `{ span.order.id = "O-2701" }` and Loki by `| orderId="O-2701"`.

This is a trimmed standalone copy of [22 Saga](../22-saga/), with Connect traced by the OpenTelemetry Java agent from `examples/_infra/compose-connect-tracing.yaml`. `order-service` owns the saga state (`orders.order_saga`: state, deadline, version, and the `traceparent` of the request) and the sweeper. `inventory-service` reserves and releases stock. `payment-service` is a Camel participant that takes the payment, declines it above a limit and refuses a payment for an order it has seen cancelled. Every event leaves through the outbox of the service that publishes it, and one Debezium connector reads the three outbox tables.

Quarkus, SmallRye Reactive Messaging, Camel Kafka (payment-service), Hibernate ORM with Panache, Flyway, `debezium-quarkus-outbox`, Quarkus Scheduler, SmallRye Health, PostgreSQL, Kafka, Kafka Connect and OpenTelemetry to the LGTM stack. Topic names come from `Topics` in `examples/domain-model`. The six topics are created by `topics-init` with one partition each.

## What was trimmed from 22

| Left out | Why |
|---|---|
| The burst of 200 orders and its timing | Throughput is not the lesson here |
| The restart check of `order-service` with a moved deadline | Saga state across a restart is chapter 22's lesson |
| The race test of a sweep against a payment, and the inventory race test | Same |
| `PAYMENT_CANCEL_GUARD` | The refusal of a cancelled order stays, as the guard that makes the timeout case end cleanly; its switch and negative witness stay in 22 |
| The out-of-stock order and the repeated `PaymentFailed` | Compensation variants are covered in 22 |

Kept from 22: the three outboxes and the connector, the sweeper, the refusal of a payment for a cancelled order, and the gate route that holds the payment route until the cancellation consumer has no lag, because without it the refusal is a race after a restart.

Added in 27: the `Correlated` bean in each service (a span with `order.id` and the `orderId` log context), the console format with the three ids, `order_saga.traceparent` recorded at placement, the sweeper's `cancel expired saga` root span with a link, and `tools/observe.py` (Tempo, Loki and Grafana over HTTP).

## Services

| Service | Port | Purpose |
|---|---|---|
| `order-service` | 8080 | `POST /orders`, `GET /orders/{id}`, the saga state, the sweeper |
| `inventory-service` | 8082 | Reservation and release |
| `payment-service` | 8084 | Payment and the check of cancelled orders |

| Setting | Default | Effect |
|---|---|---|
| `SAGA_PAYMENT_TIMEOUT` | `15s` | Demo code. How long a saga may wait in one state before the sweeper cancels it |
| `PAYMENT_DECLINE_ABOVE` | `500.00` | Demo code. Payments above this amount are declined |
| `SWEEPER_LINK` | `on` | Demo code. `off` starts the sweeper's trace without a link to the order's trace |
| `ORDER_ID_IN_LOGS` | `on` | Demo code. `off` leaves the order id out of the log context, so lines carry the trace id only |
| `CONNECT_ENABLE_OTEL`, `CONNECT_KAFKA_OPTS` | tracing on | Demo code, from `_infra`. `CONNECT_ENABLE_OTEL=false CONNECT_KAFKA_OPTS=` runs Connect without the agent |

## Run

```bash
(cd ../domain-model && mvn -B -q install)
mvn -B verify      # the three services against Dev Services PostgreSQL and Kafka
./verify.sh        # compose stack
KEEP=1 ./verify.sh # leave the stack up; Grafana is on http://localhost:3000
docker compose down -v
```

`verify.sh` refuses to start while another stack is running, removes any earlier containers and volumes before it starts, and stops this stack on exit unless `KEEP=1`. It ends with `TBD/TBD checks passed`. The Connect container downloads the OpenTelemetry Java agent into a run-scoped volume on each fresh start.

## What verify.sh does

1. Starts the stack, registers the connector over the three outbox tables and waits for it, the six one-partition topics, the nine consumer groups, the agent banner in Connect's log and the seeded stock.
2. O-2701 is PAID. Tempo holds exactly one trace for `span.order.id = "O-2701"`, with spans of order-service, connect, inventory-service and payment-service and the consumer span of order-service for PaymentProcessed, and each of the three services has a span of its own with the order id.
3. Loki holds lines with `orderId` O-2701 from the three services, every one with the trace id of that trace. A payment-service line leads to a trace that holds `POST /orders`, and the trace id leads to lines of at least three services.
4. O-2703 is declined and cancelled, the stock is released, and the release is a span of the one trace of O-2703.
5. payment-service is stopped. O-2704 is reserved and waits; the sweeper cancels it after its deadline (a `NOTE` line prints how long after). Tempo holds exactly two traces for it, the one the sweeper started links to the trace of `POST /orders`, and Loki's lines of O-2704 name two trace ids.
6. payment-service returns, waits until its cancellation consumer has no lag and refuses the waiting reservation: its log says so, there is no payment row and no payment event.
7. Grafana has the `trace_id` derived field on Loki and the trace-to-logs link on Tempo; Prometheus holds `saga_outcomes_total` for compensated and timed-out orders.

## Layout

| Path | Purpose |
|---|---|
| `*/.../Correlated` | The span with `order.id` and the `orderId` log context around every handler |
| `order-service/.../SagaService` | The transitions, the stored `traceparent`, and `cancelExpired`, the sweeper's root span with its link |
| `order-service/.../SagaListener`, `SagaSweeper`, `OrderResource` | The four replies, the scheduled sweep, the HTTP edge |
| `inventory-service/.../ReservationService`, `InventoryListener` | Reserve and release, the three inputs |
| `payment-service/.../PaymentService`, `PaymentRoute`, `CancellationRoute`, `CancellationGateRoute`, `CancellationLag` | The payment, the Camel routes, and the gate that waits for the cancellations |
| `*/src/main/resources/db/migration` | One schema per service, each with its outbox table |
| `connect/correlation-outbox.json` | The one connector over the three outbox tables |
| `tools/observe.py` | Tempo, Loki and Grafana over their HTTP APIs, used by `verify.sh` |
| `compose.yaml` | Topics, the three services, and Connect with the tracing override from `_infra` |

## Tests and checks in detail

The Maven build runs 24 tests against PostgreSQL and Kafka from Dev Services, with spans kept in memory (an `InMemorySpanExporter` bean): in `order-service` (14), a step runs in a span with `order.id` and the order id is in the log context while it runs and gone after; placing an order over HTTP puts the order id on a span of the request's trace; the saga stores the `traceparent` of that trace; the sweeper's trace is a root with the order id and a link to the order's trace; the cancellation event's outbox span is a child of the sweeper's root; with `SWEEPER_LINK=off` the root has no link; with `ORDER_ID_IN_LOGS=off` the log context has no order id but the span still carries it; and the transitions of the saga, the HTTP edge and the replies as Kafka records. In `inventory-service` (4): reserve and release, release twice, insufficient stock, and a record with a `traceparent` header is handled under that trace id with the order id on its span. In `payment-service` (6): a payment is taken, one above the limit is declined, one for a cancelled order is refused, the payment runs in a span with the order id, the route takes a payment from a topic record, and the gate starts the payment route.

The checks read effects: the order view over HTTP, rows in the three schemas, records on the topics, consumer-group lag, the services' logs, and Tempo, Loki, Grafana and Prometheus through their HTTP APIs. Tempo makes a trace searchable some seconds after the request TBD(spike: 20 to 40 s), so the trace checks poll for up to 90 seconds. Every recreate waits for readiness and the consumer groups; outputs are polled, never slept on.

## Experiments

Each row changes a single thing and names the checks that fail. Check numbers are the `PASS` and `FAIL` numbers `verify.sh` prints. The sets below are the expected ones and are frozen after the runtime window.

| Switch | Effect | Failing checks | Measured result |
|---|---|---|---|
| `SWEEPER_LINK=off ./verify.sh` (demo code) | The sweeper's trace has no link | 24 (expected) | TBD |
| `ORDER_ID_IN_LOGS=off ./verify.sh` (demo code) | Lines carry the trace id and no `orderId` | the Loki checks by order id: 12, 13, 14 and 25 (expected) | TBD |
| `CONNECT_ENABLE_OTEL=false CONNECT_KAFKA_OPTS= ./verify.sh` (demo code) | Connect adds no span and no `traceparent` header, so each consumer starts a new trace | the trace-shape checks (expected) | TBD |
| Order id attribute removed in `payment-service` (temporary edit, bite proof) | The trace has no payment span with the order id | 11 (expected) | TBD |

Timing witness: the cancellation of O-2704 (check 21) is observed within the 3500 ms the check allows. TBD (15 runs).

## Notes

The order id is a span attribute and a log attribute, never a Loki label or a metric label. The scheduler's own tracing (`quarkus.scheduler.tracing.enabled`) is left off: it makes a trace of every run, and the sweeper starts a span only when it cancels a saga. The sweeper's link is only as good as the stored `traceparent`: an order placed with no active span has none, and its cancellation trace has no link.
