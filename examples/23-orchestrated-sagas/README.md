# 23 Orchestrated sagas

`order-service` runs the same order saga, order then stock then payment, under two orchestrators over the same participants: the Camel Saga EIP with a Narayana LRA coordinator, and a Quarkus Flow workflow with its state in PostgreSQL. `inventory-service` and `payment-service` take commands from `edp.inventory.commands` and `edp.payments.commands`, answer on the catalog topics and join the Camel saga when the command carries its header.

Quarkus, Quarkus REST, Camel Kafka, Camel Saga with the LRA binding, Quarkus Flow with its Hibernate persistence and CloudEvent messaging bridge, SmallRye Reactive Messaging, Hibernate ORM with Panache, the Debezium outbox extension with a Debezium connector, Flyway, PostgreSQL, SmallRye Health and OpenTelemetry to the LGTM stack. Topic names come from `Topics` in `examples/domain-model`.

## Services

| Service | Port | Purpose |
|---|---|---|
| `order-service` | 8080 | `POST /orders/camel` and `POST /orders/flow` start a saga (one `Order` JSON body, 202), `GET /orders/{id}` reports the status and the owning orchestrator |
| `inventory-service` | 8082 | Participant: ReserveStock and ReleaseStock |
| `payment-service` | 8084 | Participant: TakePayment and RefundPayment |
| `connect` | 8083 | Debezium Connect: one connector reads the outbox table of order-service and publishes `OrderCancelled` (`examples/_infra/compose-connect.yaml`, `connect/order-outbox.json`) |
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

`verify.sh` refuses to start while another stack is running, removes any earlier containers and volumes before it starts, and stops this stack on exit unless `KEEP=1`. The containers are capped at about 7.5 GB in all: the three services and the coordinator at 384 MB each, Kafka and PostgreSQL at 1 GB each, Connect and the LGTM stack at 2 GB each.

## What verify.sh does

1. Starts Kafka, PostgreSQL, Connect, the LGTM stack, the LRA coordinator and the three services, and registers the outbox connector once order-service has created the table. `topics-init` creates the seven topics with one partition each.
2. Places `O-2301` under Camel and `O-2311` under Flow with an amount of 100.00 and reads where each ends: PAID, no active saga at the coordinator, no workflow instance left, and the saga header or the CloudEvent attribute on every command and reply.
3. Places `O-2302` and `O-2312` with 900.00, which payment-service declines: both CANCELLED, stock released, one OrderCancelled each.
4. Stops payment-service and places `O-2303` and `O-2313`. After the time limit the coordinator cancels the first, and the workflow's listen task times out for the second. Starts payment-service and reads what the late TakePayment did.
5. Recreates order-service with a longer time limit while payment-service is stopped and one order of each form is waiting, then starts payment-service.
6. Places 100 orders under each orchestrator in one burst.
7. A trace in Tempo, logs in Loki and the compensated Flow sagas in Prometheus.

## Tests and checks in detail

The Maven build runs the three services against PostgreSQL and Kafka from Dev Services and no coordinator: 28 tests. order-service runs the Camel saga on Camel's in-memory saga service, which keeps a saga on the exchange that started it and does not carry it in a header. Test routes therefore end the saga on that exchange, and the Kafka leg of the header is checked by `verify.sh`. The tests cover completion, compensation, the time limit, a compensation run twice, a saga completed by `AUTO` before any reply (the bad outcome `MANUAL` prevents), the workflow's happy, declined, insufficient, unanswered-reservation and timed-out paths, an instance row while waiting and none after, and a reply that names no instance not waking one, a reply waking only the instance it names, and one outbox row for a cancelled order however often the cancel is called. The participants' tests cover commands on the topic, the replies, a release that is idempotent and commutative with the reserve, a refund before a take refused, and that only the coordinator's refusal of a join is handled as a refusal. `verify.sh` prints `33/33 checks passed`.

