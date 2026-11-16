---
title: "Appendix G: Write-forward cache with an outbox and CDC"
order: 42
marker: "G"
label: "Appendix G"
part: appendices
permalink: /docs/ag-write-forward-cache/
description: "A cache that the request writes cannot commit with the database. A cache that only the events write can lag but never invent an order. The order and its event commit together, Debezium carries the event out, and a route applies it to Redis behind a version guard."
duration: "50 minutes"
---

`order-service` answers `GET /orders/{id}` thousands of times for every order it places. PostgreSQL can answer them, but the reads are cheap to move: the order is small, it changes rarely, and the answer is the same for everyone. Put it in Redis. The question is who writes it there. The obvious answer is the request that changes the order: save the row, set the key. It is the dual write of chapter 8 with Redis in place of Kafka, and it fails the same two ways.

{% include excalidraw.html file="ag-write-forward-path" alt="POST /orders writes the order row and the outbox row in one transaction; Debezium and the EventRouter publish the full state to edp.orders.placed and edp.orders.cancelled; a Camel route applies it to a Redis hash behind a version guard; GET /orders/{id} reads Redis only" caption="Figure G.1: The request writes PostgreSQL, the events write Redis, the read endpoint reads Redis" %}

## Pattern: Write-forward cache

### The problem

PostgreSQL and Redis have no shared transaction. A request that writes both can die between the two writes, and the cache and the database then disagree until someone notices. Write Redis first and the cache shows an order that a failed commit never created. Write it second and a crash leaves the cache holding the old state with nothing left to correct it. A Kafka transaction does not help: it covers records in one Kafka cluster and the offsets of the group that reads them (chapter 12), and neither PostgreSQL nor Redis is a participant. Neither does a cache-aside read. The Azure Architecture Center describes cache-aside as loading data on demand from the store, which gives a stale entry a way to survive, not a way to be corrected.

### The solution

**A write-forward cache is written only from the events of committed changes, so it can fall behind its source but never contain something the source did not commit.**

The request writes the order and, in the same transaction, an outbox row that carries the full state of the order (chapters 3 and 8). A log-tailing relay publishes the row, and a consumer applies it to the cache. The read side never writes to the cache and never falls back to the database, so a stale answer is visible as stale, and a miss is a miss. Every change increments a version, and the cache keeps an entry only if its version is higher than the one it holds. That makes the consumer idempotent and makes a replay of old events harmless.

The shape is CQRS (chapter 16) with a key-value store as the read model. The Azure Architecture Center's Materialized View pattern describes the same idea: views prepared over the source data for the queries that need them. The difference from a projection into a table is the guard: Redis has no `ON CONFLICT ... WHERE`, so the comparison has to be made atomically in the client.

### How Quarkus and Camel model it

`OrderService` increments `version` and fires an `ExportedEvent` in one `@Transactional` method, exactly as chapter 8, except that the payload is the whole `OrderView` and the topic column routes it. `place` fires to `edp.orders.placed`, and `cancel` fires to `edp.orders.cancelled`. The connector is chapter 8's file, unchanged: the Outbox Event Router sends each row to the topic named in its `topic` column, keyed by the order id.

One Camel route is the only writer of the cache:

{% raw %}
```java
from("kafka:{{cache.topics}}?groupId={{cache.group}}&autoOffsetReset=earliest")
        .routeId("order-cache")
        .unmarshal().json(JsonLibrary.Jackson, OrderView.class)
        .bean("orderCache", "apply");
```
{% endraw %}

`OrderCache.apply` is the guard. The Quarkus Redis client's typed transaction API watches the key, reads the stored version and queues the write only if the new version is higher:

```java
do {
    var result = redis.withTransaction(
            reader -> reader.hash(String.class).hget(key, "version"),
            (stored, tx) -> {
                if (stored == null || Long.parseLong(stored) < view.version()) {
                    tx.hash(String.class).hset(key, fields);
                }
            },
            key);
    discarded = result.discarded();
} while (discarded);
```

If another writer touches the key between the read and the commit, Redis discards the transaction and the loop reads again. The alternative is a short Lua script run with `EVAL`, which makes the same comparison in one round trip. Both leave the same state after a replay. The typed form needs no script text in the Java code, and that is why it is shown here.

Redis runs as the unmodified upstream image, `redis:8.10.3-alpine`, used under the AGPLv3 option of the tri-licence that Redis 8 is published under (RSALv2, SSPLv1 or AGPLv3), the only one of the three that is open source. It runs with no persistence, so a restart is a real cache loss. Quarkus Dev Services starts Redis 7 unless `quarkus.redis.devservices.image-name` names the same image, so the property is set.

`GET /orders/{id}` reads the hash and answers 404 on a miss. A second endpoint, `GET /orders/{id}/db`, reads PostgreSQL, so that a comparison of the two is one request each.

## How stale the cache is

The cache is behind by the time an event needs to travel: the commit, the log, the connector, the topic, the route, Redis. Measured from the answer to `POST /orders` to the first read that finds the order in Redis, the median delay was 491 to 496 ms and the slowest of 20 orders 499 to 875 ms across runs, and most of it is the connector's polling interval, not Kafka or Redis. A read that follows its own write at once usually misses. The request that wrote the order is the place to confirm it; the cache catches up.

With Connect stopped, the order is committed and readable from PostgreSQL, and the cache has no answer for it. The staleness is the outage. When Connect returns, the backlog arrives in commit order and the cache catches up without any intervention. A service that must never answer from a stale cache reads the source for the orders it just wrote; one that can tolerate a few hundred milliseconds does not need to.

## Losing the cache and rebuilding it

