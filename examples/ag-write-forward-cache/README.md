# Appendix G: Write-forward cache

`order-service` stores every order and an outbox row carrying its full state in one PostgreSQL transaction. Debezium Connect publishes the row to `edp.orders.placed` or `edp.orders.cancelled`, keyed by order id. One Camel route applies each event to a Redis hash `order:{id}` behind a version guard (the Quarkus Redis client's typed `withTransaction` with a watched key). `GET /orders/{id}` reads Redis only; `GET /orders/{id}/db` reads PostgreSQL for comparison.

Quarkus, Hibernate ORM with Panache, Flyway, `debezium-quarkus-outbox`, `quarkus-redis-client`, Camel Kafka, SmallRye Health, PostgreSQL, Debezium Connect, Redis and OpenTelemetry to the LGTM stack. Redis 8 runs as the unmodified upstream image under the AGPLv3 option of its licence. The connector file is the chapter 08 file.

## Service

| Service | Port | Purpose |
|---|---|---|
| `order-service` | 8080 | `POST /orders`, `POST /orders/{id}/cancel`, `GET /orders/{id}` (Redis), `GET /orders/{id}/db`. Health on `/q/health/ready` |

| Setting | Default | Effect |
|---|---|---|
| `CACHE_GROUP` | `order-cache` | The consumer group that feeds the cache; a new name replays both topics from the start |
| `CACHE_TOPICS` | both order topics | Demo code. The topics the route reads, narrowed for the two-phase replay |
| `CACHE_GUARD` | `on` | Demo code. `off` removes the version guard |
| `CACHE_WRITE` | `forward` | Demo code. `dual` writes Redis inside the request before the commit |
| `FAIL_BEFORE_COMMIT_FOR` | `O-4290` | Demo code. Order ids whose request fails before the commit |

## Run

```bash
(cd ../domain-model && mvn -B -q install)
mvn -B verify      # Dev Services: PostgreSQL, Kafka and the pinned Redis
./verify.sh        # compose stack with Connect and Redis
KEEP=1 ./verify.sh # leave the stack up; Grafana is on http://localhost:3000
docker compose down -v
```

`verify.sh` refuses to start while another stack is running, removes earlier containers and volumes first, and stops the stack on exit unless `KEEP=1`. It ends with `24/24 checks passed`.

## What verify.sh does

1. Infrastructure: Connect lists the connector plugin and the service is ready; the connector task is RUNNING; Redis is empty (checks 1 to 3).
2. O-4201 is placed and cancelled: the row has version 1, one record per event carries the full state, the cache follows (4 to 9).
3. Connect is stopped: O-4202 is committed and readable from PostgreSQL, the cache answers 404; after Connect returns the cache holds it (10, 11).
4. Staleness over 20 orders (O-4210 to O-4229), printed as a `NOTE` line; the first read misses at least once (12, 13).
5. O-4290 fails before the commit: 500, no row, no event, no cache entry (14 to 16).
6. O-4230 to O-4233: a fresh group replays both topics and cancelled orders stay cancelled (17); then Redis is flushed, the cancelled topic is applied, the placed topic is applied on top, and they still stay cancelled (18).
7. Redis is stopped: O-4205 is accepted and its event reaches the topic (19); Redis returns empty (20); a fresh group rebuilds every order to its PostgreSQL status and version (21) and the key count equals the order count (22).
8. A trace in Tempo and logs in Loki (23, 24).

## Experiments

| Switch | Effect | Failing checks | Measured result |
|---|---|---|---|
| `CACHE_WRITE=dual ./verify.sh` | Redis is written in the request before the commit | 10, 13, 16, 19 | 20/24 pass |
| `CACHE_GUARD=off ./verify.sh` | No version guard, applied to the older-events-after-newer-ones scenario only; every other check runs with the guard on, so no check depends on the order topics are fetched in | 18 | 23/24 pass |

Staleness from the commit to the first cache hit over 20 orders, five runs with the guard on or off: fastest 413 to 487 ms, median 491 to 496 ms, slowest 499 to 875 ms; every first read missed. With `CACHE_WRITE=dual` the median is 3 ms and no first read misses. Rebuild of 27 orders after a Redis loss, from the service answering requests to a matching cache: 1 to 2 s (polled every 2 s).

## Layout

| Path | Purpose |
|---|---|
| `order-service/.../OrderService` | `place` and `cancel`: row, version and outbox event in one transaction |
| `order-service/.../OrderEvent`, `OrderView` | The `ExportedEvent` and the full-state payload |
| `order-service/.../CacheRoute` | The Camel route that is the only writer of the cache |
| `order-service/.../OrderCache` | The version-guarded write and the read |
| `order-service/.../DemoSwitches` | Demo code: dual write and the failing order |
| `tools/staleness.py` | Measures the delay from commit to cache hit |
| `connect/order-outbox.json` | The Debezium connector with the Outbox Event Router |
