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

`verify.sh` refuses to start while another stack is running, removes any earlier containers and volumes before it starts, and stops this stack on exit unless `KEEP=1`. The containers are capped at about 5.5 GB in all: the three services and the coordinator at 384 MB each, Kafka and PostgreSQL at 1 GB each, the LGTM stack at 2 GB.

## What verify.sh does

1. Starts Kafka, PostgreSQL, the LGTM stack, the LRA coordinator and the three services. `topics-init` creates the seven topics with one partition each.
2. Places `O-2301` under Camel and `O-2311` under Flow with an amount of 100.00 and reads where each ends: PAID, no active saga at the coordinator, no workflow instance left, and the saga header or the CloudEvent attribute on every command and reply.
3. Places `O-2302` and `O-2312` with 900.00, which payment-service declines: both CANCELLED, stock released, one OrderCancelled each.
4. Stops payment-service and places `O-2303` and `O-2313`. After the time limit the coordinator cancels the first, and the workflow's listen task times out for the second. Starts payment-service and reads what the late TakePayment did.
5. Recreates order-service with a longer time limit while payment-service is stopped and one order of each form is waiting, then starts payment-service.
6. Places 100 orders under each orchestrator in one burst.
7. A trace in Tempo, logs in Loki and the compensated Flow sagas in Prometheus.

## Tests and checks in detail

The Maven build runs the three services against PostgreSQL and Kafka from Dev Services and no coordinator: 25 tests. order-service runs the Camel saga on Camel's in-memory saga service, which keeps a saga on the exchange that started it and does not carry it in a header. Test routes therefore end the saga on that exchange, and the Kafka leg of the header is checked by `verify.sh`. The tests cover completion, compensation, the time limit, a compensation run twice, a saga completed by `AUTO` before any reply (the bad outcome `MANUAL` prevents), the workflow's happy, declined, insufficient and timed-out paths, an instance row while waiting and none after, and a reply without CloudEvent attributes not waking an instance, and a reply waking only the instance it names. The participants' tests cover commands on the topic, the replies, a release that is idempotent and commutative with the reserve, and a refund before a take refused. `verify.sh` prints `32/32 checks passed`.

The checks read the effects:

- Both orchestrators reach PAID for the same input and CANCELLED for the same declined input, and the stock ends where the paid orders leave it.
- The Camel records carry `Long-Running-Action` on both commands and both replies, and the Flow records carry `flowinstanceid`, on the replies as `ce_flowinstanceid`.
- With payment-service stopped both orders are CANCELLED within 45 s of the order, the Camel order by the coordinator's cancel and the Flow order by the listen timeout, which also leaves RefundPayment and ReleaseStock on the topics (checks 15 and 16, both with a 20 s time limit).
- After payment-service starts there is no payment for the Camel order, because the join was refused and the log says so, and a REFUNDED payment for the Flow order.
- After the recreate the Camel order is PAID, because the reply joined the saga through its header and the coordinator, which was not restarted, closed it. The Flow order is still PLACED with its instance row after the payment was taken. That is the observed behaviour, not a guarantee to rely on.

### Timing witnesses

Checks 15 and 16 were run 15 times, each on a fresh stack, and passed 15 of 15. The Camel order was CANCELLED with its stock released 20.7 to 20.8 s after it was placed (median 20.8 s). The Flow check saw RefundPayment and ReleaseStock on the topics and the order CANCELLED 32.3 to 36.5 s after it was placed (median 32.4 s); it reads two topics per poll, so that time is an upper bound on the cancel, not its time.

### Bench

The burst of 100 orders per orchestrator, three runs: the last Camel order was PAID 3.82, 3.83 and 3.84 s after the first was placed (median 3.83 s) and the last Flow order 2.07, 2.10 and 2.17 s after (median 2.10 s). The coordinator served 6 requests per Camel order and none for Flow. The figures include the time `verify.sh` takes to submit the 100 orders one request at a time.

## Findings about the stack