A cache without persistence is gone when Redis restarts. `order-service` keeps accepting orders, because the request path makes no Redis call, and the events that arrive while Redis is down cannot be applied.

{% include excalidraw.html file="ag-rebuild-by-replay" alt="Redis restarts and holds no keys; order-service keeps accepting orders; a new consumer group reads edp.orders.cancelled and edp.orders.placed from the earliest offset and the version guard keeps the newest version of every order" caption="Figure G.2: A new consumer group replays both topics and the guard keeps the newest state" %}

The repair is to read the topics again. A new value for `cache.group` starts a consumer group with no offsets, `autoOffsetReset=earliest` reads both topics from the start, and the version guard turns the replay into the newest state of every order, whichever topic the records arrive from. The full state in each event is what makes this work: no event depends on an earlier one. It took 1 to 2 s to refill 27 orders after the service started answering requests. The replay also repairs the events that could not be applied while Redis was down.

The guard is what keeps the replay safe. Two topics are read by one consumer, and nothing orders a record of `edp.orders.cancelled` against one of `edp.orders.placed` (chapter 4). If the placed record for an order arrives after its cancelled record, a cache without the guard shows the order as placed again. With `CACHE_GUARD=off` (demo code) and a replay of the cancelled topic followed by the placed topic, the cancelled orders went back to PLACED at version 1; with the guard they stayed CANCELLED at version 2. The two-phase form is the one to rely on: a single group reading both topics ended correct by luck of fetch order.

The replay reaches as far back as the topics retain, so retention bounds how old an order can be and still be rebuilt (chapter 16).

## The dual write it avoids

{% include excalidraw.html file="ag-dual-write-failure" alt="Dual write: the request writes Redis, then the PostgreSQL commit fails with a 500, and GET answers 200 from Redis for an order PostgreSQL does not hold; write-forward: the commit fails, no event is published, Redis is untouched and GET answers 404" caption="Figure G.3: A failed commit after a cache write, and the same failure with write-forward" %}

`CACHE_WRITE=dual` (demo code) writes Redis inside the request before the commit, which is the code most services start with. For an order whose id is named in `FAIL_BEFORE_COMMIT_FOR`, the request then fails before the commit, the way a constraint violation or a lost connection does. The response is a 500 and PostgreSQL holds no such order. Redis answers 200 for it. With the default `CACHE_WRITE=forward` the same failing request leaves no row, no event and no cache entry. Putting the cache write after the commit trades this for the other failure, the gap of chapter 8 with Redis in place of Kafka: a crash between the two leaves the cache without the change.

The dual write has a second cost. With Redis stopped, the write-forward request still returns 201, because it never calls Redis. The dual-write request fails, so the cache's availability becomes the order service's.

## Build, run, observe

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/ag-write-forward-cache
mvn -B verify
./verify.sh
```

`verify.sh` starts Kafka, PostgreSQL, Connect, LGTM, Redis and `order-service`, registers the connector, places orders and replays the topics. Watch the cache trail each commit by a few hundred milliseconds and a stopped Redis refill from the topics. It prints `24/24 checks passed`.

Try one change and rerun:

- `CACHE_WRITE=dual ./verify.sh` (demo code): Redis holds an order PostgreSQL never committed.
- `CACHE_GUARD=off ./verify.sh` (demo code): a replay of older events moves cancelled orders back to PLACED.

## When not to use it

The pattern costs an outbox, a connector, a replication slot, a consumer and a second store to run, and it adds a delay that the reader sees. If a reader must see its own write, read the source. If the answer is slow to compute but the order is not, a cache-aside read with a short expiry is the smaller change, and it needs none of this.

A cache that mirrors one table is a projection (chapter 16); when the view joins several owners' events, it needs the projection's guards and not a single version. Where the application can tolerate a lost update, a plain cache-aside is simpler. The full state in every event also makes each event as large as the order: for large documents, hold a reference and read the rest from the source (chapter 3).

> **🤖 Explore with your agent**
> "In examples/ag-write-forward-cache, run the cache with CACHE_GUARD=off, replay edp.orders.cancelled and then edp.orders.placed into a new CACHE_GROUP, and compare OrderCache.apply with and without the guard for an order that was cancelled. Then state which two writers of the Redis hash exist in each CACHE_WRITE mode."
> Check: with the guard the cancelled orders stay CANCELLED at version 2 after the placed replay, without it they read PLACED at version 1, and in forward mode only the CacheRoute writes Redis.

## References

- Quarkus, [Redis client guide](https://quarkus.io/version/3.40/guides/redis) and [Redis Dev Services](https://quarkus.io/version/3.40/guides/redis-dev-services)
- Redis, [Licenses](https://redis.io/legal/licenses/) and the [LICENSE.txt of 8.10.3](https://github.com/redis/redis/blob/8.10.3/LICENSE.txt)
- Debezium documentation, [Outbox Event Router](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html)
- KIP-98, [Exactly Once Delivery and Transactional Messaging](https://cwiki.apache.org/confluence/display/KAFKA/KIP-98+-+Exactly+Once+Delivery+and+Transactional+Messaging)
- Microsoft Azure Architecture Center, [Cache-Aside pattern](https://learn.microsoft.com/en-us/azure/architecture/patterns/cache-aside) and [Materialized View pattern](https://learn.microsoft.com/en-us/azure/architecture/patterns/materialized-view)
- Chris Richardson, [Pattern: Transactional outbox](https://microservices.io/patterns/data/transactional-outbox.html), microservices.io
- Enterprise Integration Patterns with Camel, Appendix D: [Redis for Integration](https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/22-appendix-redis/)
