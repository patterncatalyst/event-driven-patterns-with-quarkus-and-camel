---
title: "Correlated logs and traces"
order: 27
part: observing-event-flows
permalink: /docs/27-correlated-logs-and-traces/
description: "An operator holds an order id, and the tools hold trace ids and log lines. The order id goes on every span and in the log context of every handler, the trace id goes on every log line, and a trace that a timer starts links to the trace it continues, so one question finds everything about one order."
duration: "55 minutes"
---

Someone phones to say that order O-2701 never got its payment. They have an order id. Tempo is indexed by trace id and Loki holds lines of text, and neither knows what an order is. The saga of chapter 22 crosses three services and a Debezium relay, and when a payment times out it crosses a second trace as well.

Three habits close the gap. The order id is set on every span and in the log context of every handler. The trace id is on every log line. A trace that a timer starts links to the trace it continues. With those three, a search by order id returns the trace and its logs, and a log line leads to its trace and back.

{% include excalidraw.html file="27-one-order-many-signals" alt="order-service, Connect, inventory-service and payment-service in a row inside one trace, each service marked with order.id and orderId, with arrows to Tempo, queried by span.order.id, and to Loki, queried by orderId, where every line holds the trace id" caption="Figure 27.1: A business id on every span and in every handler's log line, a trace id on every log line" %}

## Pattern: Correlation identifier across signals

### The problem

A trace id is made by the tracing library. It names one request tree, it is meaningless to the business, and nobody outside the system knows it. An order id is the opposite: customers, support and the database know it, and no tracing library does.

Three things break the link. The first is the handler that runs after a message arrives. Chapter 25 carries the trace context across the outbox, the relay and the topic, so the consumer continues the trace, but nothing records which order the trace is about unless the code says so. The second is time: an order that waits for a payment and is cancelled by a sweeper later is not one request any more, and the sweeper's work belongs to no trace that anyone started for the order. The third is the log line: one written with no trace id cannot be found from a trace.

### The solution

**Put the business identifier on every span and in the log context of every handler, put the trace identifier on every log line, and link any trace that a timer starts to the trace it continues.**

This is Hohpe and Woolf's Correlation Identifier used for something other than a reply: a token that travels with the work so that related things can be matched. Their pattern is written for request and reply; here the same idea is applied to traces and logs. The three keys have different jobs.

| Key | Set by | Finds | Where it is stored |
|---|---|---|---|
| Trace id | The tracing library | The spans of one request tree, and the log lines written inside it | Span, and a structured field on every log record |
| Order id | The handler | Every trace and every handler's log line about one order, however many trees it crossed | Span attribute `order.id`, log attribute `orderId` |
| Link | The code that starts a trace later | The trace that started the work | Link on the root span |

### How Quarkus and Camel model it

Every handler in the three services runs inside one small bean. It starts a span that carries the order id and puts the same id in the log context.

```java
@ApplicationScoped
public class Correlated {

    @WithSpan("handle event")
    public void step(@SpanAttribute("order.id") String orderId, Runnable work) {
        try (LogContext ignored = logContext(orderId)) {
            work.run();
        }
    }

    public LogContext logContext(String orderId) {
        MDC.put("orderId", orderId);
        return () -> MDC.remove("orderId");
    }
}
```

The listener calls it with the order id of the record: `correlated.step(record.value().orderId(), () -> saga.onReserved(...))`. The span has to be started here. SmallRye Reactive Messaging opens a receive span for the record and ends it before the handler runs, so `Span.current().setAttribute(...)` inside the handler writes to a span that has already ended and the attribute is lost. A `@WithSpan` method on a separate bean starts a child, and the attribute lands on that one. The HTTP endpoint and the Camel payment route go through the same bean, so the order id is set in one way in all three services.

Camel needs no extra code for the trace. The Camel Quarkus OpenTelemetry extension continues the context from the record header, and a `.log(...)` in the route is written with the active trace id. The route calls `paymentService`, which uses the same `Correlated` bean, so the log lines from inside the payment carry the order id too.

## A trace id in every log line

Two settings put the three ids on the console line, and a third sends the same fields to Loki.

```properties
quarkus.log.console.format=%d{HH:mm:ss} %-5p traceId=%X{traceId} spanId=%X{spanId} orderId=%X{orderId} [%c{2.}] %s%e%n
quarkus.otel.logs.enabled=true
```

