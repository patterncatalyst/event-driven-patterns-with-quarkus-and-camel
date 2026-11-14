# 22 Saga

An order is carried from placed to paid by three services that react to each other's events, with no coordinator. `order-service` owns the saga state (`orders.order_saga`: state, deadline, version) and a sweeper that cancels sagas past their deadline. `inventory-service` reserves stock and releases it as a compensation that is idempotent and commutative. `payment-service`, a Camel participant, takes the payment, declines it above a limit and refuses a payment for an order it has seen cancelled. Every event leaves through the outbox of the service that publishes it, and one Debezium connector reads the three outbox tables.

Quarkus, SmallRye Reactive Messaging, Camel Kafka (payment-service), Hibernate ORM with Panache, Flyway, `debezium-quarkus-outbox`, Quarkus Scheduler, SmallRye Health, PostgreSQL, Kafka, Kafka Connect and OpenTelemetry to the LGTM stack. Topic names come from `Topics` in `examples/domain-model`. The six topics are created by `topics-init` with one partition each, because topics the connector creates get three.

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
| `PAYMENT_CANCEL_GUARD` | `on` | Demo code. `off` removes the check of cancelled orders, so a late payment is taken |

## Run

```bash
(cd ../domain-model && mvn -B -q install)
mvn -B verify      # the three services against Dev Services PostgreSQL and Kafka
./verify.sh        # compose stack
KEEP=1 ./verify.sh # leave the stack up; Grafana is on http://localhost:3000
docker compose down -v
```

`verify.sh` refuses to start while another stack is running, removes any earlier containers and volumes before it starts, and stops this stack on exit unless `KEEP=1`. It ends with `30/30 checks passed`.

## What verify.sh does

1. Starts the stack, registers the connector over the three outbox tables and waits for it, the six one-partition topics, the nine consumer groups and the seeded stock.
2. O-2201: PAID, stock down by one, the three events in causal order across topics.
3. A burst of 200 orders (O-22100 to O-22299), every second one above the decline limit: 100 COMPLETED, 100 CANCELLED, 100 reservations released. A `NOTE` line prints the time to the last final state.
4. O-2202 asks for more stock than exists: CANCELLED (OUT_OF_STOCK), no payment, stock unchanged.
5. O-2203 is declined: CANCELLED (PAYMENT_DECLINED), reservation RELEASED, stock restored. A repeated PaymentFailed with the same id header is dropped by the inbox of inventory and the stock does not move.
6. payment-service is stopped. O-2204 is reserved and waits; the sweeper cancels it after its deadline (a `NOTE` line prints how long after), OrderCancelled is published and the stock is released. payment-service returns, reads the cancellations first and refuses the waiting reservation: its log says so, there is no payment row and no payment event.
7. payment-service is stopped again, O-2205 waits, order-service is recreated, and the sweeper cancels O-2205 with the deadline that PostgreSQL held before the recreate. The returning payment-service refuses it too.
8. Prometheus holds `saga_outcomes_total` for outcome `compensated`, Tempo a trace and Loki a log line.

## Layout

| Path | Purpose |
|---|---|
| `order-service/.../SagaService` | The transitions: conditional updates, inbox, outbox, sweeper query |
| `order-service/.../SagaListener`, `SagaSweeper`, `OrderResource` | The four replies, the scheduled sweep, the HTTP edge |
| `inventory-service/.../ReservationService`, `InventoryListener` | Reserve and release, the three inputs |
| `payment-service/.../PaymentService` | The payment and the cancelled-order check |
| `payment-service/.../PaymentRoute`, `CancellationRoute`, `CancellationGateRoute`, `CancellationReplay` | The Camel routes and the read of cancellations before the first payment |
| `*/src/main/resources/db/migration` | One schema per service: orders, inventory, payments, each with its outbox table |
| `connect/saga-outbox.json` | The one connector over the three outbox tables |
| `compose.yaml` | Topics and the three services |

## Tests and checks in detail

The Maven build runs 28 tests against PostgreSQL and Kafka from Dev Services. In `order-service` (12): each reply moves the saga once, insufficient stock cancels with no compensation, a declined payment cancels, the sweeper cancels an expired saga and writes the outbox row in the same transaction, a rolled back sweep leaves neither, a late payment for a cancelled order changes nothing, one event id is applied once, and a sweep and a payment released together over 50 rounds have exactly one winner (probabilistic: it makes the overlap likely, it does not prove it); three more cover the HTTP edge and the replies as Kafka records. In `inventory-service` (7): reserve then release restores the stock, release twice releases once, a release before the reservation refuses it, insufficient stock is answered and holds nothing, one event id is applied once, a reservation and a release released together over 50 rounds end with the stock back, and records on the topics reserve and release. In `payment-service` (9): a payment is taken, one above the limit is declined, one for a cancelled order is refused, a repeated reservation takes the money once, the cancellations are read before a payment is taken, the route takes a payment from a topic record, with `payment.cancel-guard=off` a payment for a cancelled order is taken (asserts the bad outcome), the replay is a container bean with its configuration injected (the gate route calls it by name, and a bean built by Camel instead has no configuration), and the gate route starts the payment route.

The checks read effects: the order view over HTTP, rows in the three schemas, records on the topics with their timestamps and id headers, consumer-group lag, the services' logs and Prometheus. Every recreate waits for readiness and the consumer groups; outputs are polled, never slept on. Consumers use a 10-second session timeout and a 3-second heartbeat.

## Experiments

Each row changes a single thing and names the checks that fail. Check numbers are the `PASS` and `FAIL` numbers `verify.sh` prints.

| Switch | Effect | Failing checks | Measured result |
|---|---|---|---|
| `PAYMENT_CANCEL_GUARD=off ./verify.sh` (demo code) | The returning payment-service takes the payment for the cancelled orders | 21, 22 and 27 | 27/30: no refusal is logged for O-2204 and O-2205, and a payment event exists for O-2204 |
| `SAGA_PAYMENT_TIMEOUT=60s ./verify.sh` (demo code) | The sweeper cancels O-2204 60 seconds after the reservation | 18 | 29/30: the check waits 45 seconds for the cancellation; the cancellation still happens before payment-service returns, so the order ends CANCELLED and checks 19 to 23 pass |
| Inbox of inventory removed (temporary edit, bite proof) | A repeated PaymentFailed is applied again | 15 | 29/30: inventory applies the repeated PaymentFailed again; the others pass |

Timing witness: the cancellation of O-2204 (check 18) passed in 15 of 15 fresh runs. The cancellation was observed 595 to 1727 ms after the deadline (median 1128 ms) against the 3500 ms limit the check allows. Burst: 200 orders reached a final state after 2597 to 3698 ms in 15 fresh runs (median 3651 ms, 55 orders per second; two runs took 2.6 s and the other 13 between 3.6 and 3.7 s). The final serial run took 2595 ms. Times are for an 8-core CPU.

## Notes

The coordinator-free saga has no isolation: the stock of a saga in flight is visibly reserved. `payment-service` reads the whole cancellation topic at every start before its payment route starts, which is cheap here and grows with the topic.
