---
title: "Snapshots"
order: 15
part: event-sourcing
permalink: /docs/15-snapshots/
description: "A stream that grows without bound makes every load slower. A snapshot is a cache of the fold at one version: load it, replay only the events after it, and rebuild it from the events whenever it is wrong."
duration: "50 minutes"
---

`inventory-service` keeps the stock of a SKU as a stream of `StockReceived` and `StockReserved` events, and `SKU-1503` is the popular one: 100,000 events and still growing. Every reservation loads the stream, folds it into the aggregate, decides and appends one event. The first load of the day reads all 100,000 rows. So does the second, and the load after a restart, and the load for a `GET`. The stream is correct, and the cost of reading it grows with every sale.

{% include excalidraw.html file="15-snapshot-plus-tail" alt="A stream of events ordered by version; a snapshot holds the fold of versions 1 to s, so a load reads one snapshot row and the events after s, where a load without it reads every event from version 1" caption="Figure 15.1: A load reads the snapshot and the events after it" %}

## Pattern: Snapshot

### The problem

An aggregate's state is the fold of its events, so the work to load it is proportional to the length of its stream. Most streams stay short: an order has a handful of events. A ledger does not. Stock, an account or a counter takes an event for every change, and the oldest events still count in the answer.

The obvious fixes each give something up. Deleting old events destroys the history. A second table holding the current quantity is a second source of truth: nothing marks it as derived, so once it disagrees with the events neither can be trusted. Caching the aggregate in memory loses it on restart and does not help a second instance.

### The solution

**A Snapshot stores the aggregate's state at one version, so that a load reads the snapshot and replays only the events after it.**

Greg Young calls this a rolling snapshot and describes it as a heuristic: the stream is still the record, and the snapshot only controls how many events a load reads. Martin Fowler's example is an overnight snapshot from which the day's events are replayed. Three decisions make it safe.

A snapshot is a cache of the fold, never a source of truth. It can be deleted at any time and the events rebuild it, so a bug in how it was written costs time and not data. It describes the aggregate at exactly one version, a newer snapshot replaces it, and only the newest is kept. And the aggregate's own code writes it, never a person, because a load believes whatever the snapshot says.

### How Quarkus and Camel model it

`InventoryItemAggregate` can be built two ways: from its first event, or from an `InventorySnapshot` plus the events after the snapshot's version. `StockLedger` chooses between them:

```java
SnapshotRow snapshot = useSnapshot ? SnapshotRow.findById(sku) : null;
if (snapshot != null && snapshot.schemaVersion != config.snapshot().schema()) {
    meters.counter("inventory.snapshot.ignored").increment();
    LOG.infof("snapshot schema %d ignored for %s", snapshot.schemaVersion, sku);
    snapshot = null;
}
int from = snapshot == null ? 0 : snapshot.version;
long[] replayed = {0};
try (Stream<StockEvent> tail = tail(sku, from).peek(event -> replayed[0]++)) {
    aggregate = snapshot == null
            ? InventoryItemAggregate.replay(sku, tail)
            : InventoryItemAggregate.from(snapshot.state, snapshot.version, tail);
}
```

The schema version, `inventory.snapshot.schema`, describes `InventorySnapshot`. When the record changes meaning the version is raised, and every stored snapshot with the old value is skipped and rebuilt from the events. Young notes that when the structure of serialised snapshots changes, the existing ones must be deleted and recreated or updated to the new schema. Nothing here updates a snapshot; it is rebuilt.

The tail is read with a Panache projection, not entities:

```java
StoredStockEvent.find("streamId = ?1 and version > ?2 order by version", sku, afterVersion)
        .project(Payload.class).withHint("org.hibernate.fetchSize", 500).stream().map(Payload::payload);
```

A projection matters for memory. A Panache `stream()` of entities still puts every row in the persistence context, so replaying 100,000 events holds 100,000 entities until the transaction ends. A projection to a record returns the payloads and nothing is managed.

`ReservationTransaction` loads, decides, appends, and then compares the new version with the snapshot's:

