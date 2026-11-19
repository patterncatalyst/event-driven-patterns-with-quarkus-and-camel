---
title: "Tracing across async hops"
order: 25
part: observing-event-flows
permalink: /docs/25-tracing-across-async-hops/
description: "An order crosses a commit, a log reader and a topic, and none of them is an HTTP request. The trace context travels in the outbox row and the record header, Connect adds its own spans, and the consumer either continues the trace as a child or starts a new one with a link."
duration: "55 minutes"
---

A customer's order is placed through `POST /orders`, and an operator wants to see where it went. The request is a span with a trace id. The order then goes through a commit, the database log, Kafka Connect and a topic. It ends in `inventory-service` seconds later, in a different process, with no HTTP call between the two. Unless something carries the trace id along, the consumer starts a trace of its own and the picture ends at the commit.

One trace follows an order from the request to the stock reservation, through the outbox and the Debezium connector, in Tempo. Where the trace ends depends on one choice at the consumer.

{% include excalidraw.html file="25-context-carriers" alt="Five boxes in a row: the POST /orders server span, the outbox row with its outbox-write span, the log and Connect with db-log-write and debezium-read, the Kafka record on edp.orders.placed, and the consumer span in inventory-service; under each the place the trace context is written: the traceparent HTTP header, the tracingspancontext column, a span started from the log entry, the traceparent record header, and a child of the span in the header" caption="Figure 25.1: The context rides in a different place at every hop, and each relay has to copy it" %}

## Learning objectives

- Carry a W3C traceparent through an outbox row, a Debezium relay, and a Kafka record header
- Decide between a child span and a linked span when a consumer reads a published event
- Configure the OpenTelemetry Java agent on Kafka Connect without flooding Tempo with unrelated spans
- Read a trace waterfall to separate time inside services from time spent in the relay

## Why this matters

When an order stalls between a commit and a consumer, an operator watching only per-service dashboards sees that nothing happened at all. A support call about a missing stock reservation then turns into a manual trawl through logs in separate processes, instead of one search in a trace viewer. Without a carried trace context, every async hop is a dead end, and the cost falls on whoever is paged to explain where the order went.

## Pattern: Trace context in the message

### The problem

An HTTP call carries its trace in a request header, and the server reads it before it starts work. A message has no request to ride on. The producer finishes, the transaction commits, and a relay publishes the event later from another process. By the time a consumer reads it, nothing is waiting for an answer, and nothing ties its work to the request that caused it.

The outbox adds two hops that a plain producer does not have. The event is first a row in a table, written in the order's transaction, and is then read from the database log by Connect and published. A trace context that is only held in memory is gone after the commit. Each of those relays has to receive the context from the one before and pass it on. A relay that does not leaves the next hop with a new trace and no parent.

### The solution

**Write the trace context into the message itself, copy it at every relay, and let the consumer decide whether its work continues the trace as a child or starts a new one with a link.**

The context is the W3C `traceparent`: a version, a trace id, the id of the parent span and a flags byte. Every hop that reads it makes its span a child of the span it names, or links to that span. Debezium describes this for the outbox in its tracing documentation, and the OpenTelemetry messaging conventions describe the two consumer forms.

### How Quarkus models it

`order-service` sets the order id on the request span, then writes the order and the event in one transaction:

```java
@POST
public RestResponse<Void> place(Order order) {
    Span.current().setAttribute("order.id", order.id());
    service.place(order);
    return RestResponse.created(URI.create("/orders/" + order.id()));
}
```

With `quarkus-opentelemetry` on the classpath, the Debezium outbox extension does the carrying on the write side. It starts an `outbox-write` span as a child of the request span and stores the active context in the `tracingspancontext` column of the outbox row. The code that fires the event does not mention tracing:

```sql
CREATE TABLE orders.outboxevent (
    id                 UUID PRIMARY KEY,
    aggregatetype      VARCHAR(255) NOT NULL,
    aggregateid        VARCHAR(255) NOT NULL,
    type               VARCHAR(255) NOT NULL,
    "timestamp"        TIMESTAMPTZ NOT NULL,
    payload            VARCHAR(8000),
    tracingspancontext VARCHAR(256),    -- written by the extension
    topic              VARCHAR(255) NOT NULL
);
```

The column holds the context as text, not as a bare `traceparent`, and the connector reads it from there. The Debezium outbox extension's table has the column.

## Through the outbox and Debezium

Connect reads the row from the database log. The Outbox Event Router, given an OpenTelemetry SDK, starts two spans of its own: `db-log-write`, which starts at the time the change was written to the log, and `debezium-read`, which is the processing of the entry. It then puts a `traceparent` header on the Kafka record. Debezium's page calls it the context of the processing span; in the runs measured, its span id is that of `db-log-write`.

The published Debezium image carries the OpenTelemetry API and no SDK. Debezium's documentation says to add the OpenTelemetry Java agent to a Docker deployment, and to set `ENABLE_OTEL` so the tracing classes are put on the class path. A small init service copies the agent from Maven Central into a volume that exists for one run, and Connect loads it with `-javaagent`:

```yaml
connect:
  environment:
    - ENABLE_OTEL=true
    - KAFKA_OPTS=-javaagent:/otel/opentelemetry-javaagent.jar
    - OTEL_SERVICE_NAME=connect
    - OTEL_EXPORTER_OTLP_ENDPOINT=http://lgtm:4318
    - OTEL_PROPAGATORS=tracecontext
    - OTEL_INSTRUMENTATION_COMMON_DEFAULT_ENABLED=false
    - OTEL_INSTRUMENTATION_OPENTELEMETRY_API_ENABLED=true
```

The last two lines turn off the agent's default instrumentation, as Debezium's tracing page does for its own setup. The agent instruments Kafka producers, JDBC and the HTTP server by default. In Connect that replaced the router's `traceparent` with the context of a new trace named after the topic, so the consumer joined a trace that did not contain the order, and it flooded Tempo with Connect's own database and internal-topic traffic. With every instrumentation off and only the bridge to the OpenTelemetry API on, Connect sends the router's spans and nothing else. The connector configuration needs no tracing keys.

The context on the record is the one the router wrote:

```
traceparent: 00-<trace id>-<span id of db-log-write>-<flags>
```

Without the agent, Connect still publishes the event. The record has no `traceparent`, no Debezium span exists, and the consumer starts a new trace that nothing connects to the order.

## Parent or link

`inventory-service` reads `edp.orders.placed` through SmallRye Reactive Messaging. The Kafka connector reads the `traceparent` header and starts a consumer span named after the topic, as a child of the span in the header. The business work needs its own span. The consumer span has ended before a blocking handler runs, so an attribute set on the current span there is lost. The work goes into a method of a second bean:

```java
@WithSpan("reserve stock")
@Transactional
public boolean reserve(@SpanAttribute("order.id") String orderId, String sku, int quantity) {
    long taken = StockItem.update("quantityOnHand = quantityOnHand - ?1 where sku = ?2 and quantityOnHand >= ?1",
            quantity, sku);
    // ...
}
```

The annotation makes a span and the attribute puts the order id on it. A search by `order.id` then finds the request span and the reservation span in one trace.

The child form is not the only one. The messaging conventions say a process span should link to the message's creation context, and that for a single message it may use that context as its parent. A link says that this work was caused by that span without making it a part of the same trace. It fits work that serves many records, such as a batch, and work that runs later, such as a retry or a timer. A single record with a prompt consumer fits the parent form, which shows one waterfall.

Demo code turns the parent form off for the channel and starts the linked form:

```java
Context creation = W3CTraceContextPropagator.getInstance().extract(Context.root(), headers, HEADERS);
Span span = tracer.spanBuilder("process edp.orders.placed").setSpanKind(SpanKind.CONSUMER)
        .setNoParent().addLink(Span.fromContext(creation).getSpanContext()).startSpan();
try (Scope scope = span.makeCurrent()) {
    work.run();
} finally {
    span.end();
}
```

{% include excalidraw.html file="25-parent-or-link" alt="Two panels. Parent: POST /orders and db-log-write in trace A, and the consumer span in trace A as a child of db-log-write. Link: POST /orders and db-log-write in trace A, which ends there, and a process span with no parent in trace B with a dashed link back to db-log-write" caption="Figure 25.2: The same record gives one trace as a child and two traces as a link" %}

| | Parent | Link |
|---|---|---|
| Trace shape | One trace from the request to the consumer | The order's trace ends at Connect; the consumer starts its own |
| One record | The natural fit | Works, with two traces to open |
| A batch of records | One parent for many children is wrong | One span with a link to each record's context |
| Work later than the record | The trace grows for as long as the work takes | The work is its own trace and points back |
| Search by order id | Returns one trace | Returns two, and the link joins them |
| Semantic conventions | Allowed for one record | Recommended |

A record with no `traceparent` gives a root span in both forms. The link has nothing to point at, and the span has no link.

## What the trace shows and hides

{% include excalidraw.html file="25-trace-waterfall" alt="A waterfall of six spans: POST /orders and outbox-write in order-service, db-log-write and debezium-read in connect, and edp.orders.placed receive and reserve stock in inventory-service, with a dashed bar below marking the hop through the log and the relay" caption="Figure 25.3: One order as one trace; the gap before the consumer span is the time outside the services" %}

The consumer span is a child of `db-log-write`, and `debezium-read` is a leaf beside it. `db-log-write` starts when the outbox insert reaches the log, and lasts until Connect has read it. From the end of `outbox-write` to the start of the consumer span, the median over 100 orders was between about 250 and 770 ms across the logged bursts, with single orders from 176 to 829 ms, always under a second. Everything before the consumer span is time the services did not spend: the log, the relay, the topic and the consumer's poll. A trace of only the two services would show a quiet gap there, and a trace through Connect shows how much of it is Debezium.

The trace does not show why a gap is long. A stopped connector, a slow broker and a lagging consumer all appear as a longer distance between the same two spans. The trace says that the time passed and metrics at the relay and the broker say where.