The checks read the effects:

- Both orchestrators reach PAID for the same input and CANCELLED for the same declined input, and the stock ends where the paid orders leave it.
- The Camel records carry `Long-Running-Action` on both commands and both replies, and the Flow records carry `flowinstanceid`, on the replies as `ce_flowinstanceid`.
- With payment-service stopped both orders are CANCELLED no earlier than the 20 s time limit and within 45 s of the order, the Camel order by the coordinator's cancel and the Flow order by the listen timeout, which also leaves RefundPayment and ReleaseStock on the topics (checks 15 and 16).
- After payment-service starts there is no payment for the Camel order, because the coordinator refused the join (HTTP 404) and the log says so, with the cause named, and a REFUNDED payment for the Flow order.
- After the recreate the Camel order is PAID, because the reply joined the saga through its header and the coordinator, which was not restarted, closed it. The Flow order is still PLACED with its instance row after the payment was taken. That is the observed behaviour, not a guarantee to rely on.

- Every reply is a CloudEvent: the workflow's consumer logs no parse failure over the whole run (check 33).
- One `OrderCancelled` per cancelled order reaches the topic through the outbox connector (check 12).

### Timing witnesses

Checks 15 and 16 assert that the cancel comes at or after the 20 s limit, and passed in all 29 runs made (each on a fresh stack). The Camel order was CANCELLED with its stock released 20.4 to 20.7 s after it was placed. The Flow check saw RefundPayment and ReleaseStock on the topics and the order CANCELLED 31.0 to 31.4 s after it was placed; it reads two topics per poll, so that time is an upper bound on the cancel, not its time.

### Bench

The burst of 100 orders per orchestrator, 29 runs: the last Camel order was PAID 3.7 to 3.8 s after the first was placed (median 3.75 s) and, in the 20 runs where every Flow order completed, the last Flow order 2.0 to 2.2 s after (median 2.03 s). The coordinator served 6 requests per Camel order and none for Flow. The figures include the time `verify.sh` takes to submit the 100 orders one request at a time. In the other 9 runs the Flow burst stalled (see the findings).

## Findings about the stack

