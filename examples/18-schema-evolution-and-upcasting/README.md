# 18 Schema evolution and upcasting

`order-service` keeps orders as an append-only stream, like chapter 14, and every row records the `schema_version` it was written in. `Upcasters` lifts a copy of an older row to the current shape when it is read, so the stored payload is never rewritten. `OrderPlaced` is also serialized to Avro inside the command's transaction with the Apicurio serializer, stored in the outbox as `bytea` and moved unchanged to `edp.orders.placed` by the Debezium Event Router with `BinaryDataConverter`. A registry that refuses the schema rolls the command back. `notification-service` reads the topic with the newest schema (`shippingPriority` has a default) through a SmallRye channel, and through a Camel route as a comparison.

Quarkus, Hibernate ORM with Panache, Flyway, Quarkus REST, SmallRye Reactive Messaging, Apicurio Registry Avro serdes, the Debezium outbox extension, Camel Kafka, PostgreSQL, Kafka Connect, SmallRye Health and OpenTelemetry to the LGTM stack. Topic names come from `Topics` in `examples/domain-model`.

## Services

| Service | Port | Purpose |
|---|---|---|
| `order-service` | 8080 | `POST /orders` (optional `shippingPriority`), `GET /orders/{id}`, `POST /orders/{id}/cancellation`. Health on `/q/health/ready` |
| `notification-service` | 8082 | Records the `shippingPriority` of every `OrderPlaced` it reads, in `notifications.recorded_priority` |

| Setting | Default | Effect |
|---|---|---|
| `ORDER_PLACED_SCHEMA` | `2` | Demo code. `1` behaves as the previous release (shape 1 rows, Avro v1), `3` publishes a `shippingPriority` without a default |
| `UPCASTERS` | `on` | Demo code. `off` skips the upcaster chain |
| `READER_PROFILE` | `prod` | `camel-reader` turns the SmallRye channel off and starts the Camel route (group `notification-camel`) |
| `REGISTRY_RULE` | `BACKWARD_TRANSITIVE` | Demo code in `verify.sh`. The global COMPATIBILITY rule set in the registry; `BACKWARD` checks only the latest version |

## Run

```bash
(cd ../domain-model && mvn -B -q install)
mvn -B verify      # upcasters, store, registry rules, outbox bytes, both readers against Dev Services
./verify.sh        # compose stack
KEEP=1 ./verify.sh # leave the stack up; Grafana is on http://localhost:3000
docker compose down -v
```

`verify.sh` refuses to start while another stack is running, removes any earlier containers and volumes before it starts, and stops this stack on exit unless `KEEP=1`.

## What verify.sh does

1. Starts the stack, sets the registry rule and the `order-outbox` connector (slot and publication `edp_order_schema_outbox`), and reads the rule back.
2. Previous release: places `O-1801` and `O-1802`; checks their rows (shape 1, no `shippingPriority`), the one registry version and the two records.
3. Current release: places `O-1803` with `EXPRESS`; checks the shape 2 row, the second version and the third record.
4. Store: `GET` upcasts `O-1801`, a cancellation leaves its stored payload unchanged, an `UPDATE` is refused.
5. Wire: `notification-service` recorded the three priorities, no lag, no deserialization failure.
6. Refusal: registering v3 is refused with 400; the broken release answers 503 for `O-1804` and leaves no row, record or version.
7. Camel: `notification-service` restarted with `camel-reader` reads the topic from the earliest offset and records the same priorities.
8. `order_upcasts_total` in Prometheus, a trace in Tempo and logs in Loki.

## Layout

| Path | Purpose |
|---|---|
| `order-service/.../Upcasters`, `OrderEvent` | The chain and the current event records |
| `order-service/.../EventStore`, `StoredEvent`, `OrderAggregate` | Upcast on read; append in the current shape |
| `order-service/.../OrderCommands`, `OrderResource` | One command, one transaction; REST and the 409 and 503 answers |
| `order-service/.../OrderPlacedPublisher`, `OrderExports` | Avro serialization through the registry and the outbox row |
| `order-service/.../ReleaseSchema`, `OrderPlacedV1` | Demo code that plays the previous and the broken release |
| `order-service/src/main/avro/order-placed.avsc` | The contract, version 2 |
| `order-service/src/main/resources/avro/` | Versions 1 and 3, used by the demo releases and the tests |
| `notification-service/.../OrderPlacedConsumer`, `OrderPlacedReaderRoute`, `Recorder` | The two readers and what they record; its `order-placed.avsc` is the reader schema it was built on |
| `connect/order-outbox.json` | The outbox connector with `BinaryDataConverter` |
| `compose.yaml` | The topic and the two services |

## Experiments

| Change | What it shows | Checks that fail |
|---|---|---|
| `UPCASTERS=off ./verify.sh` | A stored shape 1 row cannot be read as the current record, and no upcast is counted | 8, 10 and 19 (18/21) |
| `REGISTRY_RULE=BACKWARD ./verify.sh` | Version 3 is accepted and published | 14 to 16 (18/21) |