It also shows only what carries the context. A trace is searchable by order id because both services set `order.id` on a span, and a record that bypasses the outbox, such as one produced by hand, arrives with no header and starts a new trace. Tempo itself takes some seconds to make a trace searchable after the request: about 30 s in every run measured. Searching right after the request returns nothing, and a script that queries Tempo has to poll.

## Build, run, observe

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/25-tracing-across-async-hops
mvn -B verify
./verify.sh
```

`verify.sh` places an order, finds its single trace in Tempo through TraceQL, walks the span ancestry from the request to the reservation, runs a burst of orders, and restarts inventory in link form. The Connect container downloads the agent on each fresh start. It prints `22/22 checks passed`.

Try one change and rerun:

- `CONNECT_TRACING=off ./verify.sh` (demo code): Connect has no agent, the record has no `traceparent`, and the reservation is a trace of its own.
- `CONSUMER_TRACE=link ./verify.sh` (demo code): the consumer starts a new trace linked to the order's, and the trace-shape checks fail.
- Remove `Span.current().setAttribute` from `OrderResource`: the request span no longer carries the order id, so the structural query that starts from it finds nothing, and the trace is still found through the reservation span.

## When not to use it

A system with one service has nothing to carry. A trace inside one process needs no context in a message, and an outbox row's `tracingspancontext` column is wasted storage there.

The context in a message is a coupling. A producer that sets the header and a consumer that continues it have agreed on a format, and an intermediary that rewrites headers, such as a mirror or a bridge that drops them, silently cuts the trace. Where messages cross an organisation, the consumer may not want to join the sender's trace at all, and the link form lets it keep its own.

A parent that spans a long delay misleads. A consumer that reads the record an hour later shows an hour-long trace whose middle is a queue, and a trace viewer will draw it as work. Link anything that is not a prompt continuation.

Running a Java agent in Connect adds a dependency to the relay, a download at start, and memory: Connect used 752 MiB after one burst of 100 orders, a single sample with no measurement of Connect without the agent. When the relay's own time is of no interest, a producer that copies the `traceparent` into a header itself reaches the same consumer with no change to Connect, and the trace is a single hop with a gap where Connect was.

## Common pitfalls

- Leaving the Java agent's default instrumentation on in Connect replaces the outbox router's traceparent with a new trace named after the topic, flooding Tempo with Connect's own traffic.
- Skipping the agent in Connect leaves records with no traceparent header, so the consumer starts an unrelated trace and the reservation cannot be found from the request.
- Using the parent form for work that runs long after the record, such as a retry or a timer, stretches the trace across the delay and a trace viewer draws the wait as work.

> **🤖 Explore with your agent**
> "In examples/25-tracing-across-async-hops, place O-2501 through OrderResource, read the tracingspancontext column and the traceparent header on edp.orders.placed, then query Tempo for span.order.id = O-2501 and print the span ancestry down to reserve stock. Run it again with CONSUMER_TRACE=link and say which spans moved to a second trace."
> Check: the ancestry runs POST /orders, outbox-write, db-log-write, the consumer span, reserve stock in one trace, and with CONSUMER_TRACE=link the order's trace ends at Connect and a second trace links to its db-log-write span.

## References

- Debezium, [Tracing](https://debezium.io/documentation/reference/stable/integrations/tracing.html): the spans, the `tracingspancontext` field and the OpenTelemetry Java agent for Connect
- OpenTelemetry, [Semantic conventions for messaging spans](https://opentelemetry.io/docs/specs/semconv/messaging/messaging-spans/): linking and parenting a process span
- W3C, [Trace Context](https://www.w3.org/TR/trace-context/): the `traceparent` header
- OpenTelemetry, [Java agent configuration](https://opentelemetry.io/docs/zero-code/java/agent/configuration/)
- Quarkus, [Using OpenTelemetry tracing](https://quarkus.io/guides/opentelemetry-tracing): messaging instrumentation and `@WithSpan`
- SmallRye Reactive Messaging, [Receiving Kafka records](https://smallrye.io/smallrye-reactive-messaging/latest/kafka/receiving-kafka-records/): the `tracing-enabled` channel setting
- Grafana Tempo, [HTTP API](https://grafana.com/docs/tempo/latest/api_docs/) and [TraceQL queries](https://grafana.com/docs/tempo/latest/traceql/construct-traceql-queries/): search and structural operators
- Strimzi blog, [Tracing in Apache Kafka: from OpenTracing to OpenTelemetry](https://strimzi.io/blog/2023/03/01/opentelemetry/)
- OpenTelemetry observability tutorial, [Context propagation across Kafka](https://patterncatalyst.github.io/otel-observability-tutorial/docs/13-context-propagation-kafka/): the same hop with a propagation toggle
- [Transactional outbox]({{ '/docs/08-transactional-outbox/' | relative_url }})
- [Log tailing with Debezium]({{ '/docs/09-log-tailing-with-debezium/' | relative_url }})
- [Retry and dead letter]({{ '/docs/13-retry-and-dead-letter/' | relative_url }})
