---
title: "Events, commands and documents"
order: 1
part: getting-started
permalink: /docs/01-events-commands-documents/
description: "Three message types and one storage decision: update an order row in place, or append what happened and rebuild the state from the history."
duration: "35 minutes"
---

An order from the running example moves from PLACED to PAID. One message says so, and one table has to change. The message type says who decides whether the change happens. The storage choice decides whether the old status is still there afterwards.

{% include excalidraw.html file="01-row-update-vs-event-append" alt="The same ChangeOrderStatus command applied to an orders row, which overwrites the status, and to the order_event table, which gains a new version" caption="Figure 1.1: One command, two storage outcomes" %}

## Pattern: Event Message

### The problem

Other parts of the system need to know that an order was paid. If the order service calls each of them, it must know who they are and wait for each answer. By the time anyone hears that the order was paid, the payment has happened, so no receiver can refuse it.

### The solution

**An Event Message announces that something happened, named in the past tense, and the sender neither knows nor cares who reads it.**

Hohpe and Woolf's Event Message is a message for reliable, asynchronous event notification between applications. Fowler's "What do you mean by 'Event-Driven'?" separates four patterns that share the name: event notification, event-carried state transfer, event sourcing and CQRS. Part 1 covers the first two; the storage decision below is the third.

### How Quarkus models it

`OrderPlaced`, `OrderPaid`, `OrderShipped`, `OrderDelivered` and `OrderCancelled` are the events. Each is one immutable row in `order_event`, with the stream (`stream_id`, the order id), its position in that stream (`version`), a `type`, a `jsonb` payload and `occurred_at`. The payload is one record per event type under the sealed `OrderEventPayload`, so replay reads typed fields: `OrderPlaced` carries the order, `OrderCancelled` carries a reason. Hibernate ORM with Panache maps the table as an `@Immutable` entity: Hibernate never writes changes to a loaded event back to the table.

## Pattern: Command Message

### The problem

Someone must be able to ask for a change and be told no. PAID is only valid for a PLACED order, and the requester needs a yes or a no.

### The solution

**A Command Message tells one receiver to perform a named action, in the imperative, and the receiver may refuse it.**

Hohpe and Woolf describe a Command Message as a way to invoke a procedure in another application through messaging. The receiver owns the state, so it validates the command against it. Only after it accepts does anything become a fact.

### How Quarkus models it

`ChangeOrderStatus` is a Java record with the order id, the target `OrderStatus`, an optional expected version and, for a cancellation, a reason. A REST endpoint builds it from the request and hands it to one CDI bean, which asks `OrderStatus.canMoveTo` from the shared domain model: PLACED, PAID, SHIPPED, DELIVERED, one step at a time, and CANCELLED only from PLACED or PAID. A refused command produces HTTP 409 and no event.

## Pattern: Document Message

### The problem

A reader wants to know where an order stands now. It does not want an instruction to perform and it does not want to replay a history.

### The solution

**A Document Message carries a complete piece of data and prescribes nothing about what the receiver does with it.**

Hohpe and Woolf use it to transfer a data structure between applications; the receiver decides what, if anything, to do with the data.

### How Quarkus models it

`OrderDocument` pairs the shared `Order` record with its `OrderStatus`. Quarkus REST serialises it as JSON from `GET /orders/{id}`, and a second endpoint returns the same document rebuilt from events. Both return identical JSON, which is what makes the two storage models interchangeable for readers.

## Row or events for the same change

Both models receive the same command. They differ in what is kept after it runs.

The row model overwrites. `UPDATE orders SET status = 'SHIPPED'` is cheap, the current state is one primary key lookup, and the previous status is gone.

The event model appends. The handler loads the stream, replays it to find the current status, checks the transition and inserts the next event (`OrderShipped`, say) with the next version. Writers that raced on the same stream collide on `UNIQUE (stream_id, version)`, so the database settles the race. Triggers on `order_event` refuse `UPDATE`, `DELETE` and `TRUNCATE`: a row trigger for the first two and a statement trigger for the last, because a row trigger never fires for `TRUNCATE`. A script that issues any of the three is refused; only DDL, such as dropping the table or dropping or disabling a trigger, gets past them.

{% include excalidraw.html file="01-replay-rebuilds-state" alt="A command is checked against the state found by replaying the stream, then a new version is appended; a reader folds the same stream into an OrderDocument equal to the row" caption="Figure 1.2: Check against the replayed state, append, fold to read" %}

Replay is a left fold. Start with nothing, apply `OrderPlaced` to get the order and its first status, then apply each later event to move the status. The result is the same `OrderDocument` the row produces, including the amount: the `Order` record rounds it to two decimals once, so 19.999 is stored as 20.00 in the row, in the event and in the replay. The history endpoint returns a `StatusHistory`: the order id and every status with its version and timestamp. Only the stream can produce it.