- **The Camel LRA extension and Quarkus Flow cannot share the classic REST client.** `camel-quarkus-lra` brings the classic RESTEasy client and Quarkus Flow brings the reactive REST client, and the build fails on two producers of the same build item. `order-service` excludes the classic client from the LRA extension. Nothing needs it: the LRA binding calls the coordinator with the JDK HTTP client, and the HTTP surface is Quarkus REST.
- **The environment names are not `CAMEL_LRA_*`.** Camel Main reads those itself and aborts startup. The properties `camel.lra.coordinator-url`, `camel.lra.local-participant-url` and `camel.lra.callback-secret` take their values from `LRA_COORDINATOR_URL`, `LRA_PARTICIPANT_URL` and `LRA_CALLBACK_SECRET`. A blank URL switches the LRA service off, which the tests use.
- **The Flow consumer keeps `failure-strategy=ignore`.** Every reply carries CloudEvent attributes, with the instance id only when the command named one, so the consumer parses each record and finds no matching instance quietly. Before that change the replies of the Camel form carried none and each was logged at ERROR with a stack trace: 201 such lines in a `verify.sh` run, almost all from the 100-order Camel burst. The strategy stays so that a record from another producer is skipped and logged instead of stopping the channel (check 33 asserts the log holds none).
- **The `option()` values of the orchestrator's own callbacks** arrived empty with `header("orderId")` and arrived with `simple("${header.orderId}")` in a trial.
- **Quarkus Flow 1.3.0 drops a reply that arrives before the instance reaches its listen task.** The workflow sends a command in one task and waits for the reply in the next. Between the two the engine completes the first task and persists it, and an event consumed in that gap matches no waiting instance and is lost, with nothing logged. The instance then waits for ever (both waits have a time limit, but a stalled instance never reaches a wait that has a timer, so neither ends it), and the order stays PLACED with its instance row. Symptom: a burst of 100 Flow orders in which the last orders never reach PAID and check 29 fails after 180 s. It is not in the example's code: sending first and listening next is the order the Flow documentation's own example uses, and the example keeps it. Rate measured: `verify.sh` from a clean stack, 29 runs, 20 passed 33 of 33 and 9 failed check 29 alone (31 percent): in each failed run 99 of the 100 Flow orders were PAID and one of the first three (`B-f-001` to `B-f-003`) was PLACED with its instance row, no consumer lag, nothing logged. With the time limit on `awaitReserved` in place (two failed runs inspected), the stalled order was still PLACED minutes after the 20 s limit, no ReleaseStock was sent, the instance's last persisted task was `reserve` and no thread was running workflow code: the limit ends a wait that is running, not an instance that never reaches it. Conditions that raise it: with the order-service container held to 0.3 CPU after the Camel burst (`docker update --cpus 0.3 edp-order-service`) and the Flow burst submitted one request at a time, 25 and 18 of 100 orders stalled in two runs; the first orders of the burst, which run the workflow code cold, were the ones lost. In the first stalled order the command was written 753 ms after its instance row was created, the participant's reply was written 13 ms after the command, the consumer group had no lag, and the instance row still existed with no further task: the reply was read and not delivered. The burst alone on a fresh stack (100 orders one at a time, 300 orders 16 at a time) and eight Flow bursts after the Camel burst, all without the stale instance of the recreate scenario, did not stall in the runs made. A command that is sent again after a timeout would recover it (the participants answer a repeated command like the first); the example does not do that, because it would change the form shown.
- **A listen on the event type wakes every waiting instance.** `listen(toAny(...))` accepts any event of that type, whatever `flowinstanceid` it carries, so an accepted payment for one order completed the wait of another whose payment had been declined, and that order ended PAID. `replyTo` in `OrderSagaWorkflow` filters on `flowinstanceid` (`extensionByInstanceId`), and the Maven test `aReplyWakesOnlyTheInstanceItNames` fails without it. The Flow burst also finished in about 1.1 s before the filter, against about 2.1 s after, because replies completed other orders' waits.
- **The Flow restore does not resume a waiting instance.** After order-service was recreated with an instance waiting, the row stayed and the order was still PLACED 30 s after payment-service took the payment (check 27). It was the same with `FLOW_SCAN_INTERVAL=5s`: the log shows `Scheduling periodic workflow instance recovery scan every PT5S`, and check 27 passed in all four runs of the experiment, so the periodic rescan did not resume it either. `auto-restore` is on by default.
- **The coordinator loses an in-flight saga when it restarts.** With one saga active, `docker restart` of the coordinator made it list no sagas in 6 of 7 restarts, and nothing cancelled the order: in the trial with payment-service stopped and a 60 s time limit, 70 s after the order it was still PLACED with its stock RESERVED. The seventh restart compensated 5.0 s after the start. A recreate (object store lost) lost the saga too. The object store is on a volume, which a restart keeps. `verify.sh` never restarts the coordinator.
- **A payment already running when the time limit passes is compensated first and then completes.** With a payment in flight in a separate JVM, the coordinator called the refund 5.0 s into the saga, before the payment code had joined at 8.0 s; the payment then completed, answered, and `saga:complete` found no saga (HTTP 404). The join happens when the message enters the saga route, so a slow body runs after the compensation. Only a participant that never joined is protected by the refused join. No check covers the slow payment; it was measured once in a spike with a payment service made to answer late.
- **Flow persistence logs a foreign-key violation on a task row when a reply arrives for an instance that has ended.** The late TakePayment for the timed-out `O-2313` is answered after the instance is gone, the reply reaches the finished instance's listen, and Flow tries to write a task row for an instance row it already deleted: `task_info_entity` violates `fk_task_workflow_instance`, logged at WARN with a stack trace, once in a compose run. The order stays CANCELLED and check 20 passes. It appears in the compose run, at the moment payment-service reads its backlog, and in the Maven run before the instance filter, where one order's reply reached other, finished instances. It is not in the Maven run now.
- **The ordering of a late payment on the Flow path** relies on one partition per command topic and the key. With more partitions a refund could be read before the payment it undoes.

