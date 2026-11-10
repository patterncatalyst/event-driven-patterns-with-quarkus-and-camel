# Shared infrastructure

Composable services for the runnable examples. Each example includes the pieces it needs instead of copying service definitions. Every container is named `edp-<service>`, every service joins the Docker network `edp`, and the aggregate project is `edp-infra`.

## Services

| File | Service | Host port | Container address | Notes |
|------|---------|-----------|-------------------|-------|
| `compose-kafka.yaml` | Kafka (KRaft, one node) | 9092 | `kafka:9094` | Host clients use `localhost:9092`, containers use `kafka:9094`. Share groups are enabled. |
| `compose-postgres.yaml` | PostgreSQL with `wal_level=logical` | 5432 | `postgres:5432` | Database, user and password are all `edp`. |
| `compose-apicurio.yaml` | Apicurio Registry (in-memory) | 8081 (API), 19000 (health) | `apicurio:8080` | API under `/apis/registry/v3`. Empty after a restart. |
| `compose-connect.yaml` | Kafka Connect with Debezium | 8083 | `connect:8083` | Includes the PostgreSQL connector and the outbox `EventRouter`. |
| `compose-lgtm.yaml` | Grafana LGTM (otel-lgtm) | 3000 Grafana, 4317 and 4318 OTLP, 19090 Prometheus, 3200 Tempo, 3100 Loki | `lgtm:4318` | Prometheus is on 19090 because host port 9090 belongs to Cockpit on Fedora and RHEL. |

Image tags are pinned in each file. Every service sets a memory limit and a health check.

## Start and stop

```bash
scripts/setup-stack.sh up        # start everything and wait until healthy
scripts/setup-stack.sh down      # stop, keep data
scripts/setup-stack.sh --clean   # stop and delete volumes
```

Only one stack runs at a time. Stop the infrastructure before starting a Kubernetes cluster, and the other way round.

## Using the pieces from an example

An example's `compose.yaml` includes the files it needs and adds its own services on the `edp` network:

```yaml
include:
  - path: ../_infra/compose-kafka.yaml
  - path: ../_infra/compose-postgres.yaml
  - path: ../_infra/compose-lgtm.yaml

services:
  app:
    build:
      context: ..
      dockerfile: 01-example/Containerfile
    environment:
      KAFKA_BOOTSTRAP_SERVERS: kafka:9094   # SmallRye Reactive Messaging and Kafka Streams
      CAMEL_COMPONENT_KAFKA_BROKERS: kafka:9094   # Camel Kafka component (Camel apps)
      QUARKUS_DATASOURCE_JDBC_URL: jdbc:postgresql://postgres:5432/edp
      QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT: http://lgtm:4318
    healthcheck:
      test: ["CMD", "curl", "-sf", "http://localhost:8080/q/health/ready"]
      interval: 5s
      timeout: 3s
      retries: 12
      start_period: 15s
    networks: [edp]
```

Applications running on the host use `localhost` and the host ports above.

## PostgreSQL

The image is the CloudNativePG operand, the same image the operator runs on Kubernetes. It has no entrypoint, so `postgres/start.sh` initialises an empty data directory with scram-sha-256 authentication, applies `postgres/init/*.sql` once, and then starts PostgreSQL with logical decoding enabled. The process runs as UID 26, and the named volume takes that ownership from the image, so nothing needs a `chown` on the host. Add shared SQL to `postgres/init/`; it runs only when the volume is new, so use `--clean` after changing it.

## Observability

Send OTLP to `http://localhost:4318` from the host or `http://lgtm:4318` from a container. Grafana at `http://localhost:3000` needs no login and has Tempo, Loki and Prometheus preconfigured.