The tracing extension puts `traceId` and `spanId` in the log context, and the handler puts `orderId`. The OpenTelemetry log exporter copies the context into the log record. In Loki the stream has one label, `service_name`; the trace id, the span id and `orderId` arrive as structured metadata, stored with the line and not indexed.

```logql
{service_name=~".+"} | orderId="O-2701"
{service_name=~".+"} | trace_id="4bf92f3577b34da6a3ce929d0e0e4736"
```

That choice leaves the index at the number of services. An order id as a label would index every order id and open a stream per order. As structured metadata it costs storage and a filter at query time, and it still answers the question: every line of an order, from every service, in one query. In Tempo the same id is a span attribute, found with `{ span.order.id = "O-2701" }`.

Not every line has the order id. The Camel route logs "taking the payment for O-2701" in its own message, before the handler has set the log context, so that line has the trace id and the id in its text but not the `orderId` field. The line from inside the payment has both. A query by trace id returns both lines. A query by `orderId` returns only the second. The library puts the trace id on every line. Only the code that knows the order puts the order id, so it is present where a handler ran and absent elsewhere.

## One order, more than one trace

A request, its relay and its consumers are one trace. A payment that never arrives makes a second one, because the sweeper in `order-service` runs on a timer, long after the request has finished.

{% include excalidraw.html file="27-timeout-two-traces" alt="Trace 1 started by POST /orders runs through the Connect relay and inventory to AWAITING_PAYMENT; trace 2 started by the sweeper after the deadline begins with cancel expired saga, which links back to trace 1, then outbox-write, the Connect relay and the release in inventory; a search by order id returns both traces" caption="Figure 27.2: Work that a timer starts is a new trace, linked to the one it continues" %}

There are two ways to connect the sweeper's work to the order. A parent would make the cancellation a child of the request span, so the request trace would stretch across the whole timeout, with a gap in the middle that shows nothing happening. The OpenTelemetry messaging conventions say a processing span should link to the creation context of its message, and may use it as its parent only for a single message handled at once. The cancellation is neither: it is later work started by a timer. A link keeps both traces short and says that one continues the other. The sweeper uses a link.

The saga stores what it needs to link when it is placed: the W3C `traceparent` of the request that placed it, in a column on `order_saga`. A timer that fires later has no active span, so it starts its own.

```java
SpanBuilder builder = tracer.spanBuilder("cancel expired saga").setNoParent().setAttribute("order.id", orderId);
if (placed.isValid()) {
    builder.addLink(placed);
}
Span span = builder.startSpan();
try (Scope scope = span.makeCurrent(); LogContext ignored = correlated.logContext(orderId)) {
    return cancel(orderId, from, CancelReason.TIMED_OUT);
} finally {
    span.end();
}
```

`placed` is the span context parsed back from the stored `traceparent`. The cancellation event is written to the outbox inside the scope, so the outbox span, the relay and the release in inventory all hang from the sweeper's root, and the order id is on the root. Quarkus can also make every scheduled run a trace of its own. That is off by default, and it makes a trace of every run, once a second here, so the sweeper starts a span only when it cancels a saga.

A search by `order.id` returns both traces for a timed-out order, and the link on the second leads to the first. Without the link the search still returns both, because the order id is on both.

| | Parent | Link |
|---|---|---|
| Trace of the work | The same trace as the request | A new trace |
| Fits | One message handled right after it was sent | Work started later, by a timer, or by one batch for many messages |
| Duration of the original trace | Stretches to the end of the later work | Unchanged |
| What the order id adds | Nothing, one trace | Finds both traces |

## From a log line to its trace, and back

Both directions are provisioned in the bundled Grafana, so no click needs configuring. The Loki data source has a derived field that turns the `trace_id` of a line into a link to the trace in Tempo. The Tempo data source has a trace-to-logs link that, from a span, runs a Loki query filtered on that `trace_id` and the span's service.

{% include excalidraw.html file="27-log-trace-roundtrip" alt="A payment-service log line in Loki with orderId and trace_id leads by the derived field to the trace in Tempo; a span of payment-service in Tempo leads by the trace-to-logs query to the lines of three services in Loki that share the trace id" caption="Figure 27.3: The trace id is the key in both directions" %}

From a ticket, search Tempo for `{ span.order.id = "O-2704" }` and open the first trace to read the path of the saga across the services. Follow the link on the sweeper's root to see what the cancellation continued. Jump from any span to the lines of that service for the same trace. Or start in Loki with `| orderId="O-2704"`, read the lines of both traces in time order, and open either.

