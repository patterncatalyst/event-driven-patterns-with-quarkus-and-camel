---
title: "Event-carried state transfer and claim check"
order: 3
permalink: /examples/03-event-carried-state-transfer/
description: "OrderPlaced and OrderCancelled carry the whole Order, notification-service keeps a local copy, and a large attachment travels as a claim."
---

[`examples/03-event-carried-state-transfer/`](https://github.com/patterncatalyst/event-driven-patterns-with-quarkus-and-camel/tree/main/examples/03-event-carried-state-transfer) has two services. order-service publishes the whole Order with each event; notification-service upserts `notifications.order_copy` and answers from it. It accompanies [Event-carried state transfer and claim check]({{ '/docs/03-event-carried-state-transfer/' | relative_url }}).

It uses Quarkus REST, Hibernate ORM with Panache, Flyway, Camel Kafka and OpenTelemetry to the LGTM stack.

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/03-event-carried-state-transfer
mvn -B verify
./verify.sh
```

{% include excalidraw.html file="03-local-copy" alt="OrderPlaced carries the whole Order to a local copy in notification-service, which answers while order-service is stopped" caption="State carried in the event, answered from a local copy" %}

## What to observe

1. **The copy answers on its own.** Stop order-service; `GET /order-copies/{id}` on port 8082 still returns 200 with every Order field.
2. **A cancellation is state too.** The copy of a cancelled order shows CANCELLED.
3. **Nobody asked the source.** Prometheus has no `GET /orders/{id}` series for order-service, and Tempo has no HTTP client span from notification-service.
4. **A thin event gives nothing to copy.** With `ORDER_EVENTS_CARRY_STATE=false` the consumer logs the event as skipped and the copy answers 404.
5. **A large attachment travels as a claim.** The event is under 1 KB; `GET /order-copies/{id}/attachment` fetches the 100 KB content from order-service on demand.
6. **The producer refuses an oversized event.** With `ORDER_EVENTS_ATTACHMENT=inline` the same attachment makes the POST return 502 and nothing reaches the topic.

## Infrastructure

Kafka, PostgreSQL and the LGTM stack from `examples/_infra`, plus two application containers built from UBI 10 Containerfiles. The compose project is `edp-03`.
