# 26 Metrics for event flows

Orders wait in a paused relay or behind a stopped consumer while every service reports healthy. It measures the flow where the work waits: the outbox backlog in the replication slot, consumer group lag at the broker, the age of an event from the time stamped when it happened, and dead letters as a rate. A dashboard and one alert rule are provisioned from files.

Quarkus, Micrometer with OpenTelemetry export, SmallRye Reactive Messaging, Debezium outbox and Connect, Kafka, PostgreSQL and the LGTM stack. There is no Camel. Topic names come from `Topics` in `examples/domain-model`.

## Services

| Service | Port | Purpose |
|---|---|---|
| `order-service` | 8080 | `POST /orders` writes the order and `OrderPlaced` through the outbox. `OutboxBacklog` is the `outbox.backlog` gauge |
| `inventory-service` | 8082 | Reserves stock from `edp.orders.placed`; a failure goes to `edp.orders.placed-dlq`. `FlowAge` records `order.flow.event.age` and `order.flow.record.age` |

One connector, `order-flow-outbox`, reads `orders.outboxevent` with slot `edp_order_flow`. It stamps the outbox timestamp on each record as header `occurredAt` and sends a heartbeat every second that writes a row to `orders.heartbeat`; a Filter transform drops those rows before a topic. Both topics have one partition. The consumer group is `inventory-placed`; its session timeout is 10 s.

`compose-connect-flush.yaml` sets the Connect worker's offset flush to one second and `compose-lgtm-provisioning.yaml` adds three things to the LGTM container: `otelcol/kafka-metrics.yaml` (a `kafka_metrics` receiver in its own pipeline, through `OTELCOL_EXTRA_ARGS`), the dashboard `grafana/dashboards/order-flow.json` (uid `edp-order-flow`) and the alert rule from `grafana/alerting/`. Both files are merged over the files in `examples/_infra` with `include`, so nothing in `_infra` changed.

| Setting | Default | Effect |
|---|---|---|
| `BACKLOG_SOURCE` | `slot` | Demo code. `table` makes the gauge count rows of the outbox table, which are always zero (the negative witness) |
| `ALERT_ON` | `backlog` | Demo code. `age` mounts `grafana/alerting/age.yaml`, the same rule on event age (it does not fire during the pause) |
| `BACKLOG_SLOT` | `edp_order_flow` | The replication slot the gauge reads |

Metric names as Prometheus holds them: `outbox_backlog_bytes{slot}`, `order_flow_event_age_milliseconds_{bucket,count,sum}`, `order_flow_record_age_milliseconds_{bucket,count,sum}`, `kafka_consumer_group_lag_ratio{group,topic,partition}`, `kafka_partition_current_offset_ratio{topic,partition}`. The two age timers have explicit buckets at 1, 5, 30 and 120 s.

## Run

```bash
(cd ../domain-model && mvn -B -q install)
mvn -B verify        # PostgreSQL with wal_level=logical and Kafka from Dev Services
./verify.sh          # compose stack
KEEP=1 ./verify.sh   # leave the stack up; Grafana is on http://localhost:3000
docker compose down -v
```

`verify.sh` refuses to start while another stack is running, removes any earlier containers and volumes before it starts, and stops this stack on exit unless `KEEP=1`. It ends with `37/37 checks passed`.

## What verify.sh does

1. Starts the stack, registers the connector and checks that it is RUNNING, the consumer owns its partition, the dashboard and the alert rule (Normal) are loaded, and stock is seeded.
2. A warm-up order moves the slot forward; then the backlog gauge is under the 16384 byte threshold and Prometheus holds the broker's lag, its end offsets and the client's own lag metric.
3. Forty orders are reserved and the broker's lag is 0.
4. Stops inventory-service and places twenty orders: the broker's group lag reaches 20 while the client's lag metric has no sample for 15 s; after the restart lag is 0 again.
5. Three orders for a SKU with no stock row fail and go to the dead letter topic; its end offset is 3, each record keeps the `occurredAt` header and the share of dead letters to orders is above zero. After the stock row is added and at least 10 s have passed, the three records are written back to `edp.orders.placed` with their original headers: they reserve, and the two age timers split.
6. Pauses the connector and places twenty orders: the outbox table holds no rows, the backlog gauge is over the threshold, the alert is Alerting, and the topic's end offset does not move. After at least 35 s the connector resumes: the backlog falls, the alert returns to Normal, the orders reserve, and the twenty events are older than 30 s by event age and by record age.
7. A burst of 500 orders is reserved and the lag returns to 0. Every query of the provisioned dashboard returns a series, Tempo has an order-service trace and Loki has an inventory-service line.

Order ids are `O-2600` to `O-2690`, and `O-260000` to `O-260499` for the burst. Event times come from the clock of the run.

## Layout