A trace is not searchable the moment the request returns, so a query made straight after the request can come back empty. The order id is only as good as the code that sets it: a service that skips `Correlated` still has the trace id on its lines and spans, and a search by order id skips it.

## Build, run, observe

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/27-correlated-logs-and-traces
mvn -B verify
./verify.sh
```

`verify.sh` runs Kafka, PostgreSQL, Connect with the tracing agent, the LGTM stack and the three services, follows paid, declined and timed-out orders through Tempo and Loki, and ends with `31/31 checks passed`. The Connect container downloads the agent on each fresh start.

Try one change and rerun:

- `SWEEPER_LINK=off ./verify.sh` (demo code): the sweeper's trace has no link, and only the link check fails.
- `ORDER_ID_IN_LOGS=off ./verify.sh` (demo code): the lines have trace ids and no `orderId`, so the log queries by order id come back empty.
- `KEEP=1 ./verify.sh`, then in Grafana Explore run `{service_name=~".+"} | orderId="O-2704"`: the lines of both traces, each with a link to its trace.

## When not to use it

When there is one service and one request per trace, the trace id is enough. A business id on every span adds a line of code per handler and nothing a trace search by service and route would not find.

When the business id is not unique, it finds the wrong things. An order id that is reused, or an id that is shared by many customers, returns traces and lines that have nothing to do with each other. Use the id that names one unit of work.

When the id is personal data, putting it on every span and in every handler's log line spreads it across Tempo and Loki and every retention rule that applies to them. An opaque order number is fine. An email address is not.

Labels and metric dimensions are different from attributes. The order id as a Loki label or a metric label creates a series per order, which is the failure that structured metadata and span attributes avoid. Keep it out of both.

> **🤖 Explore with your agent**
> "In examples/27-correlated-logs-and-traces, place an order with POST /orders that payment-service will not answer, wait for SagaSweeper to cancel it, then find every trace with order.id equal to your order id in Tempo and every line with orderId equal to it in Loki. Show which trace links to which, and what changes when SWEEPER_LINK is off."
> Check: Tempo returns two traces for the order, the root span "cancel expired saga" of the second links to the trace that holds POST /orders through the traceparent stored in order_saga, every Loki line with the orderId carries the trace id of one of the two, and with SWEEPER_LINK=off the second root has no link.

## References

- Gregor Hohpe and Bobby Woolf, *Enterprise Integration Patterns*, Addison-Wesley, 2003, [Correlation Identifier](https://www.enterpriseintegrationpatterns.com/patterns/messaging/CorrelationIdentifier.html) (written for request and reply)
- W3C, [Trace Context](https://www.w3.org/TR/trace-context/) (the `traceparent` header)
- OpenTelemetry, [Semantic conventions for messaging spans](https://opentelemetry.io/docs/specs/semconv/messaging/messaging-spans/) (the creation context as a link, or as the parent of a single message)
- Quarkus, [Using OpenTelemetry Tracing](https://quarkus.io/guides/opentelemetry-tracing) (`@WithSpan`) and [OpenTelemetry logging](https://quarkus.io/guides/opentelemetry-logging)
- Quarkus, [Scheduler reference](https://quarkus.io/guides/scheduler-reference) (tracing of scheduled runs, off by default)
- SmallRye Reactive Messaging, [Receiving Kafka records](https://smallrye.io/smallrye-reactive-messaging/latest/kafka/receiving-kafka-records/) (`tracing-enabled`)
- Apache Camel, [OpenTelemetry 2](https://camel.apache.org/components/next/others/opentelemetry2.html)
- Grafana Loki, [OTLP ingestion](https://grafana.com/docs/loki/latest/send-data/otel/) and [structured metadata](https://grafana.com/docs/loki/latest/get-started/labels/structured-metadata/)
- Grafana Tempo, [TraceQL queries](https://grafana.com/docs/tempo/latest/traceql/construct-traceql-queries/); Grafana, [Loki data source](https://grafana.com/docs/grafana/latest/datasources/loki/configure/) (derived fields)
- Alex Lawrence, *Message- and Event-Driven Systems with CQRS and Event Sourcing*, Addison-Wesley, 2026, chapter 7: Long-Running Processes
- Adam Bellemare, *Building Event-Driven Microservices*, 2nd ed., O'Reilly, 2025, chapter 10: Building Workflows with Microservices
- Chapter 8: Transactional outbox, for the outbox span; chapter 22: Saga, for the sweeper and the compensation; chapter 25 for the trace context across the relay
