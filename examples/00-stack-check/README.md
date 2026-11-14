# 00 Stack check

A Quarkus and Camel service that proves the whole local stack works. `POST /orders` takes an `Order` as JSON, rejects an invalid one with 400, and publishes a valid one to the Kafka topic `edp.orders.placed`, keyed by order id. A second route consumes the topic and logs each record. Traces, metrics and logs leave the service over OTLP (http/protobuf) to the Grafana LGTM container with `service.name` `stack-check`. SmallRye Health serves `/q/health`.

## Run

`./verify.sh` needs only Docker Engine with the compose plugin; it builds the service and the shared domain model inside the image. `mvn -B verify` needs JDK 25 and Maven.

```bash
./verify.sh
```

`verify.sh` starts Kafka, PostgreSQL, Apicurio Registry, Kafka Connect and LGTM plus the service, runs the checks below and removes everything with `down -v`. Set `KEEP=1` to leave the stack running for inspection.

Run the tests alone (Quarkus Dev Services starts a Kafka container; no compose stack needed). Install the shared domain model first:

```bash
(cd ../domain-model && mvn -B -q install)
mvn -B verify
```

## Expected output

```
PASS 1: edp-kafka is healthy
...
PASS 7: valid Order accepted with 2xx
PASS 8: the record keyed O-1794344257 arrives on the edp.orders.placed topic
PASS 9: PostgreSQL wal_level is logical
...
PASS 13: Tempo has a trace for service stack-check
PASS 14: Prometheus has a metric from stack-check
PASS 15: Loki has a log line from stack-check
PASS 16: negative witness: invalid Order (quantity 0) gets 400
PASS 17: negative witness: record count unchanged (1)
17/17 checks passed
```

The checks cover: six healthy containers; a valid order accepted and its record on the topic; PostgreSQL `wal_level` is `logical`; Apicurio ready; Connect lists `PostgresConnector` and the outbox `EventRouter`; a trace in Tempo, a metric in Prometheus and a log line in Loki for `stack-check`; and the negative witness: an invalid order (quantity 0) gets 400 and the topic record count is unchanged.

## Tests and checks in detail

The tests pass on a Dev Services Kafka running the pinned broker image.

## Experiments

Each row changes a single thing and names the checks that fail.

| Switch | Effect | Failing checks | Measured result |
|---|---|---|---|
| Remove the `onException` clause | An invalid Order gets 500 instead of 400; the record count still passes because nothing reached the topic | 16 (17 still passes) | - |
| Change `Topics.ORDERS_PLACED` in the intake route to `"edp.orders.placed-v2"` | No record arrives on `edp.orders.placed`, and the consumer route has nothing to log | 8 and 15 | - |

## Layout

| Path | Purpose |
|------|---------|
| `src/main/java/.../OrderIntakeRoute.java` | HTTP to Kafka route with validation |
| `src/main/java/.../OrderLogRoute.java` | Kafka consumer route |
| `src/main/resources/application.properties` | OTLP export, Dev Services image |
| `compose.yaml` | includes the shared infrastructure and adds the service |
| `Containerfile` | UBI 10 multi-stage build; context is `examples/` |
| `verify.sh` | the checks above |
