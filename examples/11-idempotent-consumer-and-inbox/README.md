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
5. Resets the data and reads them through the Camel route: stock, repository keys, four records against three keys, and the untouched inbox (17 to 20).
6. Fails `O-1104` after its decrement: effect and key roll back together, and a redelivery reserves it (21 to 26).
7. A trace in Tempo, logs in Loki and the `inventory.inbox.duplicates` counter in Prometheus (27 to 29).

## Tests and checks in detail

The Maven build runs the service against PostgreSQL and Kafka from Dev Services, and 7 tests pass. The same event id twice leaves one decrement and one inbox row. An effect that fails after the inbox insert leaves no inbox row. Two threads released together with the same id apply it once, in each of 20 rounds. A duplicate record on the topic is applied once with the inbox and twice without it. The Camel route applies a duplicate once, and a failed effect rolls its key back.

`verify.sh` starts Kafka, PostgreSQL, the LGTM stack and the service, and prints `29/29 checks passed`. It starts from fresh containers and volumes every time. The checks read the database, the consumer group and the log, never a status code:

- `O-1101` (id A, quantity 3) is produced twice and `O-1102` (id B, quantity 2) once. A precondition confirms the topic holds two records with id A before anything is asserted. The group reaches lag 0, `SKU-1` is 95, the inbox holds exactly A and B, and the log shows A skipped once.
- The service is recreated with a crash switch for `O-1103` (id C, quantity 4). The container exits with code 137 after the reservation commits. Preconditions confirm that the group's committed offset is not past C's record and that the stock already dropped by 4. After a recreate without the switch the group reaches lag 0, `SKU-1` is 91 and the log shows C skipped.
- With the inbox off, stock reset and a new group over the same four records, `SKU-1` is 88: A was applied twice, and the inbox stays empty.
- With the Camel route, `SKU-1` is 91 and the repository table holds exactly A, B and C. The topic holds four records against three keys, and the inbox table is untouched.
- `O-1104` fails after its decrement. `SKU-1` stays 91 and the repository does not hold its key. After the offsets are reset and the failure switch is off, the redelivery reserves it: `SKU-1` is 86.
- Traces and logs for `inventory-service` reach Tempo and Loki, and Prometheus holds `inventory.inbox.duplicates` above 0.

## Experiments

Each row changes a single thing and names the checks that fail.

| Switch | Effect | Failing checks | Measured result |
|---|---|---|---|
| `INBOX_ENABLED=false ./verify.sh` | The duplicate and the redelivery are applied, the inbox stays empty, nothing is skipped and the duplicates counter stays at 0. The preconditions pass first, which shows both the duplicate and the redelivery happened | 5, 6, 7, 12, 13 and 29 | 23 of 29 pass |
| `CAMEL_TRANSACTED=false ./verify.sh` | After the failed effect the repository holds the key of `O-1104` and `SKU-1` is still 91, and after the redelivery the stock stays 91. The reservation is lost | 23 and 25 | 27 of 29 pass |
| Delete `session.timeout.ms` from the channel | All 29 checks still pass, but the replacement consumer waits for the broker to expire the dead member's session | none | 49 seconds from the exit of the container to lag 0 instead of 19 |

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
