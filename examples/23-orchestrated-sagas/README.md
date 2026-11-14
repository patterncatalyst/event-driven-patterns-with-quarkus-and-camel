# 23 Orchestrated sagas

`order-service` runs the same order saga, order then stock then payment, under two orchestrators over the same participants: the Camel Saga EIP with a Narayana LRA coordinator, and a Quarkus Flow workflow with its state in PostgreSQL. `inventory-service` and `payment-service` take commands from `edp.inventory.commands` and `edp.payments.commands`, answer on the catalog topics and join the Camel saga when the command carries its header. The example accompanies the chapter on orchestrated sagas.

Quarkus, Camel Kafka, Camel Saga with the LRA binding, Quarkus Flow with its Hibernate persistence and CloudEvent messaging bridge, SmallRye Reactive Messaging, Hibernate ORM with Panache, Flyway, PostgreSQL, SmallRye Health and OpenTelemetry to the LGTM stack. Topic names come from `Topics` in `examples/domain-model`.

## Services

| Service | Port | Purpose |
|---|---|---|
| `order-service` | 8080 | `POST /orders/camel` and `POST /orders/flow` start a saga (one `Order` JSON body, 202), `GET /orders/{id}` reports the status and the owning orchestrator |
| `inventory-service` | 8082 | Participant: ReserveStock and ReleaseStock |
| `payment-service` | 8084 | Participant: TakePayment and RefundPayment |
| `lra-coordinator` | 8091 | The Narayana coordinator, `GET /lra-coordinator` lists the sagas (`examples/_infra/compose-lra.yaml`) |

Orders `O-2301` to `O-2304` run under Camel and `O-2311` to `O-2314` under Flow in `verify.sh`; the stock is `SKU-23`, 20 units. Health on `/q/health/ready` of each service.

| Setting | Default | Effect |
|---|---|---|
| `SAGA_TIMEOUT` | `PT20S` | Demo code: the time limit of a saga in both orchestrators (ISO 8601) |
| `PAYMENT_JOIN` | `on` | Demo code: `off` runs TakePayment outside any saga, so the coordinator never calls the refund |
| `FLOW_COMPENSATION` | `on` | Demo code: `off` removes the refund and the release from the workflow, leaving only the cancel |
| `PAYMENT_DECLINE_ABOVE` | `500.00` | Demo code: an amount above this is declined |
| `FLOW_SCAN_INTERVAL` | unset | Demo code: `QUARKUS_FLOW_PERSISTENCE_SCAN_INTERVAL`, a periodic rescan of the workflow store |

## Run

```bash
(cd ../domain-model && mvn -B -q install)
mvn -B verify      # participants, both orchestrators and the Flow persistence against Dev Services
./verify.sh        # compose stack
KEEP=1 ./verify.sh # leave the stack up; Grafana is on http://localhost:3000
docker compose down -v
```

`verify.sh` refuses to start while another stack is running, removes any earlier containers and volumes before it starts, and stops this stack on exit unless `KEEP=1`. The stack needs about TBD of memory.

## What verify.sh does

1. Starts Kafka, PostgreSQL, the LGTM stack, the LRA coordinator and the three services. `topics-init` creates the seven topics with one partition each.
2. Places `O-2301` under Camel and `O-2311` under Flow with an amount of 100.00 and reads where each ends: PAID, no active saga at the coordinator, no workflow instance left, and the saga header or the CloudEvent attribute on every command and reply.
3. Places `O-2302` and `O-2312` with 900.00, which payment-service declines: both CANCELLED, stock released, one OrderCancelled each.
4. Stops payment-service and places `O-2303` and `O-2313`. After the time limit the coordinator cancels the first, and the workflow's listen task times out for the second. Starts payment-service and reads what the late TakePayment did.
5. Recreates order-service with a longer time limit while payment-service is stopped and one order of each form is waiting, then starts payment-service.
6. Places 100 orders under each orchestrator in one burst.
7. A trace in Tempo, logs in Loki and the compensated Flow sagas in Prometheus.

## Tests and checks in detail

The Maven build runs the three services against PostgreSQL and Kafka from Dev Services and no coordinator: TBD tests. order-service runs the Camel saga on Camel's in-memory saga service, which keeps a saga on the exchange that started it and does not carry it in a header. Test routes therefore end the saga on that exchange, and the Kafka leg of the header is checked by `verify.sh`. The tests cover completion, compensation, the time limit, a compensation run twice, a saga completed by `AUTO` before any reply (the bad outcome `MANUAL` prevents), the workflow's happy, declined, insufficient and timed-out paths, an instance row while waiting and none after, and a reply without CloudEvent attributes not waking an instance. The participants' tests cover commands on the topic, the replies, a release that is idempotent and commutative with the reserve, and a refund before a take refused. `verify.sh` prints `TBD checks passed`.

