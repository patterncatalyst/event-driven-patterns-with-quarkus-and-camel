---
title: "Aggregate replay or CQRS projection"
order: 17
part: event-sourcing
permalink: /docs/17-aggregate-replay-or-projection/
description: "Operations asks a question the model was not built for: orders shipped per carrier per day. Replay every aggregate on request, or build a projection from the whole history and keep it current. What each costs, and why a checkpoint must not trust commit order."
duration: "55 minutes"
---

Operations asks a question nobody planned for: how many orders FASTLINE and AIRCO shipped each day. `order-service` has no table to ask. An order is its stream of events, and chapter 14 loads one stream at a time to decide a command. The answer is in the events, and there are two ways to get it: replay every `Order` aggregate when the question arrives, or build a table from the whole history now and keep it current.

{% include excalidraw.html file="17-replay-vs-projection" alt="Commands append events to orders.events; a replay reads every stream at the time of the question, folds each aggregate and counts the shipped ones; a projection reads the same events before the question through a projector with a checkpoint and keeps carrier_daily_shipments, which a query scans" caption="Figure 17.1: Two read paths over one store" %}

## Pattern: Projection rebuilt from history

### The problem

The write model answers one question well: may this command happen to this order. "Shipments per carrier per day" touches every stream, and nothing indexes it.

Replay answers it with no new moving part: read the events, fold each aggregate, count. It is correct by construction, because it uses the fold the commands use. Its cost is the size of the store, paid on every request. A snapshot from chapter 15 bounds the load of one aggregate and does nothing for a question about all of them.

The other answer is a second model for reading. Greg Young splits a service into a command side and a query side, and lets the query side be built from the events, denormalised and eventually consistent. Martin Fowler warns that this adds risky complexity to most systems. A projection is worth it only when the question is asked often enough, and a replay is slow enough, to justify a second model that can be wrong.

### The solution

**A projection is a read model built by folding the event history in order, kept current by applying each new event once, and discarded and rebuilt from the first event when its logic or its question changes.**

Three decisions make it safe. The events stay the only record: a projection is a cache of a fold, so losing it costs time and never data, and the Azure Architecture Center calls a materialized view completely disposable for that reason. The projector keeps a checkpoint, committed with the rows it produced, so a crash repeats nothing and loses nothing. And the checkpoint advances only past events that no late commit can precede, which has its own section below.

### How Quarkus models it

`OrderAggregate` is chapter 14's, reduced: it folds `OrderPlaced`, `OrderPaid`, `OrderShipped`, `OrderDelivered` and `OrderCancelled` into a status, a carrier and the instant of the shipment. An order cannot be cancelled after it ships, so one that reached SHIPPED or DELIVERED is a shipped order.

`ReplayReport` reads every event stream by stream and folds one aggregate at a time:

```java
try (var events = store.replayStream()) {
    events.forEach(event -> {
        if (!event.streamId().equals(current[0])) {
            count(order[0], counts);
            current[0] = event.streamId();
            order[0] = OrderAggregate.start();
        }
        order[0].apply(event.payload(), event.occurredAt());
    });
}
```

`replayStream()` is a Panache projection to a record. A stream of entities keeps every row in the persistence context until the transaction ends; a projection leaves nothing managed. The day of a shipment is its UTC day, in the replay and in the projection alike.

`CarrierDailyProjector` is a Quarkus scheduler method. Each tick runs batches until the events it may read are used up:

```java
@Scheduled(every = "${projection.poll}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
void tick() {
    int read;
    do {
        read = batch.run(batchSize);
    } while (read == batchSize);
    lag.set(batch.lagEvents());
}
```

`SKIP` stops a slow tick from overlapping the next in one instance, and no further, so `ProjectionBatch` also locks the checkpoint row with `PESSIMISTIC_WRITE`: a second instance waits and then reads past the first. One batch is one `@Transactional` method: lock the checkpoint, read up to `projection.batch` events, add the counts to `carrier_daily_shipments`, move the checkpoint.

The read is native SQL, because the `xid8` comparison and `pg_snapshot_xmin` are not mapped by the entity:

```sql
SELECT position, tx_id::text::bigint, type, payload->>'carrier', (occurred_at AT TIME ZONE 'UTC')::date
FROM orders.events
WHERE (tx_id, position) > (CAST(CAST(:t AS text) AS xid8), :p)
  AND tx_id < pg_snapshot_xmin(pg_current_snapshot())
ORDER BY tx_id, position
LIMIT :lim
```

The `events` table is chapter 14's with one column added, `tx_id xid8 NOT NULL DEFAULT pg_current_xact_id()`, the transaction that wrote the row. `GET /reports/shipments-per-carrier?source=replay` or `source=projection` serves either path; the projection answer also reports `lagEvents`, the committed events past the checkpoint, and a gauge of the same number leaves for Prometheus.

## The same question, two ways

