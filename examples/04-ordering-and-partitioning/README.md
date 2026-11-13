# 04 Ordering and partitioning

`shipping-service` publishes the three shipment events of an order (`ShipmentScheduled`, `ShipmentInTransit`, `ShipmentDelivered`) to `edp.shipping.shipments`, a topic with three partitions. Two replicas of `order-service` share one consumer group, apply each event to the order's status (last write wins) and append every applied event to a history. The record key decides the partition, and the partition decides whether the order survives a stalled consumer. A per-order buffer variant (the Resequencer pattern) puts events back in sequence when the key cannot change.

Quarkus, Camel (Kafka and platform-http), Hibernate ORM with Panache, Flyway, PostgreSQL, SmallRye Health and OpenTelemetry to the LGTM stack. The shared records and topic names come from `examples/domain-model`.

## Services

| Service | Port | Purpose |
|---|---|---|
| `shipping-service` | 8080 | `POST /shipments/{orderId}?carrier=FASTLINE` publishes the three events and returns where the broker put each one |
| `order-1` | 8082 | One `order-service` replica. `GET /orders/{id}/status` and `GET /orders/{id}/history` |
| `order-2` | none | The second replica of the same service and group |

`SHIPPING_PARTITION_KEY` is `order-id` (default), `event-type` or `carrier`. `RESEQUENCE=true` on the order replicas stores each event in `orders.pending_shipment_event` and applies an order's events from its `next_sequence` onward, in sequence order, under a row lock on the order, which is created first, with an insert that does nothing on a conflict, when the order's first event arrives. Events that arrive early wait in the table; a missing event holds that order's later events, and only that order's.

Every event carries `sequence` (1, 2, 3 within the order).

## Run

```bash
(cd ../domain-model && mvn -B -q install)
mvn -B verify      # shipping-service and order-service tests, Kafka and PostgreSQL from Dev Services
./verify.sh        # compose stack, a paused replica, four runs
KEEP=1 ./verify.sh # leave the stack up; Grafana is on http://localhost:3000
docker compose down -v
```

`verify.sh` refuses to start while another stack is running, and stops this one on exit unless `KEEP=1`.

## What verify.sh does

1. Finds, with `kafka-consumer-groups.sh --describe`, which replica owns the partition of the `ShipmentScheduled` record, pauses it with `docker compose pause`, publishes one order, waits until the other replica has applied what it owns, resumes the paused replica and waits for the history to hold three rows. The group is described before and after: no rebalance.
2. Runs that with the order-id key (order kept) and, after publishing six orders with the carrier key and checking that each stays in one partition and that at most two partitions are used, with the event-type key (the order regresses to SHIPPED).
3. Runs the event-type key again with `RESEQUENCE=true`: while the owner of `Scheduled` is paused the other replica holds two events in the buffer and applies none, and the same stall ends at DELIVERED in lifecycle order.

The event-type runs fail loudly, before asserting anything else, if the `Scheduled` and `Delivered` partitions are owned by the same replica.

## Tests and checks in detail

The Maven build runs twelve tests across both services against Kafka and PostgreSQL from Dev Services, with three partitions. Four of them compute the expected partition of every key with Kafka's own murmur2 and compare it with the placement the broker reports, for all three strategies; the others cover arrival order and the buffer, including two replicas that handle the same order at the same moment, repeated 20 times. `verify.sh` starts Kafka, PostgreSQL, the LGTM stack, `shipping-service` and two `order-service` replicas, runs the stalled-replica order under four configurations and prints `28/28 checks passed`. When the Scheduled and Delivered partitions of an event-type run belong to the same replica, the pause cannot change the order in which events are applied, so the script stops with an error instead of reporting a pass. It also records how long each replica was paused and checks that every pause stayed under the 45 second session timeout.

## Experiments

Each row changes a single thing and names the checks that fail.

| Switch | Effect | Failing checks | Measured result |
|---|---|---|---|
| `SHIPPING_PARTITION_KEY=event-type ./verify.sh` | The first run spreads the order across partitions: the three events no longer share the key's partition, and the unpaused replica applies events while the other is paused | 3 and 5; 6, 7 and 9 may fail as well, depending on which replica owns the partition of the order id | - |
| Compare the third and fourth runs | They differ only in `RESEQUENCE=true`: the third ends SHIPPED with Delivered applied first, the fourth ends DELIVERED in lifecycle order | - | - |
| `RESEQUENCE=true ./verify.sh` | The buffer is on in the event-type run as well, so the negative witness stops failing: the order no longer ends SHIPPED | 14, 15 and 16 | - |

## Layout

| Path | Purpose |
|---|---|
| `shipping-service/.../ShipmentPublishRoute` | The publish route: plan the events, key each one, send, return the broker's placement |
| `shipping-service/.../PartitionKeyStrategy` | The three key strategies |
| `order-service/.../ShipmentConsumeRoute` | Reads the stream; applies directly or hands the event to the buffer |
| `order-service/.../ShipmentBuffer` | Locks the order, stores the event, applies the buffered run from `next_sequence` |
| `order-service/.../ShipmentApplier` | Status mapping, last write wins, one history row per event; creates the order's row (PAID) with an insert that does nothing on a conflict, then locks it |
| `order-service/src/main/resources/db/migration` | `orders.order_status` (with `next_sequence`), `orders.order_status_history` and `orders.pending_shipment_event` |
| `compose.yaml` | Creates the topics (3 partitions), then the services and two order replicas |
