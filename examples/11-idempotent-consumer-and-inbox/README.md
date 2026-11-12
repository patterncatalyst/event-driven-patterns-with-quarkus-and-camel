# 11 Idempotent consumer and inbox

`inventory-service` reads `OrderPlaced` from `edp.orders.placed` and reserves stock once per event id. The id is the record's `id` header (the shape the outbox of chapter 08 publishes). An inbox row and the stock decrement commit in one PostgreSQL transaction, so a duplicate record and a redelivery after a crash change nothing. A second path does the same through Camel's Idempotent Consumer EIP with a JPA repository, joined to the route's JTA transaction.

Quarkus, SmallRye Reactive Messaging, Hibernate ORM with Panache, Flyway, Camel Kafka with the JPA and JTA extensions, PostgreSQL, SmallRye Health and OpenTelemetry to the LGTM stack. Shared topic names come from `examples/domain-model`.

## Service

| Service | Port | Purpose |
|---|---|---|
| `inventory-service` | 8080 | Consumes `edp.orders.placed`. Health on `/q/health/ready` |

| Setting | Default | Effect |
|---|---|---|
| `INBOX_ENABLED` | `true` | Demo code (`demo.inbox.enabled`): `false` decrements without recording the event id |
| `INVENTORY_PROFILE` | `prod` | `camel-repository` turns the SmallRye channel off and starts the Camel route |
| `INVENTORY_GROUP` | `inventory-inbox` | Consumer group of the SmallRye channel |
| `CRASH_AFTER_COMMIT_FOR` | empty | Demo code: comma-separated order ids for which the JVM halts after the commit, before the acknowledgement |
| `FAIL_AFTER_EFFECT_FOR` | empty | Demo code: order ids whose reservation fails after the decrement |
| `CAMEL_TRANSACTED` | `true` | `false` removes `.transacted()` from the Camel route |

## Run

```bash
(cd ../domain-model && mvn -B -q install)
mvn -B verify      # inbox, rollback, race, duplicate record and Camel route against Dev Services
./verify.sh        # compose stack
KEEP=1 ./verify.sh # leave the stack up; Grafana is on http://localhost:3000
docker compose down -v
```

`verify.sh` refuses to start while another stack is running, always starts from fresh containers and volumes, and removes the stack on exit unless `KEEP=1`.

## What verify.sh does

1. Starts the stack and checks the consumer group and the seed data (1, 2).
2. Produces id A (`O-1101`, quantity 3) twice and id B (`O-1102`, quantity 2): stock, inbox and the skip in the log (3 to 7).
3. Recreates the service with the crash switch for `O-1103` (id C, quantity 4): exit 137, offset not past C, effect committed, then restart and redelivery skipped (8 to 13).
4. Resets the data and reads the same records with the inbox off: the duplicate is applied twice (14 to 16).
5. Resets the data and reads them through the Camel route: stock, repository keys and the untouched inbox (17 to 20).
6. Fails `O-1104` after its decrement: effect and key roll back together, and a redelivery reserves it (21 to 26).
7. A trace in Tempo and logs in Loki (27, 28).

## Layout

| Path | Purpose |
|---|---|
| `inventory-service/.../ReservationService` | `reserveOnce` inserts the inbox row and decrements in one transaction; `reserve` decrements alone |
| `inventory-service/.../OrderPlacedInbox` | The SmallRye consumer: reads the `id` header, skips duplicates, counts them |
| `inventory-service/.../InventoryItemEntity`, `InboxEntry` | Panache entities of the two tables |
| `inventory-service/.../IdempotentRepositoryRoute` | The Camel route with `JpaMessageIdRepository` |
| `inventory-service/.../CrashSwitch`, `DemoFailure` | Demo code: halt after the commit, fail after the effect |
| `inventory-service/src/main/resources/db/migration/V1__inventory.sql` | Stock, inbox and the Camel repository table |
| `compose.yaml` | The order topic and the service |
