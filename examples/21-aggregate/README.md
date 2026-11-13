# 21 Aggregate

`shipping-service` builds pickup lists from `ShipmentScheduled` twice, on the same input in the same run. A Kafka Streams topology groups the shipments of each carrier into 15-minute slots of event time, holds each slot open for 5 more minutes of stream time for records that arrive out of order, and writes one final `PickupList` per carrier per slot to `edp.shipping.pickup-lists`. A Camel route groups the same shipments per carrier in arrival order, closing a batch at 5 shipments or after 10 seconds without a new one, and writes the same `PickupList` type to `edp.shipping.pickup-lists-camel`. A real service runs one of them; both run here so the difference is visible in one run.

Quarkus, Quarkus Kafka Streams (a `Topology` producer in the plain DSL), Camel Kafka, `camel-quarkus-sql` (`PostgresAggregationRepository` over the Agroal datasource), Flyway, SmallRye Health, PostgreSQL, Kafka and OpenTelemetry to the LGTM stack. Topic names come from `Topics` in `examples/domain-model`.

## Service

| Service | Port | Purpose |
|---|---|---|
| `shipping-service` | 8080 | Both forms of the pickup list. Health on `/q/health/ready` and `/q/health/live` (liveness follows the Kafka Streams state) |

| Setting | Default | Effect |
|---|---|---|
| `PICKUP_EMIT` | `final` | Demo code. `updates` removes the suppression, so every update of a list leaves, with `closedBy` set to `update` |
| `PICKUP_CHANGELOG` | `on` | Demo code. `off` disables the changelog topic of the window store, so a recreate loses the open window |
| `PICKUP_TIMEOUT_MS` | `10000` | The Camel batch timeout. `verify.sh` raises it to 60 s for the restart scenario |

The input record is `{"shipment": {...}, "occurredAt": "<instant>"}` with the shipment from `examples/domain-model`. `occurredAt` is the event time; the topology reads it with a `TimestampExtractor`. The console producer cannot set a record timestamp, so event time can only come from the payload.

## Run

```bash
(cd ../domain-model && mvn -B -q install)
mvn -B verify      # the topology on the Kafka Streams test driver, and both forms against Dev Services Kafka and PostgreSQL
./verify.sh        # compose stack
REPEAT=15 ./verify.sh  # also repeat the Camel batch scenario 15 times
KEEP=1 ./verify.sh # leave the stack up; Grafana is on http://localhost:3000
docker compose down -v
```

`verify.sh` refuses to start while another stack is running, removes any earlier containers and volumes before it starts, and stops this stack on exit unless `KEEP=1`. It ends with `TBD/TBD checks passed`. Event times are computed from the clock: yesterday's date, with the times of the scenario. Kafka Streams forwards event time as the timestamp of every record it writes, and the broker refuses a timestamp more than an hour ahead of its own clock, so a fixed or future date would stop the instance.

## What verify.sh does

1. Starts the stack, waits for liveness (Kafka Streams RUNNING) and for the Camel route to own its partition, and checks that the three topics have one partition each and that the `shipping` schema holds the two aggregation tables and the migration history.
2. Slot 09:00: 7 FASTLINE and 3 AIRCO shipments in one producer call. Camel closes FASTLINE at 5 (size) and 2 (timeout) and AIRCO at 3 (timeout). Kafka Streams has written nothing.
3. Out of order: a FASTLINE shipment stamped 09:16, then one stamped 09:10. Camel puts both in one batch. Kafka Streams still has no list, because stream time is 09:16 and the window of slot 09:00 stays open until 09:20.
4. The tick: an AIRCO shipment stamped 09:21. Kafka Streams has exactly one list per carrier for slot 09:00, FASTLINE with 8 shipments (the out-of-order one inside) and AIRCO with 3. The shipment stamped 09:16 is on no list. Both outputs hold the same shipment ids for the slot, each once.
5. Late: a FASTLINE shipment stamped 09:05. Camel puts it in a batch of its own, Prometheus holds `kafka_stream_task_dropped_records_total` of at least 1 for `shipping-service`, it is on no Kafka Streams list and the two lists are unchanged. The service is live and its log holds no ERROR.
6. Bench: 1,000 shipments over four slots in one producer call, then a tick. Kafka Streams has exactly 8 lists whose counts equal the oracle in `bench.py`; Camel has every id once and the carrier totals of the oracle. The run prints a `NOTE` line with the batches by size and by timeout, the time from the tick to the last list and the time from the producer call to the last batch.
7. Restart (slot 19:30): with the batch timeout raised to 60 s, three FASTLINE shipments grow the changelog topic and leave one open batch row. The service is recreated with an empty state directory, two more shipments and a tick arrive: the Kafka Streams list for the slot has all 5, and Camel closes one batch of 5 by size.
8. A trace in Tempo and logs in Loki.