```java
if (config.snapshot().inline() && every > 0
        && aggregate.version() - loaded.stats().snapshotVersion() >= every) {
    store.upsert(aggregate, "inline");
}
```

The upsert is one SQL statement, written through the `EntityManager` because Panache has no native-query API:

```sql
INSERT INTO inventory.snapshots (stream_id, version, schema_version, state, taken_at, taken_by)
VALUES (?1, ?2, ?3, cast(?4 as jsonb), now(), ?5)
ON CONFLICT (stream_id) DO UPDATE SET version = excluded.version, schema_version = excluded.schema_version,
    state = excluded.state, taken_at = excluded.taken_at, taken_by = excluded.taken_by
WHERE excluded.version > inventory.snapshots.version
```

The `WHERE` is the guard. Two writers can compute snapshots from different versions, and without it the older one can arrive last and move the stored version backwards. A snapshot never conflicts with an appending command: it lives in its own table, keyed to a version, and the command that writes it has just appended at that version. Reads never write, so a `GET` on a stream that has none stays slow until a command arrives.

A lost race on a hot SKU is retried with `@Retry(retryOn = ConcurrencyConflict.class)` around the separate `@Transactional` bean, so each attempt is a new transaction that loads again.

Camel offers the other place to write the snapshot. `SnapshotterRoute` is Young's out-of-band snapshotter: a timer, a query for the streams that are too far past their snapshot, and one call per stream.

{% raw %}
```java
from("scheduler:snapshotter?delay={{inventory.snapshotter.delay-ms}}")
        .setHeader("n", constant(config.snapshot().every()))
        .to("sql:" + TOO_FAR_BEHIND)
        .split(body())
        .setBody(simple("${body[stream_id]}"))
        .bean("snapshots", "take");
```
{% endraw %}

`take` loads the stream through the same `StockLedger` and writes with the same guarded upsert in a transaction of its own. A Quarkus `@Scheduled` method would do the same job in less code; a query, a split and a call per item is what a route expresses well.

## What a rebuild costs

The timing is taken inside the service, around the snapshot read, the event read, the JSON mapping and the fold. For each stream, five loads are discarded and 21 are timed, with the full load and the snapshot load alternating so that drift affects both. The median is reported. The database is PostgreSQL on the compose stack and the machine is an 8-core laptop. Before the timing each stream receives 100 reservations, so that it holds a snapshot with 99 events after it, the longest tail the policy allows. The streams then hold 1,100, 10,100 and 100,200 events. Each figure is the median of three runs, each in a new JVM.

{% include excalidraw.html file="15-rebuild-cost" alt="Median load times: a full replay takes 1.6 ms for 1,100 events, 11.6 ms for 10,100 and 127.7 ms for 100,200, and a snapshot load with 99 events after it takes 0.50 ms, 0.63 ms and 0.95 ms" caption="Figure 15.2: A full load grows with the stream, a snapshot load does not" %}

| Stream | Events in the stream | Events replayed with a snapshot | Median full load | Median snapshot load | Cost per event on a full load |
|---|---|---|---|---|---|
| `SKU-1501` | 1,100 | 99 | 1.6 ms | 0.50 ms | 1.4 us |
| `SKU-1502` | 10,100 | 99 | 11.6 ms | 0.63 ms | 1.2 us |
| `SKU-1503` | 100,200 | 99 | 127.7 ms | 0.95 ms | 1.3 us |

The full load is linear in the stream, and most of it is spent turning rows into events and folding them, not reading them: `EXPLAIN (ANALYZE)` puts the database read of the 100,200 event stream at 41 ms of the 128 ms. The snapshot load reads one row and 99 events, so it stayed under 1 ms on every stream in all three runs, and it rises from 0.50 ms to 0.95 ms while the full load rises from 1.6 ms to 127.7 ms. The state is 57 bytes of `jsonb` at any version, which is why the policy counts events and not bytes.

The cost per event gives the rule for N. A load replays at most N-1 events after a snapshot, so the worst case is N-1 times the per-event cost plus the fixed cost of reading the snapshot. A smaller N bounds the tail more tightly and writes more snapshots, each one a row version written inside a command.

