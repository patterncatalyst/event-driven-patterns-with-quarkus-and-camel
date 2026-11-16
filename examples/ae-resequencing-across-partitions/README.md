# Appendix E: Resequencing across partitions

`order-service` reads `edp.shipping.scheduled`, `edp.shipping.in-transit` and `edp.shipping.delivered` in one consumer group, restores the order of the events with the Camel Resequencer, writes one row per applied event to PostgreSQL (the identity id is the order of application) and publishes each event to `edp.shipping.shipments`, keyed by order id. Chapter 4's per-order PostgreSQL buffer is the comparison, by reference; it is not run here.

Quarkus, Camel (Kafka, direct, Jackson), Hibernate ORM with Panache, Flyway, PostgreSQL, SmallRye Health and OpenTelemetry to the LGTM stack. Order ids are `O-40xx`.

## Service

| Service | Port | Purpose |
|---|---|---|
| `order-service` | 8080 | The resequencer. Health on `/q/health`; no business endpoint |

| Setting | Default | Effect |
|---|---|---|
| `RESEQUENCER` | `stream` | `stream`, `batch`, or `none` (demo code: apply as events arrive) |
| `SEQUENCE_BY` | `global` | `order` (demo code) drives the stream form with the per-order number |
| `RESEQUENCER_TIMEOUT_MS` | 2000 | Stream form: wait for a missing predecessor |
| `RESEQUENCER_CAPACITY` | 1000 | Stream form: events held before the consumer blocks |
| `RESEQUENCER_REJECT_OLD` | false | Stream form: a late event throws instead of being applied |
| `RESEQUENCER_BATCH_SIZE`, `RESEQUENCER_BATCH_TIMEOUT_MS` | 10, 3000 | Batch form |

The stream form needs a global sequence, stamped on every event by one sequencer (`globalSequence` in the payload). Offsets are committed by the Kafka client's auto-commit, the Camel Kafka default; the service does not change it.

## Run

```bash
(cd ../domain-model && mvn -B -q install)
mvn -B verify      # route tests on Dev Services for Kafka and PostgreSQL
./verify.sh        # compose stack, seven scenarios in one run
KEEP=1 ./verify.sh # leave the stack up; Grafana is on http://localhost:3000
docker compose down -v
```

## What verify.sh does

Events are produced to the three topics with scattered keys, by two concurrent producers, and the service is recreated between scenarios with the settings the scenario needs.

1. Stream form, global sequence: twelve events of four orders applied as 1 to 12; every order's events 1, 2, 3 in one partition of the shipments topic; the first release waits for the timeout.
2. A gap: 13 to 15 applied at once, 17 and 18 after the timeout, a late 16 applied last.
3. The per-order number: fewer events applied than sent, nothing logged.
4. `rejectOld`: the late event is rejected and logged.
5. Batch form: each batch sorted, one order inverted across the batch boundary.
6. Capacity 5 with a missing predecessor: the consumer's lag stays up and nothing is applied until the timeout, then all eleven leave in order.
7. A restart while five events are held and committed: only the missing event is applied afterwards.

## Tests and checks in detail

The Maven build runs three tests: the stream form on a scrambled global sequence, a gap with a late event, and the batch form with one order split across two batches. Kafka and PostgreSQL come from Dev Services.

`verify.sh` prints `TBD/TBD checks passed`. Checks 1, 2, 4 and 20 are preconditions and stop the run when they fail.

| Check | Description |
|---|---|
| 1 | order-service is live and its consumer owns all nine partitions (precondition) |
| 2 | the three input topics and the shipments topic have three partitions (precondition) |
| 3 | the schema holds the applied-events table and the migration history |
| 4 | the twelve events reached the service out of global order (precondition) |
| 5 | applied in global order 1 to 12 |
| 6 | on the shipments topic every order holds its events 1, 2, 3 in order |
| 7 | every order's events sit in one partition of the shipments topic |
| 8 | the first release waits for the timeout |
| 9 | the events after the gap are applied in order and 16 is absent |
| 10 | 13 to 15 are released at once, 17 and 18 wait for the timeout |
| 11 | the late event is applied when it arrives, after 18 |
| 12 | per-order number: some events are never applied and nothing is logged |
| 13 | `rejectOld`: the late event is rejected and logged |
| 14 | `rejectOld`: the five earlier events were applied in order |
| 15 | each batch of six is sorted by order and sequence |
| 16 | an order split over two batches is applied out of order |
| 17 | with the buffer full the consumer stops: records stay unconsumed, nothing is applied (sampled for six seconds) |
| 18 | all eleven released in order at the timeout |
| 19 | nothing was released before the timeout |
| 20 | five events held and committed, none applied (precondition) |
| 21 | after the restart only the missing event is applied |
| 22 | Tempo has a trace for order-service |
| 23 | Loki has logs for order-service |

Numbers are written when the live window has run: TBD.

## Experiments

| Switch | Effect | Failing checks | Measured result |
|---|---|---|---|
| `RESEQUENCER=none ./verify.sh` | Demo code: events are applied as they arrive | TBD(window), predicted 5, 6, 8, 9 | TBD |
| `SEQUENCE_BY=order RESEQUENCER_TIMEOUT_MS=500 ./verify.sh` | Demo code: the first scenario runs on the per-order number | TBD(window), predicted 5, 6, 8, 9 | TBD |

## Layout

| Path | Purpose |
|---|---|
| `ShipmentConsumeRoute` | Reads the three topics in one group |
| `ShipmentIntakeRoute` | Parses the event, sets `seq` and `sortKey`, selects the form |
| `StreamResequencerRoute`, `BatchResequencerRoute` | The two Camel Resequencer forms |
| `ShipmentApplyRoute` | Records the event, then publishes it keyed by order id |
| `ShipmentRecorder`, `AppliedShipment` | One row per applied event |
| `src/main/resources/db/migration` | `resequencing.shipment_applied` |
| `compose.yaml` | Topics (three partitions each), `order-service`, a 20 s stop grace period |
