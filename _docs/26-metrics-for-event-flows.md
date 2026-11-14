---
title: "Metrics for event flows"
order: 26
part: observing-event-flows
permalink: /docs/26-metrics-for-event-flows/
description: "Every service reports healthy while orders wait in a paused relay or behind a stopped consumer. Four signals are read where the work waits: the outbox backlog in the replication slot, group lag at the broker, the age of an event from the time stamped when it happened, and dead letters as a rate. One provisioned alert watches the signal that grows while the flow is stuck."
duration: "60 minutes"
---

`order-service` accepts an order in a few milliseconds and answers 201. `inventory-service` has no errors and an idle thread pool. Kafka is up and the dead letter topic is empty. Every dashboard built from the services' own counters is green, and twenty orders have been waiting for a minute because the connector that reads the outbox is paused.

Nothing is wrong inside any one service. The work is stuck between them: in a replication slot, on a topic nobody reads, or in a queue of failures. A counter in a service sees the work that reaches it. Work that has not arrived has no service to count it.

{% include excalidraw.html file="26-where-each-signal-lives" alt="An order passes from order-service through the replication slot, Connect and the topic edp.orders.placed to inventory-service; the backlog is read at the slot, group lag at the topic and event age at the consumer, while the outbox table, always empty, reads zero in a stall" caption="Figure 26.1: Each signal is read where the work waits, not inside the service that is stuck" %}

## Pattern: Measure the flow, not the service

### The problem

A service's metrics describe the service: requests handled, errors, latency, queue depth of its own threads. An event flow also has places that belong to no service. The outbox sits between a commit and a topic (chapter 8). The topic sits between a producer and a group. A dead letter topic holds what a consumer gave up on (chapter 13). When any of them stops moving, the services on both sides stay healthy, because each one is waiting for the other.

The obvious readings fail in a specific way. The outbox table has no rows to count, because the extension deletes each row in the transaction that inserts it. Debezium's streaming metrics report the lag of the last event it read, which read TBD(spike: 41 ms) after a 25 second pause with an order waiting. A consumer's own lag metric disappears with the consumer. And the age of an event, measured on arrival, says nothing until something arrives.

### The solution

**Measure each stage of the flow where its work waits: the backlog at the relay, the lag at the broker, the age of an event from the time stamped when it happened, and dead letters as a rate, and alert on the signal that grows while the flow is stuck.**

Each signal answers a different question. Backlog says whether the relay is keeping up with commits. Lag says whether a group is keeping up with the topic. Age says how late events are when they arrive, and what a customer waited. Dead letters say how much work failed outright.

### How Quarkus models it

Micrometer is the API in the services and OpenTelemetry carries the result to Prometheus, so a gauge and a timer are ordinary Micrometer meters. The collector that ships with the LGTM image adds a receiver for the broker, and Grafana loads the dashboard and the alert from files.

## Backlog before the topic

Debezium reads a PostgreSQL replication slot, and the slot remembers how far the connector has confirmed. The distance between the current write position and that confirmed position is the work committed but not yet published, in bytes.

```java
@Singleton
public class OutboxBacklog implements MeterBinder {

    private static final String SLOT_LAG = """
            SELECT coalesce(pg_wal_lsn_diff(pg_current_wal_lsn(), confirmed_flush_lsn), 0)
            FROM pg_replication_slots WHERE slot_name = ?""";

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("outbox.backlog", this, OutboxBacklog::read)
                .baseUnit("bytes").tag("slot", slot).register(registry);
    }
    // read() runs SLOT_LAG on a pooled connection
}
```

A `MeterBinder` bean is bound to the registry at startup, and Prometheus holds the gauge as `outbox_backlog_bytes`. The table is the wrong place to look: with `remove-after-insert` it holds zero rows however many orders wait, and `BACKLOG_SOURCE=table` (demo code) swaps the query to count them, to show a gauge that stays flat through a stall.

The slot is noisy for reasons unrelated to orders. PostgreSQL writes WAL for every table, and the connector only confirms a position when it processes an event or a heartbeat. Two settings keep the reading near zero at rest: `heartbeat.interval.ms` on the connector, so a quiet slot still advances, and a one second offset flush on the Connect worker. At rest the gauge then reads TBD(spike: 576 B); twenty orders waiting behind a paused connector read about TBD(spike: 27 KB). The alert threshold sits between the two, at 16 KiB. Right after a start the first captured event, not the heartbeat, pulls the slot forward, so a warm-up order precedes any reading at rest.

## Lag at the broker

A consumer can report its own lag, and the metric is useful until the consumer stops. The last value then stays in Prometheus for the default lookback and no new one arrives, so a stopped group looks like a recent, healthy one. Lag is better read from the broker, which knows each group's committed position and each partition's end offset whether or not anyone is consuming.

