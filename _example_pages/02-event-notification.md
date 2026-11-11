---
title: "Event notification"
order: 2
permalink: /examples/02-event-notification/
description: "OrderPlaced names the order; notification-service fetches the facts from order-service, survives its absence and reads current state."
---

[`examples/02-event-notification/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/02-event-notification) runs two services. order-service publishes `OrderPlaced` with only the order id. notification-service consumes it, fetches the order over HTTP and stores a Notification. It accompanies [Event notification]({{ '/docs/02-event-notification/' | relative_url }}).

It uses Quarkus REST, Hibernate ORM with Panache, Flyway, PostgreSQL, Camel on Quarkus (Kafka and http) and OpenTelemetry to the LGTM stack.

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/02-event-notification
mvn -B verify
./verify.sh
```

{% include excalidraw.html file="02-fetch-back" alt="order-service publishes OrderPlaced with the order id; notification-service consumes it and fetches the order from order-service" caption="The event names the order; the consumer fetches the facts" %}

## What to observe

1. **The record carries only the id.** The record on `edp.orders.placed` has the order id as its key and `eventId`, `occurredAt` and `orderId` as its value.
2. **The notification holds fetched facts.** `GET /notifications?orderId=` on port 8082 returns the customer, amount and status read from order-service.
3. **One trace spans both services.** Grafana at `http://localhost:3000` shows a trace from the `POST /orders` request to the fetch in `notification-service`.
4. **A missing source holds the record.** With order-service stopped, no notification appears and the group's offset stays where it was. When order-service returns, the notification appears.
5. **The fetch reads current state.** An order cancelled before notification-service reads the event shows CANCELLED in its notification.

## Infrastructure

Kafka, PostgreSQL and the LGTM stack from `examples/_infra`, plus two application containers built from UBI 10 Containerfiles. The compose project is `edp-02`.
