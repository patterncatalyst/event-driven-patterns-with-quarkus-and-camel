# 01 CRUD versus events

One command, `ChangeConsignmentStatus`, applied two ways to the same Consignment: the `consignment` row is updated in place, and a `ConsignmentStatusChanged` event is appended to `consignment_event`. The service answers the same questions from both and shows which ones only the events can answer.

Quarkus REST, Hibernate ORM with Panache, Flyway, PostgreSQL, SmallRye Health and OpenTelemetry to the LGTM stack. No Camel and no Kafka: they arrive in chapter 02. The shared records come from `examples/domain-model`.

## Endpoints

| Method and path | Result |
|---|---|
| `POST /consignments` | Books a Consignment: one row and a `ConsignmentBooked` event |
| `POST /consignments/{id}/status` | Body `{"to": "LOADED"}`, optional `"expectedVersion"`. Updates the row and appends an event in one transaction. 409 for an illegal transition or a stale version |
| `GET /consignments/{id}` | The Consignment document read from the row |
| `GET /consignments/{id}/replayed` | The same document rebuilt by replaying the stream |
| `GET /consignments/{id}/history` | Every status with its version and timestamp, from events only |

The lifecycle is BOOKED, LOADED, IN_TRANSIT, CLEARED, DELIVERED, one step forward at a time.

## Run

```bash
mvn -B verify      # six tests, PostgreSQL from Dev Services
./verify.sh        # compose stack plus the observable checks
KEEP=1 ./verify.sh # leave the stack up; Grafana is on http://localhost:3000
docker compose down -v
```

`verify.sh` refuses to start while another stack is running, and stops this one on exit unless `KEEP=1`.

## Layout

| Path | Purpose |
|---|---|
| `src/main/resources/db/migration/V1__consignment_and_events.sql` | Both tables, the unique `(stream_id, version)` and the append-only trigger |
| `ConsignmentService` | The two paths: `updateRow` and `appendEvent` |
| `ConsignmentDocument` | Row to document, and replay of a stream to the same document |
| `ConsignmentResource` | REST endpoints and 409 mapping |
| `Containerfile` | UBI 10 multi-stage build; context is `examples/` |
| `compose.yaml` | Includes the shared PostgreSQL and LGTM files and adds the app on port 8080 |

Traces and logs go to `http://lgtm:4318` over OTLP http/protobuf with `service.name` `crud-vs-events`.
