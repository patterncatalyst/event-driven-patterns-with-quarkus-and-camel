---
title: "Events, commands and documents"
order: 1
part: getting-started
permalink: /docs/01-events-commands-documents/
description: "Three message types and one storage decision: update a Consignment row in place, or append what happened and rebuild the state from the history."
duration: "35 minutes"
---

A Consignment moves from BOOKED to LOADED. One message says so, and one table has to change. The message type decides who is responsible for what, and the storage decision decides whether the previous status survives the change.

{% include excalidraw.html file="01-row-update-vs-event-append" alt="The same ChangeConsignmentStatus command applied to a consignment row, which overwrites the status, and to the consignment_event table, which gains a new version" caption="Figure 1.1: One command, two storage outcomes" %}

## Pattern: Event Message

### The problem

Other parts of the system need to know that a Consignment was loaded. If the booking service calls each of them, it must know who they are and wait for each answer. The fact is already true when anyone learns it; nothing about it can be refused.

### The solution

**An Event Message announces that something happened, named in the past tense, and the sender neither knows nor cares who reads it.**

Hohpe and Woolf define the pattern in *Enterprise Integration Patterns*: the sender reports a change, and receivers decide what to do about it. Fowler's "What do you mean by 'Event-Driven'?" separates the uses of an event: a bare notification, a message that carries enough state to avoid a callback, and a record of every change used as the source of truth. This chapter covers the last use. The first two are the subject of chapter 02.

### How Quarkus and Camel model it

`ConsignmentBooked` and `ConsignmentStatusChanged` are the events. Each is one immutable row in `consignment_event`, with the stream (`stream_id`, the Consignment id), its position in that stream (`version`), a `type`, a `jsonb` payload and `occurred_at`. Hibernate ORM with Panache maps the table as an immutable entity, so the persistence layer cannot issue an update for it even by accident. Camel carries the same events over Kafka from chapter 02 on.

## Pattern: Command Message

### The problem

Someone must be able to ask for a change and be told no. LOADED is only valid for a BOOKED Consignment, and the requester needs an answer, not an announcement.

### The solution

**A Command Message tells one receiver to perform a named action, in the imperative, and the receiver may refuse it.**

Hohpe and Woolf describe a command as an ordinary message whose content is an instruction. The receiver validates it against its own state. Only after it accepts does anything become a fact.

### How Quarkus and Camel model it

`ChangeConsignmentStatus` is a Java record with the Consignment id, the target `ConsignmentStatus` and an optional expected version. A REST endpoint builds it from the request and hands it to one CDI bean, which checks the move against the lifecycle: BOOKED, LOADED, IN_TRANSIT, CLEARED, DELIVERED, one step forward at a time. A refused command produces HTTP 409 and no event. In a Camel route the same record is the body of a message sent to a single endpoint.

## Pattern: Document Message

### The problem

A reader wants to know where a Consignment stands now. It does not want an instruction to perform and it does not want to replay a history.

### The solution

**A Document Message carries a complete piece of data and prescribes nothing about what the receiver does with it.**

Hohpe and Woolf use it for transferring data between systems. The sender does not care whether the receiver stores it, displays it or ignores it.

### How Quarkus and Camel model it

`ConsignmentDocument` pairs the shared `Consignment` record with its `ConsignmentStatus`. Quarkus REST serialises it as JSON from `GET /consignments/{id}`, and a second endpoint returns the same document rebuilt from events. Both return identical JSON, which is what makes the two storage models interchangeable for readers.

## Row or events for the same change

The command is identical in both models. What differs is what survives it.

The row model overwrites. `UPDATE consignment SET status = 'IN_TRANSIT'` is cheap, the current state is one primary key lookup, and the previous status is gone.

The event model appends. The handler loads the stream, replays it to find the current status, checks the transition and inserts `ConsignmentStatusChanged` with the next version. Writers that raced on the same stream collide on `UNIQUE (stream_id, version)`, so the database settles the race. A trigger on `consignment_event` raises on any `UPDATE` or `DELETE`, so the history cannot be rewritten even by a careless script.

{% include excalidraw.html file="01-replay-rebuilds-state" alt="A command is checked against the state found by replaying the stream, then a new version is appended; a reader folds the same stream into a ConsignmentDocument equal to the row" caption="Figure 1.2: Check against the replayed state, append, fold to read" %}