The checks read the effects:

- Both orchestrators reach PAID for the same input and CANCELLED for the same declined input, and the stock ends where the paid orders leave it.
- The Camel records carry `Long-Running-Action` on both commands and both replies, and the Flow records carry `flowinstanceid`, on the replies as `ce_flowinstanceid`.
- With payment-service stopped both orders are CANCELLED within TBD of the order, the Camel order by the coordinator's cancel and the Flow order by the listen timeout, which also leaves RefundPayment and ReleaseStock on the topics.
- After payment-service starts there is no payment for the Camel order, because the join was refused and the log says so, and a REFUNDED payment for the Flow order.
- After the recreate the Camel order is PAID, because the reply joined the saga through its header and the coordinator, which was not restarted, closed it. The Flow order is still PLACED with its instance row after the payment was taken. That is the observed behaviour, not a guarantee to rely on.

## Findings about the stack

- **The Camel LRA extension and Quarkus Flow cannot share an application as published.** `camel-quarkus-lra` brings the classic RESTEasy client and Quarkus Flow brings the reactive REST client, and the build fails on two producers of the same build item. `order-service` excludes the classic client from the LRA extension; the reactive client provides the JAX-RS client API the LRA binding calls. The HTTP surface is a Camel `platform-http` route for the same reason.
- **The environment names are not `CAMEL_LRA_*`.** Camel Main reads those itself and aborts startup. The properties `camel.lra.coordinator-url`, `camel.lra.local-participant-url` and `camel.lra.callback-secret` take their values from `LRA_COORDINATOR_URL`, `LRA_PARTICIPANT_URL` and `LRA_CALLBACK_SECRET`. A blank URL switches the LRA service off, which the tests use.
- **A reply without CloudEvent attributes stops the Flow consumer** unless `failure-strategy` is `ignore`. Replies of the Camel form carry none, so `order-service` sets it, and each skipped reply is logged at ERROR.
- **The `option()` values of the orchestrator's own callbacks** arrived empty with `header("orderId")` and arrived with `simple("${header.orderId}")` in the spike.
- **The Flow restore does not resume a waiting instance.** The restore scans the store, finds the instance, logs that it is already active in memory, and skips it, so neither its timeout nor a correlated reply is processed. The documented knobs are `auto-restore` (on by default) and `scan-interval`; `FLOW_SCAN_INTERVAL` is the switch for trying the second. TBD(window: result).
- **The coordinator does not recover in-flight sagas after a restart.** In the spike a restart lost the saga without compensating it in 6 of 7 trials. `verify.sh` never restarts it.
- **Flow persistence logged a foreign-key violation on a task row** while instances ended in the Maven run. The order and the workflow completed in every test. TBD(window: whether it appears in the compose run).
- **The ordering of a late payment on the Flow path** relies on one partition per command topic and the key. With more partitions a refund could be read before the payment it undoes.

## Experiments

Each row changes a single thing and names the checks that fail.

| Switch | Effect | Failing checks | Measured result |
|---|---|---|---|
| `PAYMENT_JOIN=off ./verify.sh` | TakePayment runs outside any saga, the coordinator never calls the refund, and the late TakePayment for `O-2303` takes the payment | TBD | TBD |
| `FLOW_COMPENSATION=off ./verify.sh` | The workflow cancels `O-2313` and sends no RefundPayment or ReleaseStock: the payment stays COMPLETED and the stock stays reserved | TBD | TBD |
| `FLOW_SCAN_INTERVAL=5s ./verify.sh` | A periodic rescan of the workflow store; whether a waiting instance then resumes after the recreate | TBD | TBD |

`SAGA_TIMEOUT=PT0S` is not offered: a saga without a time limit is stated in the chapter, not run.

## Layout

| Path | Purpose |
|---|---|
| `order-service/.../CamelOrderSagaRoute`, `CamelSagaRepliesRoute` | The Camel orchestrator: start, replies, callbacks |
| `order-service/.../OrderSagaWorkflow`, `FlowCommands` | The workflow and the emitters for its commands |
| `order-service/.../OrdersRoute`, `OrderService`, `SagaOutcomes` | The HTTP routes, the order table, and the two ways a saga ends, with the metric `saga.outcomes` |
| `inventory-service/.../StockRoute`, `ReservationService` | Commands, the saga join, replies, idempotent reserve and release |
| `payment-service/.../PaymentRoute`, `PaymentService` | Commands, the saga join, replies, take and refund |
| `*/src/main/resources/db/migration` | One schema per service; Flow's own migration runs from its jar |
| `compose.yaml` | Topics, the services and the coordinator from `_infra` |