## Experiments

Each row changes a single thing and names the checks that fail.

| Switch | Effect | Failing checks | Measured result |
|---|---|---|---|
| `PAYMENT_JOIN=off ./verify.sh` | TakePayment runs outside any saga, so the late TakePayment for `O-2303` is not refused at the coordinator | 18 | 32/33. Only check 18 fails: payment-service logs no refusal of a join for `O-2303`. The other 32 pass, including the Camel orders reaching PAID |
| `FLOW_COMPENSATION=off ./verify.sh` | The workflow cancels `O-2313` and sends no RefundPayment or ReleaseStock: the payment is not refunded and the stock is not released | 11, 13, 16, 19, 20 | 27/33 in the run made. Check 11 (the declined `O-2312` released), check 16 (RefundPayment and ReleaseStock for `O-2313`), check 19 (its payment REFUNDED), and checks 13 and 20 (SKU-23 back at 18) fail; check 29 failed too, because the Flow burst stalled in that run (see the findings), so the effect of the switch alone is 28/33 |
| `FLOW_SCAN_INTERVAL=5s ./verify.sh` | A periodic rescan of the workflow store; whether a waiting instance then resumes after the recreate | none | 33/33 in the run made (the Flow burst of 100 finished 2.0 s after the first order). `O-2314` was still PLACED with its instance row 30 s after the payment (check 27 passes): the rescan does not resume it. The 29 failure of an earlier account of this row was the stalled burst, which this switch does not cause |
| `SAGA_TIMEOUT=PT5S ./verify.sh` | Both orchestrators cancel after 5 s while the checks expect 20 s | 15, 16 | 31/33. Checks 15 and 16 fail, the lower bound at work: the Camel order was cancelled 5.2 s after it was placed and the Flow order was first seen cancelled at 15.9 s, both before the 20 s the checks expect. The other 31 pass |

A saga without a time limit is not offered as a switch: Camel's Saga documentation says there is no default timeout on Sagas, so every saga here sets one.

## Layout

| Path | Purpose |
|---|---|
| `order-service/.../CamelOrderSagaRoute`, `CamelSagaRepliesRoute`, `CamelTakePaymentRoute`, `CamelCancelOrderRoute`, `CamelCompleteOrderRoute` | The Camel orchestrator, one route each: start, replies, the second step, the two callbacks |
| `order-service/.../OrderSagaWorkflow`, `FlowCommands` | The workflow and the emitters for its commands |
| `order-service/.../OrderResource`, `OrderService`, `OrderCancelledEvent`, `SagaOutcomes` | The Quarkus REST endpoints, the order table with the outbox event written in the cancelling transaction, and the two ways a saga ends, with the metric `saga.outcomes` |
| `inventory-service/.../StockCommandsRoute`, `ReserveStockRoute`, `ReleaseStockRoute`, `ReleaseCompensationRoute`, `StockReplyRoute`, `ReservationService` | Commands, the saga join, the compensation, replies, idempotent reserve and release |
| `payment-service/.../PaymentCommandsRoute`, `TakePaymentRoute`, `RefundPaymentRoute`, `RefundCompensationRoute`, `PaymentReplyRoute`, `PaymentService` | Commands, the saga join and its refusal, the compensation, replies, take and refund |
| `*/src/main/resources/db/migration` | One schema per service; Flow's own migration runs from its jar |
| `compose.yaml`, `connect/order-outbox.json` | Topics, the services, Connect and the coordinator from `_infra`, and the outbox connector |