| Path | Purpose |
|---|---|
| `order-service/.../OrderResource`, `OrderService`, `OrderPlacedEvent` | The order and its outbox event |
| `order-service/.../db/migration/V2__heartbeat.sql` | The heartbeat table the connector writes and captures |
| `order-service/.../OutboxBacklog` | The gauge: slot lag in bytes, or table rows as demo code |
| `inventory-service/.../OrderPlacedConsumer`, `ReservationService` | The consumer; a failure is nacked to the dead letter topic |
| `inventory-service/.../FlowAge` | Event age and record age timers |
| `connect/order-flow-outbox.json` | The connector with the `occurredAt` header, the heartbeat query and the Filter that drops heartbeat rows |
| `otelcol/kafka-metrics.yaml` | Broker metrics receiver, merged into the collector |
| `grafana/` | Dashboard, dashboard provider and the two alert rule files |
| `compose.yaml`, `compose-connect-flush.yaml`, `compose-lgtm-provisioning.yaml` | The stack and the two overrides |

## Tests and checks in detail

`mvn -B verify` runs 18 tests (10 in `order-service`, 8 in `inventory-service`).

- The gauge against a real logical replication slot on PostgreSQL with `wal_level=logical`: lag grows with writes and falls when the slot's consumer confirms; the table source stays at 0 while the slot falls behind; an unknown slot reads 0; the gauge is registered in bytes with the slot as a tag.
- The two timers on a registry of their own and a fixed clock: both ages agree for a record written when the event happened; a redriven record keeps its event age and has a small record age; a record without the header has no event age sample; a clock behind the record counts as zero; the buckets are explicit.
- The consumer against Kafka from Dev Services: a known SKU is reserved; an unknown SKU and a malformed record each land on the dead letter topic, and the dead letter keeps the original bytes and the `occurredAt` header.
- The provisioning files: the dashboard has its uid and six panels, every panel reads Prometheus and an exported metric, both alert rule files share one uid and title and watch the backlog and the event age, the collector file holds the receiver and its pipeline, and the connector's heartbeat query writes to a table that the migration creates, the connector captures and a Filter transform drops.

`verify.sh` reads effects: the broker's lag from the collector's metrics, the client's metric through `absent_over_time`, counts and buckets of the two timers as differences from a baseline, the alert state from Grafana's rules API and the connector's state from Connect. The redrive is written in `verify.sh` with the console consumer and producer, because a redrive is an operator action.

If the redrive contrast does not hold in a run (record age reads large for a redriven record), the checks that compare the two ages after the redrive are the ones to read first; the pause checks stand on their own.

## Experiments

Each run starts from clean containers. Both end at 35/37.

| Switch | Effect | Failing checks | Measured result |
|---|---|---|---|
| `BACKLOG_SOURCE=table ./verify.sh` (demo code) | The gauge counts table rows | 24, 25 | The gauge stays at zero through the pause, so check 24 (over the threshold) and check 25 (Alerting) fail; the other 35 pass |
| `ALERT_ON=age ./verify.sh` (demo code) | The rule watches event age | 25, 28 | Not Alerting within 60 s of the pause (check 25), and not Normal within 90 s of the resume (check 28); the other 35 pass |

## Measurements

- Alert witness: 15 fresh-container runs of the stack start, warm-up order, pause with 20 orders, resume, each 15/15 on the checks it holds (alert Alerting during the pause, no record on the topic, backlog under the threshold and alert Normal after the resume, 20 orders reserved). Alerting after the orders were placed: median 28.9 s, range 14.6 to 35.1 s. Backlog back under the threshold after the resume: median 5.2 s, range 4.2 to 6.3 s. Alert Normal after the resume: median 8.3 s, range 6.2 to 14.4 s in the 15 loops; later single runs took 3.2 to 16.5 s. A failure rate of 0 of 15. One loop reached Alerting 14.6 s after the orders were placed, too early for a rule that starts Normal (`for: 20s`, 10 s evaluation), so the rule was probably Pending from a checkpoint before the stall; the 15 loops predate the precondition below.
- Backlog: 384 to 632 bytes at rest (median 632); 24.9 to 29.4 KB with twenty orders paused (median 27.4 KB). With the heartbeat table a checkpoint lifted it to about 69 KB for about 4 s in an exploratory sampler run (`w26-explore2.raw`, then back to 600 B).
- Burst of 500 orders, three runs: group lag back to 0 after 12.0, 2.7 and 6.9 s (median 6.9 s); peak group lag 271 to 500; event age p95 21.3, 17.5 and 21.3 s (median 21.3 s). The peak of the backlog gauge over the burst is not reported: it varied from 384 B to 169,608 B between runs, depending on where a checkpoint fell.
- Redrive contrast (three redriven records, every run): record age under 5 s and event age over 5 s; the check passed in all seven full runs that reached it with the heartbeat table.
- Before the heartbeat table the backlog check after the resume failed (35/37): unconfirmed write-ahead log from inventory updates and checkpoints after the last order stayed at about 108 KB and the alert did not return to Normal. `heartbeat.action.query` on a captured table fixed it; the check numbering did not change.
- The precondition before the pause (the 26 arrivals counted by both age timers) now also requires the alert to be Normal, so a rule left Pending by an earlier checkpoint cannot reach Alerting early. It extends that precondition in place; no check was added or renumbered.

The gauge's meaning is unchanged: bytes of write-ahead log the slot has not confirmed. Because it counts every change in the database, a database with heavy unrelated writes needs the heartbeat table, and a threshold measured at rest in that environment.
