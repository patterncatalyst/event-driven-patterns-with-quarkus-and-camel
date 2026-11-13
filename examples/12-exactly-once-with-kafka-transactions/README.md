# 12 Exactly-once with Kafka transactions

`payment-service` reads `InventoryReserved` records from `edp.inventory.reserved` in batches and writes one `PaymentProcessed` per input to `edp.payments.processed`. The output records and the batch's consumer offsets are sent in one Kafka transaction (SmallRye `KafkaTransactions`, `transactional.id=payment-service-tx`). A comparison route, `CamelTransactedPaymentRoute`, does the same job with the Camel Kafka producer's `transacted=true`, which makes each output atomic but leaves the offsets outside the transaction.

Quarkus, SmallRye Reactive Messaging (Kafka), Camel Kafka, SmallRye Health and OpenTelemetry to the LGTM stack. Shared records and topic names come from `examples/domain-model`. There is no database: the guarantee covers Kafka to Kafka only.

## Service

| Service | Port | Purpose |
|---|---|---|
| `payment-service` | 8080 | Consumes the input topic; health on `/q/health/ready` |

| Setting | Default | Effect |
|---|---|---|
| `PAYMENT_MODE` | `smallrye` | `camel-transacted` sets the Quarkus profile that turns the SmallRye channel off and the Camel route on |
| `CRASH_MID_BATCH_AT` | empty | Demo code: the order id at which the JVM halts inside the open transaction |
| `TX_TIMEOUT_MS` | `60000` | `transaction.timeout.ms` of the transactional producer |

## Run

```bash
(cd ../domain-model && mvn -B -q install)
mvn -B verify       # batch, abort and offset tests against Dev Services
./verify.sh         # compose stack: commit, crash, restart, Camel comparison
KEEP=1 ./verify.sh  # leave the stack up; Grafana is on http://localhost:3000
docker compose down -v
```

`verify.sh` refuses to start while another stack is running, starts every run from fresh containers and volumes, and removes them on exit unless `KEEP=1`.

## What verify.sh does

1. Starts the stack; checks the service and both topics (checks 1, 2).
2. Produces `O-5001` to `O-5005` in one call: five committed payments, group offset 5, `CompleteCommit` (3 to 5).
3. Restarts the service with `CRASH_MID_BATCH_AT=O-5108` and produces `O-5101` to `O-5110` in one call. Exit 137, a partial batch visible to read_uncommitted, nothing visible to read_committed, group offset still 5 (6 to 9).
4. Restarts without the switch: ten orders once each within 30 seconds, the aborted copies in the log, group offset 15, `CompleteCommit` (10 to 13).
5. Comparison: runs the Camel route with the crash at `O-6008`. Outputs committed before the crash, offset behind, then duplicates after the restart and no order lost (14 to 18).
6. A trace in Tempo, logs in Loki and the transaction commit time of the producer in Prometheus (19 to 21).

Checks 6, 7, 15 and 16 are preconditions: the run stops if the crash or the partial write did not happen.

## Tests and checks in detail

The Maven build runs the service against a Kafka broker from Dev Services: five inputs give exactly five payments to a `read_committed` reader, an aborted transaction leaves its record in the log and out of the `read_committed` view, and the input offset is committed together with the output. `verify.sh` starts Kafka, the LGTM stack and the service, and prints `21/21 checks passed`.

The checks read the effects, not the exit codes:

- Five orders `O-5001` to `O-5005` produce exactly five payments for a `read_committed` reader, one per order, keyed by the order id, with payment id `P-O-5001` and so on, status `COMPLETED` and the amount of the input. The group `payment-service` has committed offset 5, and `kafka-transactions.sh describe` shows `payment-service-tx` as `CompleteCommit`.
- With the crash switch set for `O-5108`, ten orders `O-5101` to `O-5110` are produced in one burst. The container exits with 137, and a `read_uncommitted` reader sees the seven payments written before the crash in the recorded runs: the open transaction wrote them. A `read_committed` reader sees none of the ten, and the group is still at offset 5.
- After a restart without the switch, a `read_committed` reader sees all ten orders, each exactly once, within 30 seconds of the restart, and under 55 seconds after the crash was detected, so the transaction timeout of 60 seconds had not run out. In the recorded runs it took 14 seconds from the restart and 28 to 33 from the crash. The restarted producer fenced the old one and aborted its transaction, so nothing waited for the timer. A `read_uncommitted` reader sees the ten plus the seven aborted copies, and the group is at offset 15.
- The comparison runs the Camel route with the crash at `O-6008`. After the crash a `read_committed` reader already sees payments for some of `O-6001` to `O-6007`, and the group has not committed past `O-6008`. After the restart every order has a payment and at least one of the first seven has two committed; in the recorded run each of the seven had two.
- A trace and logs for `payment-service` reach Tempo and Loki, and Prometheus holds the transaction commit time of its producer.

## Experiments

Each row changes a single thing and names the checks that fail.

| Switch | Effect | Failing checks | Measured result |
|---|---|---|---|
| `PAYMENT_MODE=camel-transacted ./verify.sh` | The main phase runs on the Camel route. The Camel route uses another group and transactional id, and each exchange commits on its own, so a read_committed reader already sees the first seven payments while the service is down. The producer metric of check 21 belongs to the channel that is off, which commits no transaction in that mode. Checks 6 and 7 still pass, proving the crash and the partial write | 10, with 4, 5, 8, 9, 11, 12, 13 and 21 | 12 of 21 pass |
| `TX_TIMEOUT_MS=10000` with `CRASH_MID_BATCH_AT=O-5108`: produce the burst and leave the service down | `kafka-transactions.sh describe` shows the transaction `Ongoing`, and the coordinator aborts it. A `read_committed` reader sees nothing of the batch before or after, and a `read_uncommitted` reader still sees the seven written records. Watch the transaction with `docker exec edp-kafka /opt/kafka/bin/kafka-transactions.sh --bootstrap-server localhost:9092 describe --transactional-id payment-service-tx` | - | Aborted 16 seconds after the crash was detected, in each of two runs |
| Read the output with `kafka-console-consumer.sh` and no `--isolation-level` after the restart | The seven orders written before the crash appear twice: aborted records are in the log, and the default reader returns them | - | - |

## Layout

| Path | Purpose |
|---|---|
| `payment-service/.../PaymentProcessor` | The batch, the transaction and the sends |
| `payment-service/.../CamelTransactedPaymentRoute` | The comparison route (profile `camel-transacted`) |
| `payment-service/.../PaymentMapping` | The mapping the Camel route applies to each record |
| `payment-service/.../CrashSwitch` | Demo code: halts the JVM inside the transaction |
| `payment-service/.../InventoryReserved`, `PaymentProcessed` | Input and output records |
| `compose.yaml` | The two topics and the service |
