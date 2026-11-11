# 03 Event-carried state transfer and claim check

Two services, one topic pair and one table each.

- order-service (port 8080) stores an Order and publishes `OrderPlaced` and `OrderCancelled` after the transaction commits. Each event carries the whole Order and its status, keyed by order id.
- notification-service (port 8082) upserts `notifications.order_copy` from each topic, with one Camel route per topic, and answers `GET /order-copies/{id}` from that table.

A request may also carry an `attachment`, a large optional part of the order. By default order-service stores it in `orders.claim_check` and the event carries the claim id and size. notification-service fetches the content with `GET /claims/{id}` only when someone asks for `GET /order-copies/{id}/attachment`.

Quarkus REST, Hibernate ORM with Panache, Flyway (one schema per service in the `edp` database), Camel Kafka, SmallRye Health and OpenTelemetry to the LGTM stack. The shared records come from `examples/domain-model`; the event records live in the services.

## Endpoints

| Service | Method and path | Result |
|---|---|---|
| order-service | `POST /orders` | Places an Order, optionally with `attachment`. 201, or 400 for an invalid Order, 409 for a duplicate id, 502 if the producer refuses the event |
| order-service | `POST /orders/{id}/cancel` | PLACED or PAID to CANCELLED. 204, or 409 |
| order-service | `GET /orders/{id}` | The Order and its status |
| order-service | `GET /claims/{id}` | The stored attachment as text |
| notification-service | `GET /order-copies/{id}` | The copy: every Order field, the status and the attachment claim. 404 if no event has arrived |
| notification-service | `GET /order-copies/{id}/attachment` | The attachment, fetched from order-service. 404 without a claim, 503 if order-service is unreachable |

## Configuration

| Setting (environment variable) | Default | Effect |
|---|---|---|
| `ORDER_EVENTS_CARRY_STATE` | `true` | `false` publishes the thin event, the order id alone. The consumer logs it as skipped and the copy answers 404 |
| `ORDER_EVENTS_ATTACHMENT` | `claim-check` | `inline` puts the attachment in the event instead of a claim |
| `camel.component.kafka.max-request-size` | 65536 | The producer's request limit, set low to show the limit. Kafka's producer default is 1048576 |

## Run

```bash
(cd ../domain-model && mvn -B -q install)
mvn -B verify      # order-service and notification-service tests; Kafka and PostgreSQL from Dev Services
./verify.sh        # compose stack plus the observable checks
KEEP=1 ./verify.sh # leave the stack up; Grafana is on http://localhost:3000
docker compose down -v
```

`verify.sh` refuses to start while another stack is running, and stops this one on exit unless `KEEP=1`.

## Layout

| Path | Purpose |
|---|---|
| `order-service/.../OrderService` | Stores the Order and the claim in one transaction |
| `order-service/.../OrderPublisher` | After commit, sends the event into the `direct:` route and turns a producer refusal into 502 |
| `order-service/.../OrderPlacedRoute`, `OrderCancelledRoute` | One route per topic: key by order id, marshal, `kafka:` |
| `order-service/.../ClaimResource` | The claim check's counter |
| `notification-service/.../OrderCopyRoute` and its two subclasses | One consumer route per topic; an event that cannot be applied is logged and skipped |
| `notification-service/.../OrderCopies` | The upsert; an event that moves the status backwards is ignored |
| `notification-service/.../OrderClaims` | The REST client for the on-demand fetch |
| `*/Containerfile`, `compose.yaml` | UBI 10 builds with context `examples/`; compose includes Kafka, PostgreSQL and LGTM |