With `REPEAT=n`, the 7 + 3 scenario of step 2 repeats n times (n up to 15) at later event times after the bench, and one extra line `PASS R` or `FAIL R` reports how many runs gave the expected batches. It is outside the numbered checks.

## Layout

| Path | Purpose |
|---|---|
| `shipping-service/.../ShipmentScheduled`, `HasEventTime`, `PayloadTimestampExtractor` | The input record and the event-time extractor |
| `shipping-service/.../PickupTopology` | The Kafka Streams topology: tumbling window with grace, suppression, one list per carrier per slot |
| `shipping-service/.../PickupBatchRoute` | The Camel route: aggregate per carrier, size or timeout, open batches in PostgreSQL |
| `shipping-service/.../PickupRepository` | The `PostgresAggregationRepository` bean over the Agroal datasource |
| `shipping-service/.../PickupList` | The output record both forms write |
| `shipping-service/src/main/resources/db/migration/V1__pickups.sql` | The two tables of the Camel aggregation repository |
| `bench.py` | The 1,000-shipment input and its oracle (the expected count per carrier and slot) |
| `compose.yaml` | Topics and the service |

## Tests and checks in detail

The Maven build runs 15 tests. Ten use the Kafka Streams test driver, with no broker: one final list per carrier per slot once the window closes, the window staying open through the grace and closing after it, an out-of-order record inside the grace landing in its own slot, a record after the close dropped and counted, every update leaving without the suppression, an idle stream emitting nothing however long the wall clock runs, the slot following the payload and not the record timestamp, one list per carrier, the names of the stores and the repartition topic, and hopping windows counting each shipment three times (comparison code). Five run the service against PostgreSQL and Kafka from Dev Services: a batch of 5 by size and the remainder by timeout, a batch never mixing carriers, the batch key being the carrier, the open batch being a row in PostgreSQL until it closes, and both forms on the same input.

`verify.sh` starts Kafka, PostgreSQL, the LGTM stack and the service, produces the shipments in the owners' record format, and prints `TBD/TBD checks passed`.

The checks read the effects:

- Both outputs are read as one line per record: carrier, slot, count, what closed it and the sorted ids, so a second list for the same slot shows up as a second line.
- Before the tick, the Kafka Streams output is empty. After it, it holds exactly the two lists of slot 09:00 and nothing else.
- The dropped record is proved by the Prometheus counter, not only by its absence from the output. The Micrometer binder rebinds the Kafka Streams meters about once a minute, so the check polls for up to 90 seconds.
- The restart scenario uses the changelog end offset and the row in `shipping.pickups` as preconditions, so a result is never an effect of nothing having been stored.
- Every recreate waits for liveness and for the Camel partition before the next record, and the outputs are polled, never slept on. The Kafka Streams consumer and the Camel consumer use a 10-second session timeout and a 3-second heartbeat, so a recreated instance takes its partitions back in seconds.

## Experiments

Each row changes a single thing and names the checks that fail.

| Switch | Effect | Failing checks | Measured result |
|---|---|---|---|
| `PICKUP_EMIT=updates ./verify.sh` (demo code) | Every update of a list leaves, so the output holds partial lists with `closedBy` set to `update` | TBD(expected: 5, 7, 8, 9, 10, 13, 15 and 19) | TBD |
| `PICKUP_CHANGELOG=off ./verify.sh` (demo code) | The window store has no changelog, so the recreate loses the three shipments of the open window and the list holds the two that came after | TBD(expected: 17 and 19) | TBD |

Repeated runs of the Camel batch scenario (`REPEAT=15`): TBD(spike: 15 of 15).
