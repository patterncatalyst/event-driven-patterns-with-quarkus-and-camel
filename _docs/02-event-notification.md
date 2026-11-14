---
title: "Event notification"
order: 2
part: events-between-services
permalink: /docs/02-event-notification/
description: "An event that says only that an order was placed: how the consumer fetches the facts back from the source, what that costs when the source is down, and why it reads current state."
duration: "40 minutes"
---

An order is placed. The notification service has to tell the customer, and it knows nothing about orders: not who the customer is, not what they owe. The order service knows all of it, and does not know the notification service exists. The smallest event that connects them carries the order id and nothing else.

{% include excalidraw.html file="02-fetch-back" alt="order-service commits the order and publishes OrderPlaced with the order id; notification-service consumes it, fetches the order from order-service and stores a Notification" caption="Figure 2.1: The event names the order, the consumer fetches the facts" %}

## Pattern: Event Notification

### The problem

Order placement matters to other services. Inventory must reserve stock, a customer must be told, someone may want a metric. If order-service calls each of them, it must know who they are, wait for each answer and change whenever a new party appears. It wants to say "order O-1001 was placed" once and be done.

The other side has a matching problem. A consumer told only that something happened does not have enough to act on. It needs the customer, the amount, the item.

### The solution

**An Event Notification announces that something happened and names the thing it happened to, leaving every consumer to ask the source for the details it needs.**

Fowler's "What do you mean by 'Event-Driven'?" calls this event notification: the source sends the event and waits for no answer, and the event is small, often only an identifier a receiver can use to ask the source for more. Hohpe and Woolf's Event Message is the message form: it announces an occurrence and may carry little or nothing beyond that. The producer holds no list of consumers, and a new consumer is a new subscription, not a change to order-service.

order-service does not depend on its consumers, but each consumer still depends on order-service: it must call it for the details, and that call is where the costs below come from.

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
        .toF("kafka:%s", Topics.ORDERS_PLACED);
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
        .setBody(constant(null))
        .setHeader(Exchange.HTTP_METHOD, constant("GET"))
        .toD("{{order-service.url}}/orders/${header.orderId}")
        .unmarshal().json(FetchedOrder.class)
        .bean("notificationRecorder", "record(${header.orderId}, ${body})");
```
{% endraw %}

`toD` builds the URI per exchange from the order id. The base URL is a property, set in compose from `ORDER_SERVICE_URL`, so the route names no host. The Notification row holds the customer, the amount and the order's status as order-service reported them at that moment, and Hibernate ORM with Panache stores it in the `notifications` schema, apart from the `orders` schema that order-service owns.

`breakOnFirstError=true` changes what happens to a record whose route fails. Without it, a record whose route fails is skipped: the consumer moves on and the offset moves with it. With it, the consumer stops at the failed record and reads it again. After two retries one second apart the exchange fails, and the consumer polls the same record again. A fetch that cannot succeed because the source is down therefore waits, instead of losing the notification.

The OpenTelemetry integration for Camel carries the trace context in the Kafka record headers and in the HTTP request, so a single trace covers the request that placed the order, the publish, the consume and the fetch, across both services. Without that propagation the consume starts a trace of its own and the `POST /orders` span is not in it.

{% include excalidraw.html file="02-source-down" alt="While order-service is stopped the record stays uncommitted and is re-polled; when order-service returns the fetch succeeds and the offset moves on" caption="Figure 2.2: An unreadable source holds the record" %}

## Build, run, observe

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/02-event-notification
mvn -B verify
./verify.sh
```

Both services' tests run against PostgreSQL and Kafka from Dev Services. `verify.sh` places orders and stops each service in turn. Watch one Tempo trace follow the order request through the consume to the fetch; `KEEP=1 ./verify.sh` leaves Grafana at `http://localhost:3000`. It prints `21/21 checks passed`.

Try one change and rerun:

- Remove `breakOnFirstError=true` from the consumer route: the consumer commits the record it could not fetch, and that notification never appears.
- Send the event without the key header in `OrderPlacedPublisher`: the record is no longer keyed by the order id and the Maven tests that read the topic by order id fail.

## When not to use it

Use Event Notification when consumers need to know that something happened and can tolerate reading the details later. It fits best when the details change and the latest is what matters.

The cost is the call back to the source. While the source is down, the consumer cannot finish. Camel's documentation warns that a record bound to fail every time is read again endlessly with this option, so a record whose order will never exist stalls its partition, and a fetch that returns an error for a permanent reason needs its own handling.

Fowler also warns that a flow made of notifications is not explicit in any program text; a trace that spans both services shows the flow.

Every consumer that fetches adds one request to order-service for each event it reads.

A consumer reads current state, not the state at event time. A notification built after the order was cancelled says CANCELLED, which is right for a customer and wrong for an auditor who wants to know what the order looked like when it was placed. When consumers need the state as it was at event time, or the source cannot take the read load or answer reliably, carry the state in the event instead.

Publishing after the commit leaves a window: if the process stops between the commit and the publish, the order exists and the event does not. If the publish itself fails, the order can still be read from order-service and nothing reaches the topic, and nothing here publishes the event later.

> **🤖 Explore with your agent**
> "In examples/02-event-notification, stop notification-service, place an Order with order-service, cancel it, then start notification-service. Read the Notification from GET /notifications and compare its orderStatus with the status the OrderPlaced event announced. State the rule that says when a consumer may fetch current state and when the event must carry the state, and name one Order field each case needs."
> Check: the notification shows CANCELLED although the event was published while the order was PLACED.

## References

- Martin Fowler, 2017: [What do you mean by "Event-Driven"?](https://martinfowler.com/articles/201701-event-driven.html)
- Gregor Hohpe and Bobby Woolf, *Enterprise Integration Patterns*, Addison-Wesley, 2003: [Event Message](https://www.enterpriseintegrationpatterns.com/patterns/messaging/EventMessage.html)
- Alex Lawrence, *Message- and Event-Driven Systems with CQRS and Event Sourcing*, Addison-Wesley, 2026: Chapter 2, Anatomy of Message-Driven Systems
- Adam Bellemare, *Building Event-Driven Microservices*, Second Edition, O'Reilly, 2025: Chapter 2, Fundamentals of Events and Event Streams; Chapter 5, Designing Events; Chapter 17, Integrating Event-Driven and Request-Response Microservices
- Apache Camel, Kafka component documentation: [camel.apache.org/components/latest/kafka-component.html](https://camel.apache.org/components/latest/kafka-component.html)
