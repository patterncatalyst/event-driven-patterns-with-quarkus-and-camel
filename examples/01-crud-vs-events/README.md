# 01 CRUD versus events

One command, `ChangeOrderStatus`, applied two ways to the same Order: the `orders` row is updated in place, and an event (`OrderPaid`, `OrderShipped`, `OrderDelivered` or `OrderCancelled`) is appended to `order_event`. The service answers the same questions from both and shows which ones only the events can answer.

Quarkus REST, Hibernate ORM with Panache, Flyway, PostgreSQL, SmallRye Health and OpenTelemetry to the LGTM stack. Camel and Kafka are not used here. The shared records come from `examples/domain-model`.

## Endpoints

| Method and path | Result |
|---|---|
| `POST /orders` | Places an Order: one row and an `OrderPlaced` event. 409 if the id exists |
| `POST /orders/{id}/status` | Body `{"to": "PAID"}`, optional `"expectedVersion"` and, for CANCELLED, `"reason"`. Updates the row and appends an event in one transaction. 409 for an illegal transition or a stale version |
| `GET /orders/{id}` | The Order document read from the row |
| `GET /orders/{id}/replayed` | The same document rebuilt by replaying the stream |
| `GET /orders/{id}/history` | Every status with its version and timestamp, from events only |

The lifecycle is PLACED, PAID, SHIPPED, DELIVERED, one step at a time. CANCELLED is allowed only from PLACED or PAID. The amount is stored at two decimals, so 19.999 becomes 20.00 in the row, the event and the replay.

## Run

```bash
(cd ../domain-model && mvn -B -q install)
mvn -B verify      # eleven tests, PostgreSQL from Dev Services
./verify.sh        # compose stack plus the observable checks
KEEP=1 ./verify.sh # leave the stack up; Grafana is on http://localhost:3000
docker compose down -v
```

`verify.sh` refuses to start while another stack is running, and stops this one on exit unless `KEEP=1`.

## Layout

| Path | Purpose |
|---|---|
| `src/main/resources/db/migration/V1__order_and_events.sql` | The `orders` and `order_event` tables, the unique `(stream_id, version)` and the UPDATE and DELETE trigger |
| `src/main/resources/db/migration/V2__append_only_truncate.sql` | The statement trigger that refuses TRUNCATE |
| `OrderService` | The two paths: `updateRow` and `appendEvent` |
| `OrderDocument` | Row to document, and replay of a stream to the same document |
| `OrderResource` | REST endpoints and 409 mapping |
| `Containerfile` | UBI 10 multi-stage build; context is `examples/` |
| `compose.yaml` | Includes the shared PostgreSQL and LGTM files and adds the app on port 8080 |

Compose sets the datasource and OTLP endpoint through `QUARKUS_*` variables. Traces and logs go to `http://lgtm:4318` over OTLP http/protobuf with `service.name` `crud-vs-events`.