The LGTM image's collector has a Kafka metrics receiver. It takes a second configuration file through an environment variable and runs in a pipeline of its own, so the pipelines that carry the services' OTLP data are untouched.

```yaml
receivers:
  kafka_metrics:
    brokers: ["kafka:9094"]
    scrapers: [brokers, topics, consumers]
    collection_interval: 5s
    topic_match: "^edp\\..*"
service:
  pipelines:
    metrics/kafka:
      receivers: [kafka_metrics]
      processors: [batch]
      exporters: [otlp_http/metrics]
```

The receiver's default interval is one minute; five seconds makes a stop visible in the time an operator is watching. The metrics arrive as `kafka_consumer_group_lag_ratio` per group, topic and partition, and `kafka_partition_current_offset_ratio` per topic and partition: the unit of these metrics is `1`, which the Prometheus conversion turns into a `_ratio` suffix. The receiver and its metrics are still marked beta and in development upstream.

With the consumer stopped, the group's lag at the broker equals the orders waiting, and the consumer's own `kafka_consumer_fetch_manager_records_lag_max` has no new sample. "Has no sample in the last 15 seconds" is a query of its own, `absent_over_time`, because the last value would otherwise be served for minutes.

## Event age, not record age

The age of an event is how long it has existed when it arrives. The order service stamps the time of the commit in the outbox row, and the connector's router places that column in a header.

```json
"transforms.outbox.table.fields.additional.placement": "type:header:eventType,timestamp:header:occurredAt"
```

The consumer subtracts it from the clock and also subtracts the Kafka record timestamp, into two timers.

```java
void record(ConsumerRecord<?, ?> record, Instant now) {
    recordAge.record(nonNegative(Duration.between(Instant.ofEpochMilli(record.timestamp()), now)));
    Header occurredAt = record.headers().lastHeader(OCCURRED_AT);
    if (occurredAt != null) {
        eventAge.record(nonNegative(Duration.between(Instant.parse(text(occurredAt)), now)));
    }
}
```

Behind a Debezium outbox the two agree. The router stamps the record with the commit time, not the time it read the change: with the connector paused for 30 seconds the record timestamp was the placement time plus TBD(spike: 78 ms). A stall at the relay therefore shows up in record age, which is what chapter 16's projection lag relies on, and event age adds nothing there.

They part when a record is written again. A dead letter keeps the original headers, so `occurredAt` travels with it, but a redrive publishes a new record and the broker stamps a new timestamp. The redriven record reads young by record age and old by event age.

{% include excalidraw.html file="26-event-age-redrive" alt="A record fails in inventory-service and goes to the dead letter topic with its headers; the redrive writes it again with a new record time and the same occurredAt, so record age reads small and event age reads the wait" caption="Figure 26.2: A redrive resets the record timestamp and keeps the event time" %}

A customer who waited an hour for a reservation did wait an hour, and event age says so. Record age says the order was young when it arrived the second time. Both timers use explicit buckets, because the OTLP export carries no percentile histogram of its own: `publishPercentileHistogram()` produced a single `+Inf` bucket, and `histogram_quantile` over it returns NaN.

## Dead letters as a rate

A dead letter is a failure already isolated, so what matters is how many arrive and what share of the orders they are. The end offset of the dead letter topic is a counter the broker keeps, and the same receiver exports it. The dashboard shows the count over five minutes and the ratio of dead letters to orders.

A dead letter that can be fixed is a redrive. The cause here is a SKU with no stock row. The operator adds the row, reads the dead letter topic, and publishes each record to `edp.orders.placed` with its `id`, `eventType` and `occurredAt` headers. The redriven orders reserve, and the dead letter topic keeps its records as the evidence of what failed.

## A dashboard and one alert

Grafana loads provisioning files at startup. The dashboard is a JSON file with six panels, each a Prometheus query on one of the signals above, and the alert is a rule file in Grafana's alerting directory.

```yaml
- uid: edp-outbox-backlog
  title: order flow stalled
  condition: C
  for: 20s
  data:
    - refId: A
      datasourceUid: prometheus
      model: { expr: "max(outbox_backlog_bytes)", instant: true }
    - refId: B
      datasourceUid: __expr__
      model: { type: reduce, expression: A, reducer: last }
    - refId: C
      datasourceUid: __expr__
      model: { type: threshold, expression: B, conditions: [{ evaluator: { type: gt, params: [16384] } }] }
```

The rule has the shape Grafana's file provisioning documents: a query, a reduction and a threshold, evaluated every 10 seconds. With the connector paused the state went Normal, Pending, Alerting, and Grafana's word for a firing alert is Alerting. It reached Alerting TBD(spike: 31.8 s) after the orders were placed, and Normal again TBD(spike: 8.4 s) after the resume.

