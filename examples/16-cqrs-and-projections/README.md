# 16 CQRS and projections

`order-service` is the query side of the order-to-delivery flow. It keeps no write model. It reads the six public topics that the order, payment and shipping services publish, applies each event to one table, `orders.order_view`, and answers `GET /orders/{id}` and `GET /customers/{customerId}/orders` from that table only. Every column belongs to one owner, and every update is an upsert that only moves forward, so the topics can be read in any order and any record can be read twice. Each answer carries `asOf`, the record timestamp of the last event applied, so a reader can see how current it is. The write side of an order is chapter 14; this service never calls it.

Quarkus, Hibernate ORM with Panache (a read-only entity), Flyway, Quarkus REST, SmallRye Reactive Messaging with pausable channels, Camel Kafka for the comparison route, PostgreSQL, Kafka, SmallRye Health and OpenTelemetry to the LGTM stack. Topic names come from `Topics` in `examples/domain-model`.

## Service

| Service | Port | Purpose |
|---|---|---|
| `order-service` | 8080 | `GET /orders/{id}`, `GET /customers/{customerId}/orders`. Health on `/q/health/ready` |

| Setting | Default | Effect |
|---|---|---|
| `PROJECTOR_PROFILE` | `prod` | `camel-projector` turns the six SmallRye channels off and starts one Camel route over the six topics (group `order-view-camel`) |
| `PROJECTION_GUARD` | `on` | Demo code. `off` removes the forward-only guard from the shipment upsert, so the last event applied wins (the negative witness) |
| `PROJECTOR_DELAY_MS` | `0` | Demo code. Every event takes this long to apply, so the backlog can outgrow the projector |

The demo endpoints `POST /demo/channels/{name}/pause`, `POST /demo/channels/{name}/resume`, `GET /demo/channels` and `GET /demo/lag` are demo code too. `{name}` is one of `placed`, `cancelled`, `paid`, `scheduled`, `in-transit`, `delivered`.

## Run

```bash
(cd ../domain-model && mvn -B -q install)
mvn -B verify      # guarded upserts, status, Kafka to view per topic, pause and resume, and the Camel route against Dev Services
./verify.sh        # compose stack
KEEP=1 ./verify.sh # leave the stack up; Grafana is on http://localhost:3000
docker compose down -v
```

`verify.sh` refuses to start while another stack is running, removes any earlier containers and volumes before it starts, and stops this stack on exit unless `KEEP=1`. It ends with `29/29 checks passed`.

## What verify.sh does

1. Starts the stack and checks that the six groups own their partitions, that the six topics have one partition each, and that the schema holds the view and the migration history and nothing else.
2. Produces the lifecycle of `O-1601` one event at a time, in the owners' record format (key = order id, `id` and `eventType` headers), and reads the view after each one: PLACED, PAID, SHIPPED, SHIPPED with the shipment in transit, DELIVERED. The customer list holds that order and no other customer's. `O-1602` is placed and cancelled.
3. Read lag: the `delivered` channel is paused, `ShipmentDelivered` for `O-1603` is produced, and the group has lag 1. Five reads over five seconds all return SHIPPED with an `asOf` older than the delivered record. After resume the order is DELIVERED and the group has lag 0. Prometheus holds `order_view_lag_max_milliseconds` at least as large as the pause.
4. Order across topics: with the `in-transit` channel paused, `ShipmentScheduled` and `ShipmentDelivered` for `O-1604` are applied and then `ShipmentInTransit`. The view stays DELIVERED.
5. An event before its order: with the `placed` channel paused, `ShipmentScheduled` for `O-1605` creates a row with no customer. After resume the order is listed under its customer as SHIPPED.
6. A burst of 250 orders (1,250 records, one producer call per topic) converges, and the 95th percentile of `order.view.lag` is below 2 s.
7. Replay: the service stops, all six groups are reset to the earliest offset and the service starts again. The view hash is unchanged.
8. Camel: the view is emptied and the service is recreated with `camel-projector`. One group owns the six partitions, the view hash equals the hash from the channels, and `O-1604` is DELIVERED.
9. A trace in Tempo and logs in Loki.