## Inline or out of band

Where the snapshot is written decides how long the tail can get.

{% include excalidraw.html file="15-inline-vs-snapshotter" alt="Inline, the command that crosses N events writes the snapshot in the same transaction, so the tail stays below N; out of band, commands append without snapshots and a timer route takes them later, so the tail can pass N during a burst" caption="Figure 15.3: Inline bounds the tail, out of band moves the cost" %}

| | Inline, in the command's transaction | Camel route on a timer |
|---|---|---|
| Tail after a load | At most N-1 events | N plus whatever arrives between two ticks |
| Cost on the command | One upsert every N events | None |
| Finding the work | None | One `max(version)` scan per tick, unless a head table is kept |
| Failure of the snapshot | Rolls the command back | The next tick retries; commands are unaffected |
| Streams nobody commands | Never snapshotted | Snapshotted when they pass N, bulk loads included |
| Same row, same guard | Yes | Yes |

During a burst of 1,000 reservations with a route period of one second, the largest tail sampled reached 350 events with N at 100 (322 to 358 over three runs). Within eight seconds after the burst the stored snapshot was again less than N events behind the head, and the version it stored never went down. With a period of 30 seconds the tail grew to 974, almost the size of the burst.

Young's snapshotter suits commands on a latency budget, or streams bulk-loaded without a snapshot. Inline is the default because it is the only arrangement that bounds the tail.

## Build, run, observe

```bash
cd examples/15-snapshots
mvn -B verify
./verify.sh
```

The Maven build runs 13 tests against PostgreSQL from Dev Services: a snapshot plus the tail equals the full replay at every split point for N of 1, 7 and 100, a snapshot with another schema is ignored, a read writes nothing, the guarded upsert never lowers the version, a refused reservation appends nothing, eight writers on one SKU lose no reservation, the Camel route writes the snapshot of a stream past N, a failed inline snapshot rolls its reservation back, and a snapshotter tick that fails leaves reservations working and is repeated on the next tick. `verify.sh` starts PostgreSQL, the LGTM stack and the service, loads the three streams, and prints `27/27 checks passed`.

The checks read the effects:

- `SKU-1503` is read from the bulk-loaded events with the quantity that SQL folds from the payloads, all 100,000 events replayed. The first reservation writes a snapshot at version 100001. A load then replays 0 events; after 99 more reservations it replays exactly 99; the hundredth moves the snapshot on. All three streams give the same quantity and version with and without the snapshot, and a stream nobody has commanded has none.
- The snapshot load of the 100,000 event stream is at least 20 times faster than the full load.
- After every snapshot is deleted the state is the same and the whole stream is replayed; the next reservation writes one again. A snapshot whose schema version is set to 0 by hand is ignored and logged.
- A reservation larger than the stock returns 409, appends nothing and leaves the snapshot row untouched. 200 reservations from 8 clients on one SKU all succeed with contiguous versions. A snapshot written late is refused by the guard.
- With the `camel-snapshotter` profile only the route writes snapshots, the stored version never decreases during a burst, and the snapshot catches up after it.
- A trace and logs for `inventory-service` reach Tempo and Loki, and Prometheus holds the rehydrate timer and the counter of ignored snapshots.

Change one thing and watch a guard fail:

- Run `SNAPSHOT_EVERY=0 ./verify.sh`. No snapshot is ever written and every load replays the whole stream. Checks 4 to 7, 10 and 11 fail, and the run stops before check 12, because no snapshot exists to delete.
- Run `SNAPSHOT_EVERY=10 ./verify.sh` and then `SNAPSHOT_EVERY=1000 ./verify.sh`. Every check passes, and the tail and the writes follow N:

  | N | At most this many events replayed by a load | Snapshots per 1,000 reservations | Median snapshot load, `SKU-1503` |
  |---|---|---|---|
  | 10 | 9 | 100 | 1.19 ms |
  | 100 | 99 | 10 | 0.95 ms |
  | 1000 | 999 | 1 | 1.60 ms |

  The runs replayed exactly 9 and 999 events after the snapshot. The loads for N of 10 and 1000 come from one run each.