| Question | A row is enough | The history is the requirement |
|---|---|---|
| What is the status now? | Yes, one lookup | Yes, by replay or a maintained row |
| What was the status last Tuesday? | No | Yes, replay up to that time |
| Who or what moved it, and in which order? | Only with an audit table kept alongside | Yes, every fact is recorded in order |
| A dispute needs proof of past states | Audit table, trusted by convention | Append-only table, triggers refuse UPDATE, DELETE and TRUNCATE |
| Two writers change the same order | Optimistic lock on the row | Unique stream version |
| Reads dominate and nothing asks about the past | Preferred | Adds replay cost for no benefit |
| New readers need to derive new views later | Not possible from past states | Replay the stream into the new view |

Use a row when the current state is the only question. Use events when the past is part of the requirement: audit, dispute, reconstruction or a view you have not designed yet. Many systems need both, and keeping both lets each be checked against the other.

## Build, run, observe

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/01-crud-vs-events
mvn -B verify
./verify.sh
```

The Maven build runs eleven tests against PostgreSQL started by Dev Services, with `orders` and `order_event` created by a Flyway migration. `verify.sh` starts PostgreSQL, the LGTM stack and the application with Docker Compose, places an order and moves it from PLACED to DELIVERED, then prints `19/19 checks passed`: the row, the stream, the replayed document and the history agree, and an illegal move, a duplicate order, an `UPDATE`, a `DELETE` and a `TRUNCATE` on the stream are all refused, and Tempo holds a trace for the service. Run `KEEP=1 ./verify.sh` and open Grafana at `http://localhost:3000` to see the request traces.

Change one thing and watch a guard fail:

- Make `Lifecycle.allows` return true. The illegal move returns 204 instead of 409 and the event count grows: `verify.sh` checks 11, 12 and 15 fail.
- Delete the `UPDATE` and `DELETE` trigger from the first Flyway migration. The `UPDATE` and the `DELETE` on `order_event` succeed and history can be rewritten: checks 14 and 16 fail.
- Delete the second Flyway migration. `TRUNCATE order_event` empties every stream: checks 17 and 18 fail.
- Remove the `appendEvent` call in `OrderService.change`. The row advances while the stream stops, so the replayed document and the history fall behind the row: checks 6, 8, 10, 12 and 15 fail.
- Send `PAID` and then `SHIPPED`, both with `expectedVersion` 1, for the same order. The second gets 409 because version 2 already exists, and neither the row nor the stream changes. Remove the unique constraint from the first migration and the second also gets 204, so two events share version 2.

## When not to use it

Event sourcing costs more than a row. Every reader of the current state either replays a stream or maintains a second, derived copy that can lag. Long streams need snapshots. Events are permanent contracts: a payload shape written today must still be readable after the code changes, and every consumer depends on it. Corrections are new events, never edits, so a wrong fact needs a compensating one. And the unique constraint settles only concurrent writers on one stream; it does not make a status change in another system atomic with this one.

Prefer a document over an event when the receiver needs the whole current state rather than one change. Keep the row when nobody asks about the past, when the data is reference data, or when an audit table beside the row answers the questions that get asked.

> **🤖 Explore with your agent**
> "In examples/01-crud-vs-events, apply ChangeOrderStatus to a new Order through both models, then answer three questions: what was its OrderStatus before the last change, can you answer it from the OrderRow, and can you answer it from the OrderEvent stream? State the rule that says when a row is enough and when the history is the requirement, and name one order question from the shipping domain that falls on each side."
> Check: the row answers only the current status; order_event answers every earlier status and its timestamp.

## References

- Gregor Hohpe and Bobby Woolf, *Enterprise Integration Patterns*, Addison-Wesley, 2003: [Event Message](https://www.enterpriseintegrationpatterns.com/patterns/messaging/EventMessage.html), [Command Message](https://www.enterpriseintegrationpatterns.com/patterns/messaging/CommandMessage.html) and [Document Message](https://www.enterpriseintegrationpatterns.com/patterns/messaging/DocumentMessage.html)
- Martin Fowler, 2005: [Event Sourcing](https://martinfowler.com/eaaDev/EventSourcing.html)
- Martin Fowler, 2017: [What do you mean by "Event-Driven"?](https://martinfowler.com/articles/201701-event-driven.html)
- Alex Lawrence, *Message- and Event-Driven Systems with CQRS and Event Sourcing*, Addison-Wesley, 2026: Chapter 2, Anatomy of Message-Driven Systems, and Chapter 6, Event Sourcing