The hash covers every row except `O-1604`, whose final state is what checks 18 and 27 examine.

## Layout

| Path | Purpose |
|---|---|
| `order-service/.../Events`, `Deserializers` | The six event records and one Jackson deserializer per topic |
| `order-service/.../OrderViewProjector` | One guarded native upsert per event; each sets only the columns its owner owns |
| `order-service/.../ProjectionChannels` | Six SmallRye channels, one group each, acknowledged after the transaction; the `order.view.lag` timer |
| `order-service/.../OrderView`, `OrderViewResource` | The read-only entity with the status derived from the furthest fact, and the two queries |
| `order-service/.../OrderViewRoute` | The Camel route: one consumer over the six topics (comparison) |
| `order-service/.../DemoSwitches`, `DemoChannels` | Demo code: the guard and delay switches, pause and resume, the lag percentiles |
| `order-service/src/main/resources/db/migration/V1__order_view.sql` | The view table, its customer index and the shipment rank function |
| `compose.yaml` | Topics and the service |

## Tests and checks in detail

The Maven build runs 14 tests against PostgreSQL and Kafka from Dev Services. They cover the guarded upsert, the same event applied twice, an event before its order, CANCELLED as terminal, the status derived from the facts a row holds, each topic moving an order forward, a paused topic leaving the row stale until it resumes, the lag timer, and the Camel route building the same view. `verify.sh` starts Kafka, PostgreSQL, the LGTM stack and the service, produces the events in each owner's record format, and prints `29/29 checks passed`.

The checks read the effects:

- One order moves through PLACED, PAID, SHIPPED and DELIVERED as its five events are produced one at a time, with its customer, amount, carrier and tracking number; the customer's list holds that order and no one else's.
- With the delivered channel paused, the group has lag 1, five reads over five seconds all return SHIPPED with an `asOf` older than the delivered record, and after resume the order is DELIVERED and the lag is 0. Prometheus holds `order_view_lag_max_milliseconds`, at least as large as the pause.
- With the in-transit channel paused, `ShipmentDelivered` is applied first and `ShipmentInTransit` last, and the order stays DELIVERED.
- A shipment for an order with no row creates a partial row with no customer, and the later `OrderPlaced` lists it under its customer.
- 1,250 records converge, and the 95th percentile of `order.view.lag` is below 2 s.
- Stopping the service, resetting all six groups to the earliest offset and starting it again leaves the view's hash unchanged.
- With the view emptied and the `camel-projector` profile, one group owns the six partitions and builds the same hash.

## Experiments

Each row changes a single thing and names the checks that fail.

| Switch | Effect | Failing checks | Measured result |
|---|---|---|---|
| `PROJECTION_GUARD=off ./verify.sh` (demo code) | The late `ShipmentInTransit` replaces DELIVERED, and the reader sees SHIPPED. Without the guard the last shipment event applied wins, and a second pass over the topics does not apply the three shipment events in their original order. `ShipmentInTransit` applied after `ShipmentDelivered` moves `O-1604` back to IN_TRANSIT. The replay and the Camel route also land on a different view | 18, 26 and 27 in all five runs, 24 in four of the five | In a recorded run, a replay from offset 0 and the Camel route each moved 252 of the 254 other orders from DELIVERED back to SCHEDULED. Four runs ended 25 of 29 and one ended 26 of 29 |
| `PROJECTOR_DELAY_MS=50 ./verify.sh` (demo code) | The burst outruns the projector, so lag grows with the backlog; the burst still converges within 60 s | 23 | 95th percentile of lag 12,881 ms against 318 to 334 ms without the delay; 28 of 29 pass |

