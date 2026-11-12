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
6. A trace in Tempo and logs in Loki (19, 20).

Checks 6, 7, 15 and 16 are preconditions: the run stops if the crash or the partial write did not happen.

## Experiments

- `PAYMENT_MODE=camel-transacted ./verify.sh` runs the first phases on the Camel route. Check 10 fails, with 4, 5, 8, 9, 12 and 13 (the Camel route uses another group and transactional id, and a read_committed reader sees seven payments while the service is down).
- `TX_TIMEOUT_MS=10000`: start the stack with `CRASH_MID_BATCH_AT=O-5108`, produce the burst and leave the service down. The coordinator aborts it about 16 seconds after the crash; a read_committed reader sees none of the batch throughout. Watch the transaction with `docker exec edp-kafka /opt/kafka/bin/kafka-transactions.sh --bootstrap-server localhost:9092 describe --transactional-id payment-service-tx`.
- Read the output with `kafka-console-consumer.sh` and no `--isolation-level` after the restart: the aborted copies appear.

## Layout

| Path | Purpose |
|---|---|
| `payment-service/.../PaymentProcessor` | The batch, the transaction and the sends |
| `payment-service/.../CamelTransactedPaymentRoute` | The comparison route (profile `camel-transacted`) |
| `payment-service/.../CrashSwitch` | Demo code: halts the JVM inside the transaction |
| `payment-service/.../InventoryReserved`, `PaymentProcessed` | Input and output records |
| `compose.yaml` | The two topics and the service |