Replay is a left fold. Start with nothing, apply `ConsignmentBooked` to get the Consignment and its first status, then apply each `ConsignmentStatusChanged` to move the status. The result is the same `ConsignmentDocument` the row produces. The history endpoint returns a `TrackingHistory`-shaped list: the Consignment id and every status with its version and timestamp. Only the stream can produce it.

| Question | A row is enough | The history is the requirement |
|---|---|---|
| What is the status now? | Yes, one lookup | Yes, by replay or a maintained row |
| What was the status last Tuesday? | No | Yes, replay up to that time |
| Who or what moved it, and in which order? | Only with an audit table kept alongside | Yes, every fact is recorded in order |
| A dispute needs proof of past states | Audit table, trusted by convention | Append-only table, enforced by the database |
| Two writers change the same Consignment | Optimistic lock on the row | Unique stream version |
| Reads dominate and nothing asks about the past | Preferred | Adds replay cost for no benefit |
| New readers need to derive new views later | Not possible from past states | Replay the stream into the new view |

Use a row when the current state is the only question. Use events when the past is part of the requirement: audit, dispute, reconstruction or a view you have not designed yet. Many systems need both, and the example keeps both so each can be checked against the other.

## Build, run, observe

```bash
cd examples/01-crud-vs-events
mvn -B verify
./verify.sh
```

The Maven build runs six tests against PostgreSQL started by Dev Services, with `consignment` and `consignment_event` created by a Flyway migration. `verify.sh` starts PostgreSQL, the LGTM stack and the application with Docker Compose, books a Consignment, drives three status changes and prints `10/10 checks passed`. It checks that the row holds the latest status, the stream holds four events and the replayed document equals the row. It also checks that the history lists every status, an illegal move returns 409 and appends nothing, `UPDATE` on `consignment_event` fails in `psql`, and Tempo holds a trace for `crud-vs-events`. Open Grafana at `http://localhost:3000` with `KEEP=1 ./verify.sh` to see the request traces.

Change one thing and watch a guard fail:

- Make `Lifecycle.allows` return true. The illegal move returns 204 instead of 409 and the event count grows.
- Delete the trigger from the Flyway migration. The `UPDATE` on `consignment_event` succeeds and history can be rewritten.
- Send two commands with `expectedVersion` 1 for the same Consignment. The second gets 409, because version 2 already exists, and neither the row nor the stream changes.
- Remove the `append` call in `ConsignmentService.change`. The row advances while the stream stops, so the replayed document and the history fall behind the row.

## When not to use it

Event sourcing costs more than a row. Every reader of the current state either replays a stream or maintains a second, derived copy that can lag. Streams grow, so long-lived Consignments need snapshots. Events are permanent contracts: a payload shape written today must still be readable after the code changes, which is a problem chapter 18 addresses. Corrections are new events, never edits, so a wrong fact needs a compensating one. And the unique constraint settles only concurrent writers on one stream; it does not make a status change in another system atomic with this one.

Keep the row when nobody asks about the past, when the data is reference data, or when an audit table beside the row answers the questions that actually get asked.

> **🤖 Explore with your agent**
> "In examples/01-crud-vs-events, apply ChangeConsignmentStatus to a new Consignment through both models, then answer three questions: what was its ConsignmentStatus before the last change, can you answer it from the consignment row, and can you answer it from consignment_event? State the rule that says when a row is enough and when the history is the requirement, and name one Consignment question from the shipping domain that falls on each side."
> Check: the row answers only the current status; consignment_event answers every earlier status and its timestamp.

## References

- Gregor Hohpe and Bobby Woolf, *Enterprise Integration Patterns*, Addison-Wesley, 2003: [Event Message, Command Message and Document Message](https://www.enterpriseintegrationpatterns.com/)
- Martin Fowler, [Event Sourcing](https://martinfowler.com/eaaDev/EventSourcing.html)
- Martin Fowler, [What do you mean by "Event-Driven"?](https://martinfowler.com/articles/201701-event-driven.html)
- Alex Lawrence, *Message- and Event-Driven Systems with CQRS and Event Sourcing*, Addison-Wesley, 2026: Chapter 2, Anatomy of Message-Driven Systems, and Chapter 6, Event Sourcing
