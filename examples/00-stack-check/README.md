# 00 Stack check

A Quarkus and Camel service that proves the whole local stack works. `POST /consignments` takes a `Consignment` as JSON, rejects an invalid one with 400, and publishes a valid one to the Kafka topic `consignments`, keyed by consignment id. A second route consumes the topic and logs each record. Traces, metrics and logs leave the service over OTLP (http/protobuf) to the Grafana LGTM container with `service.name` `stack-check`. SmallRye Health serves `/q/health`.

## Run

Needs Docker Engine with the compose plugin, JDK 25 and Maven (the shared domain model is built inside the image).

```bash
./verify.sh
```

`verify.sh` starts Kafka, PostgreSQL, Apicurio Registry, Kafka Connect and LGTM plus the service, runs the checks below and removes everything with `down -v`. Set `KEEP=1` to leave the stack running for inspection.

Run the unit and integration tests alone (Dev Services starts a Kafka container; no compose stack needed):

```bash
mvn -B verify
```

## Expected output

```
PASS 1: edp-kafka is healthy
...
PASS 17: negative witness: the topic did not grow (1 records before and after)
17/17 checks passed
```

The checks cover: six healthy containers; a valid consignment accepted and its record on the topic; PostgreSQL `wal_level` is `logical`; Apicurio ready; Connect lists `PostgresConnector` and the outbox `EventRouter`; a trace in Tempo, a metric in Prometheus and a log line in Loki for `stack-check`; and the negative witness: an invalid consignment gets 400 and the topic does not grow.

## Layout

| Path | Purpose |
|------|---------|
| `src/main/java/.../ConsignmentIntakeRoute.java` | HTTP to Kafka route with validation |
| `src/main/java/.../ConsignmentLogRoute.java` | Kafka consumer route |
| `src/main/resources/application.properties` | OTLP export, Dev Services image |
| `compose.yaml` | includes the shared infrastructure and adds the service |
| `Containerfile` | UBI 10 multi-stage build; context is `examples/` |
| `verify.sh` | the checks above |