- Run `SNAPSHOT_GUARD=false ./verify.sh`. The stale snapshot replaces a newer one, the stored version goes down, and check 18 is the only one that fails.
- Change the quantity in the stored `state` of a snapshot and leave its schema version alone. The next `GET` answers with the wrong quantity, and the same `GET` with `ignoreSnapshot=true` does not. A snapshot is believed, and deleting it is the repair.
- Restart the service with `SNAPSHOT_SCHEMA=2`. The first load replays the whole stream and the next command writes a snapshot of schema 2.
- Run `SNAPSHOTTER_DELAY_MS=30000 ./verify.sh`. Every check passes, because the route still catches up afterwards, but the largest tail sampled grows from 350 to between 970 and 1,000 events (974 and 993 in two runs).
- Run `INVENTORY_FETCH=entities EXTRA_JAVA_OPTS=-Xmx96m ./verify.sh`. Replaying 100,000 events as managed entities exhausts a 96 MB heap and the service exits with `OutOfMemoryError` at the first load, so check 3 stops the run. With the projection the same heap passes 27 of 27.

## When not to use it

Most streams do not need one. A stream of 1,100 events loads in 1.6 ms without one, and every snapshot is another thing to version, write and keep correct. Measure the load first.

A snapshot cannot repair a wrong fold. If the aggregate's logic had a bug, the snapshot repeats it. Fix the logic, raise the schema version, and let the events rebuild every snapshot.

When the read side is hot, a projection is the better cache: a table kept up to date from the events and built for queries. Snapshots serve the load path of the aggregate only.

> **🤖 Explore with your agent**
> "In examples/15-snapshots, load SKU-1503 with and without its snapshot, then find where InventoryItemAggregate, StockLedger, SnapshotterRoute and inventory.snapshot.schema decide what a load replays. Set SNAPSHOT_EVERY to 10 and state the most events a load can replay and what it costs in snapshot writes."
> Check: a load with a snapshot replays fewer than N events, a load without it replays the whole stream, and both give the same quantity and version.

## References

- Greg Young, [CQRS Documents](https://cqrs.wordpress.com/wp-content/uploads/2010/11/cqrs_documents.pdf), 2010: rolling snapshots, the separate snapshot table and the snapshotter outside the application server, and rebuilding snapshots when their serialised structure changes
- Martin Fowler, [Event Sourcing](https://martinfowler.com/eaaDev/EventSourcing.html), 2005
- Microsoft Azure Architecture Center, [Event Sourcing pattern](https://learn.microsoft.com/en-us/azure/architecture/patterns/event-sourcing): snapshots at intervals, regenerated from the stream
- Alex Lawrence, *Message- and Event-Driven Systems with CQRS and Event Sourcing*, Addison-Wesley, 2026, chapter 6: Event Sourcing
- Martin Kleppmann and Chris Riccomini, *Designing Data-Intensive Applications*, 2nd ed., O'Reilly, 2026
- Quarkus, [Hibernate ORM with Panache](https://quarkus.io/guides/hibernate-orm-panache) (`stream()` and projections) and [SmallRye Fault Tolerance](https://quarkus.io/guides/smallrye-fault-tolerance) (`@Retry`)
- Apache Camel, [Scheduler](https://camel.apache.org/components/4.22.x/scheduler-component.html), [SQL](https://camel.apache.org/components/4.22.x/sql-component.html) and [Split](https://camel.apache.org/components/4.22.x/eips/split-eip.html)
- PostgreSQL documentation, [INSERT](https://www.postgresql.org/docs/18/sql-insert.html) (`ON CONFLICT DO UPDATE ... WHERE`), [EXPLAIN](https://www.postgresql.org/docs/18/sql-explain.html) and [Set Returning Functions](https://www.postgresql.org/docs/18/functions-srf.html) (`generate_series`)
- Chapter 01: Events, commands and documents, for the event table and the left fold, and chapter 14 for the aggregate and its optimistic append