Both paths return the same lines: a carrier, a UTC day and a count. A SQL query over the raw events, grouping the `OrderShipped` payloads of streams that were not cancelled, is the oracle for both. A replay that disagrees with it has a wrong fold; a projection that disagrees with the replay has a wrong projector or has not caught up.

A new question differs between them. A new replay is code, and the answer is exact at once. A new projection is code plus a rebuild from the first event, and until it has read them all it undercounts without saying so in the rows. `lagEvents` says so, and a rebuild is finished when it reaches 0.

## What each answer costs

Both are timed inside the service, so the HTTP round trip is not in the numbers. The store is loaded cumulatively to 1,000, 10,000 and 100,000 orders, which is 3,600, 36,000 and 360,000 events. At each size three loads are discarded and 11 are timed, alternating replay and projection read so that drift affects both. A rebuild is timed from the moment the projection is emptied to the moment the checkpoint reaches the newest event; at the two smaller sizes it is bounded by the one-second tick, and three runs gave 0.5 to 1.3 s. The database runs on the compose stack of an 8-core laptop, and each figure is the median of three runs from fresh containers.

{% include excalidraw.html file="17-cost-by-size" alt="Median read times: a replay takes 14 ms at 1,000 orders, 76 ms at 10,000 and 581 ms at 100,000; a projection read takes 0.67, 0.61 and 0.48 ms; a rebuild takes 1.0 s, 1.0 s and 27 s" caption="Figure 17.2: A replay grows with the store, a projection read does not" %}

| | Replay every aggregate | Projection |
|---|---|---|
| Read at 1,000 / 10,000 / 100,000 orders | 14 / 76 / 581 ms | 0.67 / 0.61 / 0.48 ms |
| Freshness | The committed state at the read | One scheduler tick, about 1 s at the default |
| A new question | Code only, exact at once | Code plus a rebuild: 1.0 / 1.0 / 27 s |
| Storage | None | 27 rows at every size, against an events table of 1.1 / 10 / 103 MB |
| What can go wrong | The read cost grows with the store | Lag, a commit-order gap, a projector bug repeated until a rebuild |

The replay grows in step with the store, at about 1.6 microseconds per event at 100,000 orders, while the projection read scans the same 27 rows at every size. At 1,000 orders the difference is 13 ms and a replay is the better choice, because there is nothing to keep correct. At 100,000 a dashboard that refreshes every few seconds pays more than half a second of database and CPU each time for an answer that changed by a handful of rows. The projection read is about 1,200 times faster.

Freshness is the projection's price. A command reaches the projection within a tick, and a run of 50 commands at the one-second default gave a median of 993 ms. A question that must see the command it just committed belongs to the replay or to the aggregate.

## Commit order is not position order

The obvious checkpoint is the last `position` read: `WHERE position > :last`. It loses rows without a trace.

A position is assigned when a row is inserted, and a commit happens later. Transaction A inserts position 41 and keeps working. Transaction B inserts 42 and commits. The projector reads 42 and stores it as its checkpoint. A commits, its row becomes visible at 41, behind a checkpoint that never goes back. The order A shipped is not counted, and the log shows no error.

{% include excalidraw.html file="17-commit-order-gap" alt="A position checkpoint reads B at 42 while A at 41 is still open, so A is skipped when it commits; a watermark checkpoint reads nothing past the oldest open transaction and counts both A and B once A commits" caption="Figure 17.3: A checkpoint by position skips a late commit; a watermark waits for it" %}

The batch query closes the gap with two conditions. Rows are read in `(tx_id, position)` order, and only those below `pg_snapshot_xmin(pg_current_snapshot())`, the lowest transaction id still running. Every transaction below that id has finished, so no row can appear later before the checkpoint. While A is open nothing past A is read and `lagEvents` stays at 1 or more. When A commits, both rows are read in the next batch.

The price is that one long transaction holds the projection back, and any open transaction in the database counts, an idle-in-transaction session included. The stall is visible, because the report shows `lagEvents` at 1 or more while the transaction is held.

## Build, run, observe

```bash
cd examples/17-aggregate-replay-or-projection
mvn -B verify
./verify.sh
```

The Maven build runs 16 tests against PostgreSQL from Dev Services: a replay against a history worked out by hand, UTC days, a projection equal to the replay after catch-up and in batches of three, a failed batch that rolls back its rows and its checkpoint, four overlapping ticks that count each event once, a rebuild, the timer, the commands and their refusals, a replay of 100,000 orders in a 256 MB heap, and the commit-order gap on two connections, with the watermark and with it off. `verify.sh` starts PostgreSQL, the LGTM stack and the service, loads the three sizes, and prints `22/22 checks passed`.

The checks read the effects:

