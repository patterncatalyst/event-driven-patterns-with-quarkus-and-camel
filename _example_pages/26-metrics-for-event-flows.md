---
title: "Metrics for event flows"
order: 26
permalink: /examples/26-metrics-for-event-flows/
description: "An order service and an inventory service on one topic, with the outbox backlog, group lag at the broker, event age and dead letters measured where each can be seen, a provisioned Grafana dashboard and one provisioned alert."
---

[`examples/26-metrics-for-event-flows/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/26-metrics-for-event-flows) is two services, `order-service` and `inventory-service`, one Debezium connector and the LGTM stack with a dashboard and an alert rule loaded from files. It accompanies [Metrics for event flows]({{ '/docs/26-metrics-for-event-flows/' | relative_url }}).

It uses Quarkus, Micrometer with OpenTelemetry export, SmallRye Reactive Messaging, the Debezium outbox, Kafka and PostgreSQL. The collector in the LGTM image gets a second configuration that reads consumer group lag from the broker.

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/26-metrics-for-event-flows
mvn -B verify
./verify.sh
```

{% include excalidraw.html file="26-stall-timeline" alt="A paused connector: orders are placed, the backlog rises, the alert reaches Alerting, the consumer sees nothing; after the resume the backlog falls, the alert returns to Normal and events arrive old" caption="The relay stalls: only the backlog moves, and the alert fires while nothing reaches the topic" %}

## What to observe

1. **A backlog the table cannot show.** While the connector is paused the outbox table holds no rows and `outbox_backlog_bytes` rises.
2. **An alert that fires during the stall.** The rule `order flow stalled` reaches Alerting with no record on `edp.orders.placed`, and returns to Normal after the resume.
3. **Lag the stopped consumer cannot report.** With `inventory-service` stopped the broker's group lag equals the orders waiting, and the service's own lag metric has no new sample.
4. **Dead letters and a redrive.** Three orders for a SKU with no stock land on `edp.orders.placed-dlq`; after the stock is added and the records are written back, record age reads small and event age reads the wait.
5. **A dashboard from a file.** Every panel of `edp-order-flow` returns data.

## Infrastructure

Kafka, PostgreSQL, Connect and the LGTM stack from `examples/_infra`, merged with two small files of the example, plus the two services. The compose project is `edp-26`.
