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

## Tests and checks in detail

The Maven build runs 22 tests against PostgreSQL, Kafka and Apicurio Registry from Dev Services. In `order-service` they cover:

- the chain and its default;
- the stored node left untouched;
- an unknown version;
- a mixed stream of version 1 and version 2 rows;
- the append-only trigger;
- an outbox payload that starts with the schema id and decodes to the current record;
- the two rules in the registry, and a first version accepted on an empty artifact;
- Avro resolution with no registry, where a version 2 reader fills the default and a version 3 reader throws;
- a command under the broken release that leaves no row.

In `notification-service` the SmallRye channel and the Camel route each read a version 1 and a version 2 record, and a plain test fails when its copy of `order-placed.avsc` differs from the one in `order-service`. `verify.sh` starts the stack and prints `21/21 checks passed`.

The checks read the effects, release by release:

- With the registry rule read back, the previous release (`ORDER_PLACED_SCHEMA=1`, demo code) places `O-1801` and `O-1802`. Their rows are stored at `schema_version` 1 with no `shippingPriority`, and `edp.orders.placed` holds two records written with registry version 1.
- The current release places `O-1803` with `EXPRESS`: a version 2 row, a second registry version and a third record, written with version 2.
- `GET /orders/O-1801` returns `STANDARD` from a version 1 row, `GET /orders/O-1803` returns `EXPRESS`, and cancelling `O-1801` leaves the stored payload with the same checksum. An `UPDATE` of a stored payload is refused.
- `notification-service` recorded the three priorities, its group has no lag and no deserialization failure is logged.
- Registering version 3 is refused with 400 and the artifact keeps two versions. The broken release (`ORDER_PLACED_SCHEMA=3`) answers 503 for `O-1804`, stores no row, adds no record and no version, and the log shows the refusal.
- Recreated with the `camel-reader` profile, `notification-service` reads the topic from the earliest offset and records the same three priorities.
- `order_upcasts_total` with `from="1"` reaches Prometheus, and a trace and logs reach Tempo and Loki.

## Experiments

Each row changes a single thing and names the checks that fail.

| Switch | Effect | Failing checks | Measured result |
|---|---|---|---|
| `UPCASTERS=off ./verify.sh` (demo code) | The chain is skipped, loading `O-1801` fails because the current record refuses the missing field, the cancellation fails with it, and no upcast is counted. Check 9 passes, because a version 2 row needs no step | 8, 10 and 19 | 18 of 21 pass |
| `REGISTRY_RULE=BACKWARD ./verify.sh` (demo code) | Version 3 is registered and then published for `O-1804`. A reader built on version 3 cannot read the version 1 records already on the topic | 14 to 16 | 18 of 21 pass |