- **The Camel LRA extension and Quarkus Flow cannot share an application as published.** `camel-quarkus-lra` brings the classic RESTEasy client and Quarkus Flow brings the reactive REST client, and the build fails on two producers of the same build item. `order-service` excludes the classic client from the LRA extension; the reactive client provides the JAX-RS client API the LRA binding calls. The HTTP surface is a Camel `platform-http` route for the same reason.
- **The environment names are not `CAMEL_LRA_*`.** Camel Main reads those itself and aborts startup. The properties `camel.lra.coordinator-url`, `camel.lra.local-participant-url` and `camel.lra.callback-secret` take their values from `LRA_COORDINATOR_URL`, `LRA_PARTICIPANT_URL` and `LRA_CALLBACK_SECRET`. A blank URL switches the LRA service off, which the tests use.
- **The Flow consumer sets `failure-strategy=ignore`.** Replies of the Camel form carry no CloudEvent attributes, so each is skipped by the Flow consumer, and each skipped reply is logged at ERROR with a stack trace: about 200 such lines in a `verify.sh` run, almost all from the Camel-form replies of the 100-order burst.
- **The `option()` values of the orchestrator's own callbacks** arrived empty with `header("orderId")` and arrived with `simple("${header.orderId}")` in the spike.
- **A listen on the event type wakes every waiting instance.** `listen(toAny(...))` accepts any event of that type, whatever `flowinstanceid` it carries, so an accepted payment for one order completed the wait of another whose payment had been declined, and that order ended PAID. `replyTo` in `OrderSagaWorkflow` filters on `flowinstanceid` (`extensionByInstanceId`), and the Maven test `aReplyWakesOnlyTheInstanceItNames` fails without it. The Flow burst also finished in about 1.1 s before the filter, against about 2.1 s after, because replies completed other orders' waits.
- **The Flow restore does not resume a waiting instance.** After order-service was recreated with an instance waiting, the row stayed and the order was still PLACED 30 s after payment-service took the payment (check 27). It was the same with `FLOW_SCAN_INTERVAL=5s`: the log shows `Scheduling periodic workflow instance recovery scan every PT5S`, and check 27 passed in all four runs of the experiment, so the periodic rescan did not resume it either. `auto-restore` is on by default.
- **The coordinator loses an in-flight saga when it restarts.** In one trial, with payment-service stopped and a 60 s time limit, one Camel saga was active at the coordinator; after `docker restart` of the coordinator it listed no sagas, and 70 s after the order the order was still PLACED with its stock RESERVED: nothing cancelled it. Its object store is on a volume, which the restart kept. `verify.sh` never restarts the coordinator.
- **Flow persistence logs a foreign-key violation on a task row when a reply arrives for an instance that has ended.** The late TakePayment for the timed-out `O-2313` is answered after the instance is gone, the reply reaches the finished instance's listen, and Flow tries to write a task row for an instance row it already deleted: `task_info_entity` violates `fk_task_workflow_instance`, logged at WARN with a stack trace, once in a compose run. The order stays CANCELLED and check 20 passes. It appears in the compose run, at the moment payment-service reads its backlog, and in the Maven run before the instance filter, where one order's reply reached other, finished instances. It is not in the Maven run now.
- **The ordering of a late payment on the Flow path** relies on one partition per command topic and the key. With more partitions a refund could be read before the payment it undoes.

## Experiments

Each row changes a single thing and names the checks that fail.

| Switch | Effect | Failing checks | Measured result |
|---|---|---|---|
| `PAYMENT_JOIN=off ./verify.sh` | TakePayment runs outside any saga, so the late TakePayment for `O-2303` is not refused at the coordinator | 18 | 31/32. Only check 18 fails: payment-service logs no refusal for `O-2303`. The other 31 pass, including the Camel orders reaching PAID |
| `FLOW_COMPENSATION=off ./verify.sh` | The workflow cancels `O-2313` and sends no RefundPayment or ReleaseStock: the payment is not refunded and the stock is not released | 11, 13, 16, 19, 20 | 27/32. Check 11 (the declined `O-2312` released), check 16 (RefundPayment and ReleaseStock for `O-2313`), check 19 (its payment REFUNDED), and checks 13 and 20 (SKU-23 back at 18) fail; the other 27 pass |
| `FLOW_SCAN_INTERVAL=5s ./verify.sh` | A periodic rescan of the workflow store; whether a waiting instance then resumes after the recreate | none in 3 of 4 runs; 29 in the fourth | 32/32 in three runs (the Flow burst of 100 finished 2.06 to 2.08 s after the first order). In the fourth run 31/32: check 29 failed because the Flow burst had not completed 100 orders after 181 s, and the cause was not investigated. In all four runs `O-2314` was still PLACED with its instance row 30 s after the payment (check 27 passes): the rescan does not resume it |

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