The rule watches backlog because backlog is the signal that grows while the flow is stuck. Lag grows too, but only after records reach the topic, and a stalled relay never lets them. Age cannot be the alert: it is recorded when an event arrives, so a rule on it fires after the resume, when the stall is over. `ALERT_ON=age` (demo code) mounts that rule instead, to show it.

| Signal | Read at | What it misses | Use |
|---|---|---|---|
| Outbox backlog | Replication slot, one gauge | Anything after the topic | Alert |
| Group lag | The broker, per group and partition | A stalled relay; nothing reaches the topic | Alert per consumer that must keep up |
| Event age | The consumer, when an event arrives | A stall, until it ends | Graph, and the number a customer feels |
| Record age | The consumer, from the record timestamp | A redrive or any rewritten record | Graph |
| Dead letters | End offset of the dead letter topic | Failures that are retried or dropped silently | Alert on a rising share |

## Build, run, observe

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/26-metrics-for-event-flows
mvn -B verify
./verify.sh
```

Compose runs Kafka, PostgreSQL, Connect and the LGTM stack with the dashboard, the alert rule and the broker receiver loaded from files, plus `order-service` and `inventory-service`; the script stops the consumer, pauses the relay and redrives dead letters, reads each effect back from Prometheus, Grafana and the broker, and ends with `TBD/TBD checks passed`.

Try one change and rerun:

- `BACKLOG_SOURCE=table ./verify.sh` (demo code): the gauge counts table rows, stays at zero through the pause and the alert never fires.
- `ALERT_ON=age ./verify.sh` (demo code): the rule watches event age and fires only after the relay resumes.
- `KEEP=1 ./verify.sh`, then open the Order flow dashboard on `localhost:3000` and pause the connector again with `curl -X PUT localhost:8083/connectors/order-flow-outbox/pause`.

## When not to use it

A flow with a handful of events an hour has no stall worth a metric. A log line at each stage finds the one that went missing.

The backlog is a property of the slot, so it reads every change in the database, not only the outbox. A database with heavy unrelated writes raises the floor, and a threshold set at rest in one environment is wrong in another. Measure at rest first, and keep the heartbeat on.

Group lag measures records, not time. A hundred records of lag is nothing for a consumer that drains thousands a second and a long wait for one that handles a few a minute. Where lateness is the contract, alert on age after arrival or on lag divided by the consumption rate.

Alerts on all five signals teach people to ignore alerts. One that fires while the flow is stuck, and graphs for the rest, is enough.

> **🤖 Explore with your agent**
> "In examples/26-metrics-for-event-flows, pause the Debezium connector order-flow-outbox, place twenty orders through order-service, and watch outbox.backlog, the provisioned alert order flow stalled and the group lag of inventory-placed in Prometheus and Grafana. Resume, then add stock for SKU-26-LATE, redrive edp.orders.placed-dlq and compare order.flow.event.age with order.flow.record.age. Say which signal moved first and which one an alert should watch."
> Check: OutboxBacklog reports bytes above 16384 while the outbox table holds 0 rows, the alert reaches Alerting with no record on edp.orders.placed, and after a redrive FlowAge records a small record age and a large event age for the same order.

## References

- Debezium, [Outbox event router](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html) (additional field placement, event timestamp) and [connector for PostgreSQL](https://debezium.io/documentation/reference/stable/connectors/postgresql.html) (heartbeats, the replication slot and WAL)
- PostgreSQL, [`pg_replication_slots`](https://www.postgresql.org/docs/18/view-pg-replication-slots.html) and the [administration functions](https://www.postgresql.org/docs/current/functions-admin.html) (`pg_wal_lsn_diff`, `pg_current_wal_lsn`)
- OpenTelemetry Collector contrib, [Kafka metrics receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/v0.165.0/receiver/kafkametricsreceiver)
- Quarkus, [Micrometer](https://quarkus.io/guides/telemetry-micrometer) and [Micrometer to OpenTelemetry bridge](https://quarkus.io/guides/telemetry-micrometer-to-opentelemetry)
- SmallRye Reactive Messaging, [Receiving Kafka records](https://smallrye.io/smallrye-reactive-messaging/latest/kafka/receiving-kafka-records/) (the dead letter queue strategy)
- Grafana, [alerting file provisioning](https://grafana.com/docs/grafana/latest/alerting/set-up/provision-alerting-resources/file-provisioning/) and [alert rule states](https://grafana.com/docs/grafana/latest/alerting/fundamentals/alert-rule-evaluation/); [docker-otel-lgtm](https://github.com/grafana/docker-otel-lgtm) (custom dashboards, `OTELCOL_EXTRA_ARGS`)
- Adam Bellemare, *Building Event-Driven Microservices*, 2nd ed., O'Reilly, 2025, chapter 18: Handling Bad Data in Event Streams
- Chapter 8 for the outbox, chapter 9 for the connector, chapter 13 for the dead letter queue, chapter 16 for projection lag
