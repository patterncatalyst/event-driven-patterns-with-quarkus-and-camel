---
title: "Event notification"
order: 2
part: getting-started
permalink: /docs/02-event-notification/
description: "An event that says only that an order was placed: how the consumer fetches the facts back from the source, what that costs when the source is down, and why it reads current state."
duration: "40 minutes"
---

An order is placed. The notification service has to tell the customer, and it knows nothing about orders: not who the customer is, not what they owe. The order service knows all of it, and does not know the notification service exists. The cheapest event that joins them says which order, and nothing more.

{% include excalidraw.html file="02-fetch-back" alt="order-service commits the order and publishes OrderPlaced with the order id; notification-service consumes it, fetches the order from order-service and stores a Notification" caption="Figure 2.1: The event names the order, the consumer fetches the facts" %}

## Pattern: Event Notification

### The problem

Order placement matters to other services. Inventory must reserve stock, a customer must be told, someone may want a metric. If order-service calls each of them, it must know who they are, wait for each answer and change whenever a new party appears. It wants to say "order O-1001 was placed" once and be done.

The other side has a matching problem. A consumer told only that something happened does not have enough to act on. It needs the customer, the amount, the item.

### The solution

**An Event Notification announces that something happened and names the thing it happened to, leaving every consumer to ask the source for the details it needs.**

Fowler's "What do you mean by 'Event-Driven'?" calls this event notification: the source does not much care about a response, the event need not carry much data, often just an id and a link back, and a receiver that wants more asks the sender. Hohpe and Woolf's Event Message is the message form: it announces an occurrence, often with minimal contents, and the receiver decides what to do with it. The producer holds no list of consumers, and a new consumer is a new subscription, not a change to order-service.

The decoupling runs one way. The consumer is coupled to the source for as long as it needs the details, and that coupling is where the pattern's costs sit.

### How Quarkus and Camel model it

`OrderPlaced` is a record with an event id, a time and the order id. Nothing else about the order is in it.

order-service stores the order in one transaction and fires the event as a CDI event from inside it. A transactional observer receives the event only after the commit succeeded and hands it to a Camel route:

```java
void onPlaced(@Observes(during = TransactionPhase.AFTER_SUCCESS) OrderPlaced event) {
    producer.sendBodyAndHeader("direct:order-placed", event, KafkaConstants.KEY, event.orderId());
}
```

```java
from("direct:order-placed")
        .routeId("order-placed-publisher")
        .marshal().json()
        .to("kafka:" + Topics.ORDERS_PLACED);
```

The key is the order id, so every event about one order lands in one partition. Publishing after the commit matters to the consumer: it fetches the order the moment it reads the record, and a record published before the commit could be read before the row exists.

notification-service consumes the topic with a Camel Kafka route. After it unmarshals the event it calls order-service with the `http` component and stores what comes back:

{% raw %}
```java
errorHandler(defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(1000));

from("kafka:" + Topics.ORDERS_PLACED + "?groupId=notification-service&breakOnFirstError=true")
        .routeId("order-placed-notification")
        .unmarshal().json(OrderPlaced.class)
        .setHeader("orderId", simple("${body.orderId}"))
        .removeHeaders("kafka.*")
        .setBody(constant(null))
        .setHeader(Exchange.HTTP_METHOD, constant("GET"))
        .toD("{{order-service.url}}/orders/${header.orderId}")
        .unmarshal().json(FetchedOrder.class)
        .bean("notificationRecorder", "record(${header.orderId}, ${body})");
```
{% endraw %}

`toD` builds the URI per exchange from the order id. The base URL is a property, set in compose from `ORDER_SERVICE_URL`, so the route names no host. The Notification row holds the customer, the amount and the order's status as order-service reported them at that moment, and Hibernate ORM with Panache stores it in the `notifications` schema, apart from the `orders` schema that order-service owns.

`breakOnFirstError=true` changes what a failure means. Without it, a record whose route fails is skipped: the consumer moves on and the offset moves with it. With it, the consumer stops at the failed record and reads it again. After two retries one second apart the exchange fails, and the consumer polls the same record again. A fetch that cannot succeed because the source is down therefore waits, instead of losing the notification.

