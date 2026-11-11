# 02 Event notification

order-service announces that an order was placed. The event names the order and carries nothing else. notification-service reads the event, fetches the order from order-service over HTTP and stores a Notification built from what it fetched.

Quarkus REST, Hibernate ORM with Panache, Flyway and PostgreSQL in both services; Camel on Quarkus for the Kafka producer route, the Kafka consumer route and the HTTP fetch; OpenTelemetry to the LGTM stack. The shared records come from `examples/domain-model`.

## Services

| Service | Port | Purpose |
|---|---|---|
| order-service | 8080 | Owns the `orders` schema. `POST /orders` places an order and publishes `OrderPlaced`. `GET /orders/{id}` returns its fields and current status. `POST /orders/{id}/cancel` cancels it (409 once it is past PAID) |
| notification-service | 8082 | Owns the `notifications` schema. Consumes `edp.orders.placed`, fetches the order, stores a notification. `GET /notifications?orderId=` lists them |

`OrderPlaced` is `{eventId, occurredAt, orderId}` on `edp.orders.placed`, keyed by order id. order-service publishes it after the order's transaction commits, so the fetch never races the insert.

## Run

```bash
(cd ../domain-model && mvn -B -q install)
mvn -B verify      # order-service and notification-service tests, PostgreSQL and Kafka from Dev Services
./verify.sh        # compose stack plus the observable checks
KEEP=1 ./verify.sh # leave the stack up; Grafana is on http://localhost:3000
docker compose down -v
```

`verify.sh` refuses to start while another stack is running, and stops this one on exit unless `KEEP=1`. Its source-down check stops and starts the two services in a fixed order, so run it on its own.

## Layout

| Path | Purpose |
|---|---|
| `order-service/.../OrderService` | Stores the order and fires the CDI event inside the transaction |
| `order-service/.../OrderPlacedPublisher` | Observes `AFTER_SUCCESS` and hands the event to the route |
| `order-service/.../OrderPlacedRoute` | `direct:order-placed` to the topic as JSON, keyed by order id |
| `notification-service/.../OrderPlacedNotificationRoute` | Kafka consumer with `breakOnFirstError`, redelivery, the `toD` fetch and the store |
| `notification-service/src/test/.../StubOrderService` | Test-scoped JAX-RS resource that stands in for order-service |
| `*/Containerfile` | UBI 10 multi-stage builds; context is `examples/` |
| `compose.yaml` | Includes Kafka, PostgreSQL and LGTM from `_infra`; adds both services |

Compose supplies `ORDER_SERVICE_URL`, `CAMEL_COMPONENT_KAFKA_BROKERS`, the datasource settings and the OTLP endpoint as environment variables. Traces and logs reach `http://lgtm:4318` with `service.name` `order-service` and `notification-service`.