- At each size the replay equals the SQL oracle, the projection reaches `lagEvents=0` within 10 seconds (60 at 100,000 orders) and equals the replay. NOTE lines print the medians, the rebuild time and the sizes.
- At 100,000 orders a projection read is at least 250 times faster than a replay (1,218 times in the measured runs). A rebuild starts with work left and a count below the replay, then catches up and equals it.
- A shipment is in the next replay at once and in the projection within 2 seconds. The median over 50 commands is 993 ms.
- `ShipOrder` for `O-1701` is held after its append with its transaction open. `O-1702` ships and commits, and the projection counts neither: `lagEvents` is at least 1, the total has not moved, and a replay already counts `O-1702`. When the hold ends the projection equals the replay with both counted.
- After a restart the projection resumes from its checkpoint and counts nothing twice. It holds at most 60 rows at every size.
- Prometheus holds `projection_lag_events`, and Tempo and Loki hold a trace and logs of `order-service`.

Change one thing and watch a check fail:

- Run `PROJECTION_WATERMARK=off ./verify.sh`. This is demo code: the batch reads by position alone. The projection moves past `O-1701`, counts `O-1702` and never counts `O-1701`. Check 16 fails, because the projection counted `O-1702` while `O-1701` was still open, and checks 17 and 18 fail because the projection stays one order short after the hold and after a restart. No other check fails.
- Run `PROJECTION_POLL=5s ./verify.sh`. A command reaches the projection within a tick, now 5 seconds, and check 14 fails and no other. The freshness median is 4,994 ms.
- With the stack up, `POST /demo/projection/rebuild` (demo code) empties the projection and rewinds the checkpoint. The projection report then shows a count below the replay and a `lagEvents` that falls to 0.

## When not to use it

When the question is rare or the history is small, replay. A report that runs once a day over 10,000 orders costs 76 ms, and a projection for it adds a table, a checkpoint, a timer and a rebuild to keep correct. Measure the replay against the budget of the read before building anything.

A projection serves the questions it was built for. A new question needs a new fold and a rebuild from the first event, so the store must keep that history for as long as such questions can arrive. A bug in the fold is repeated into the rows until the rebuild. The Azure Architecture Center advises serving the last complete view while a new one is built; here the projection undercounts for the length of the rebuild.

When a command must see its own write, read the aggregate: a projection is eventually consistent, and the gap is a tick plus a batch. Chapter 16 fills a read model from Kafka topics, where lag and the order between topics are the problems. Here the projector reads the event store itself, and the problem is commit order inside one database.

> **🤖 Explore with your agent**
> "In examples/17-aggregate-replay-or-projection, ask for shipments per carrier through ReplayReport and through CarrierDailyProjector, then find where projection_checkpoint and the tx_id watermark decide which events a batch reads. Set PROJECTION_WATERMARK=off, hold ShipOrder O-1701 open while O-1702 ships, and state which order the projection never counts and why."
> Check: with the watermark both orders are counted once the held transaction commits and the projection equals the replay; with it off the projection stays one order short.

## References

- Greg Young, [CQRS Documents](https://cqrs.wordpress.com/wp-content/uploads/2010/11/cqrs_documents.pdf), 2010: the command and query sides, and a read model built from the events
- Martin Fowler, [CQRS](https://martinfowler.com/bliki/CQRS.html), 2011, and [Event Sourcing](https://martinfowler.com/eaaDev/EventSourcing.html), 2005
- Microsoft Azure Architecture Center, [CQRS pattern](https://learn.microsoft.com/en-us/azure/architecture/patterns/cqrs) and [Materialized View pattern](https://learn.microsoft.com/en-us/azure/architecture/patterns/materialized-view): views regenerated by replaying history, and staleness while a view is rebuilt
- Alex Lawrence, *Message- and Event-Driven Systems with CQRS and Event Sourcing*, Addison-Wesley, 2026, chapters 5 and 6
- Adam Bellemare, *Building Event-Driven Microservices*, 2nd ed., O'Reilly, 2025, chapter 9: Deterministic Stream Processing
- Martin Kleppmann and Chris Riccomini, *Designing Data-Intensive Applications*, 2nd ed., O'Reilly, 2026
- Quarkus, [Scheduler reference](https://quarkus.io/guides/scheduler-reference) (`concurrentExecution`) and [Hibernate ORM with Panache](https://quarkus.io/guides/hibernate-orm-panache) (projections and `stream()`)
- PostgreSQL documentation, [Transaction ID and Snapshot Information Functions](https://www.postgresql.org/docs/18/functions-info.html) (`pg_current_xact_id`, `pg_current_snapshot`, `pg_snapshot_xmin`, `xid8`), [SELECT](https://www.postgresql.org/docs/18/sql-select.html) (`FOR UPDATE`) and [Set Returning Functions](https://www.postgresql.org/docs/18/functions-srf.html) (`generate_series`)
- Chapter 14 for the event store and the aggregate, chapter 15 for snapshots and chapter 16 for a read model fed from Kafka
