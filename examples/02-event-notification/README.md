# 02 Event notification

order-service announces that an order was placed. The event names the order and carries nothing else. notification-service reads the event, fetches the order from order-service over HTTP and stores a Notification built from what it fetched.

Quarkus REST, Hibernate ORM with Panache, Flyway and PostgreSQL in both services; Camel on Quarkus for the Kafka producer route, the Kafka consumer route and the HTTP fetch; OpenTelemetry to the LGTM stack. The shared records come from `examples/domain-model`.

## Services

| Service | Port | Purpose |
|---|---|---|
| order-service | 8080 | Owns the `orders` schema. `POST /orders` places an order and publishes `OrderPlaced`. `GET /orders/{id}` returns its fields and current status. `POST /orders/{id}/cancel` cancels it (409 once it is past PAID) |
| notification-service | 8082 | Owns the `notifications` schema. Consumes `edp.orders.placed`, fetches the order, stores a notification. `GET /notifications?orderId=` lists them |

`OrderPlaced` is `{eventId, occurredAt, orderId}` on `edp.orders.placed`, keyed by order id. order-service publishes it after the order's transaction commits, so a record is never visible before its row, and a rolled-back order publishes nothing.

## Run

```bash
(cd ../domain-model && mvn -B -q install)
mvn -B verify      # order-service and notification-service tests, PostgreSQL and Kafka from Dev Services
./verify.sh        # compose stack plus the observable checks
KEEP=1 ./verify.sh # leave the stack up; Grafana is on http://localhost:3000
docker compose down -v
```

`verify.sh` refuses to start while another stack is running, and stops this one on exit unless `KEEP=1`. Its source-down check stops and starts the two services in a fixed order, so run it on its own.

## Tests and checks in detail

The Maven build runs both services' tests against PostgreSQL and Kafka started by Dev Services. In notification-service the order-service is a test resource served on the test application's own port, so a test can make the source answer, refuse or report CANCELLED on demand. `verify.sh` starts Kafka, PostgreSQL, LGTM and both services with Docker Compose and prints `21/21 checks passed`:

- Placing an order publishes one record keyed by the order id whose value holds exactly the event id, the time and the order id.
- The notification holds the customer and amount that order-service holds, read through the fetch, and one Tempo trace holds order-service's `POST /orders` span together with the consume and the fetch in notification-service. Request metrics from order-service and JVM metrics from notification-service reach Prometheus.
- With notification-service stopped, a second order placed and then order-service stopped too, notification-service starts, tries the fetch, fails, and neither writes a notification nor moves the group's offset past the record. When order-service starts again the notification appears and the group has committed everything.
- A third order cancelled before notification-service reads its event yields a notification that says CANCELLED.

Run `KEEP=1 ./verify.sh` and open Grafana at `http://localhost:3000` to follow the trace from the order request to the fetch.

## Experiments

Each row changes a single thing and names the checks that fail.

| Switch | Effect | Failing checks | Measured result |
|---|---|---|---|
| Remove `breakOnFirstError=true` from the consumer route | The consumer reads past the record it could not fetch and commits it, so the offset moves beyond B and B's notification never appears | 14 and 16 | - |
| Send the event without the key header in `OrderPlacedPublisher` | The record is no longer keyed by the order id | none in `verify.sh`; the three Maven tests that read the topic by order id fail | - |

## Layout

| Path | Purpose |
|---|---|
| `order-service/.../OrderService` | Stores the order and fires the CDI event inside the transaction |
| `order-service/.../OrderPlacedPublisher` | Observes `AFTER_SUCCESS` and hands the event to the route |
| `order-service/.../OrderPlacedRoute` | `direct:order-placed` to the topic as JSON; the key header comes from `OrderPlacedPublisher` |
| `notification-service/.../OrderPlacedNotificationRoute` | Kafka consumer with `breakOnFirstError`, redelivery, the `toD` fetch and the store |
| `notification-service/src/test/.../StubOrderService` | Test-scoped JAX-RS resource that stands in for order-service |
| `*/Containerfile` | UBI 10 multi-stage builds; context is `examples/` |
| `compose.yaml` | Includes Kafka, PostgreSQL and LGTM from `_infra`; adds both services |

Compose supplies `ORDER_SERVICE_URL`, `CAMEL_COMPONENT_KAFKA_BROKERS`, the datasource settings and the OTLP endpoint as environment variables. Traces, metrics and logs reach `http://lgtm:4318` with `service.name` `order-service` and `notification-service`.