The OpenTelemetry integration for Camel carries the trace context in the Kafka record headers and in the HTTP request, so a single trace covers the request that placed the order, the publish, the consume and the fetch, across both services.

{% include excalidraw.html file="02-source-down" alt="While order-service is stopped the record stays uncommitted and is re-polled; when order-service returns the fetch succeeds and the offset moves on" caption="Figure 2.2: An unreadable source holds the record" %}

## Build, run, observe

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/02-event-notification
mvn -B verify
./verify.sh
```

The Maven build runs both services' tests against PostgreSQL and Kafka started by Dev Services. In notification-service the order-service is a test resource served on the test application's own port, so a test can make the source answer, refuse or report CANCELLED on demand. `verify.sh` starts Kafka, PostgreSQL, LGTM and both services with Docker Compose and prints `19/19 checks passed`:

- Placing an order publishes one record keyed by the order id whose value holds exactly the event id, the time and the order id.
- The notification holds the customer and amount that order-service holds, read through the fetch, and one Tempo trace spans both services.
- With notification-service stopped, a second order placed and then order-service stopped too, notification-service starts, tries the fetch, fails, and neither writes a notification nor moves the group's offset past the record. When order-service starts again the notification appears and the group has committed everything.
- A third order cancelled before notification-service reads its event yields a notification that says CANCELLED.

Run `KEEP=1 ./verify.sh` and open Grafana at `http://localhost:3000` to follow the trace from the order request to the fetch.

Change one thing and watch a guard fail:

- Remove `breakOnFirstError=true` from the consumer route. The consumer reads past the record it could not fetch and commits it, so the offset moves beyond B and B's notification never appears: `verify.sh` checks 12 and 14 fail, and the Maven test for a failed fetch fails on the committed offset.
- Send the event without the key header in `OrderPlacedPublisher`. The record is no longer keyed by the order id, and the three Maven tests that read the topic by order id fail.

## When not to use it

Use Event Notification when consumers need to know that something happened and can tolerate reading the details later. It fits best when the details change and the latest is what matters.

The coupling back to the source is the cost. While the source is down, the consumer cannot finish. Camel's documentation warns that a record bound to fail every time is read again endlessly with this option, so a record whose order will never exist stalls its partition, and a fetch that returns an error for a permanent reason needs its own handling.

Fowler also warns that a flow made of notifications is not explicit in any program text; the trace across both services is how it becomes visible here.

Every event costs the source a read. Ten consumers fetching the same order is ten requests, all arriving within moments of the event, and the load is highest exactly when order-service is busiest placing orders. A source that cannot take that load is a reason to carry the state in the event instead.

A consumer reads current state, not the state at event time. A notification built after the order was cancelled says CANCELLED, which is right for a customer and wrong for an auditor who wants to know what the order looked like when it was placed. When consumers need the state as it was at event time, or the source cannot take the read load or answer reliably, carry the state in the event instead.

Publishing after the commit leaves a window: if the process stops between the commit and the publish, the order exists and the event does not. The failed-publish case leaves the order readable and the topic empty, and nothing in this design repairs it.

> **🤖 Explore with your agent**
> "In examples/02-event-notification, stop notification-service, place an Order with order-service, cancel it, then start notification-service. Read the Notification from GET /notifications and compare its orderStatus with the status the OrderPlaced event announced. State the rule that says when a consumer may fetch current state and when the event must carry the state, and name one Order field each case needs."
> Check: the notification shows CANCELLED although the event was published while the order was PLACED.

## References

- Martin Fowler, 2017: [What do you mean by "Event-Driven"?](https://martinfowler.com/articles/201701-event-driven.html)
- Gregor Hohpe and Bobby Woolf, *Enterprise Integration Patterns*, Addison-Wesley, 2003: [Event Message](https://www.enterpriseintegrationpatterns.com/patterns/messaging/EventMessage.html)
- Alex Lawrence, *Message- and Event-Driven Systems with CQRS and Event Sourcing*, Addison-Wesley, 2026: Chapter 2, Anatomy of Message-Driven Systems
- Apache Camel, Kafka component documentation: [camel.apache.org/components/latest/kafka-component.html](https://camel.apache.org/components/latest/kafka-component.html)
