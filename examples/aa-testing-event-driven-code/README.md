# Appendix A: Testing event-driven code

One Camel route in `notification-service` reads `OrderPlaced` from `edp.orders.placed`, drops a repeated event id, fetches the order over HTTP, skips an order that is CANCELLED and publishes the notification to `edp.orders.notified`, keyed by the order id. `edp.orders.notified` is local to this example and not in the domain model. The notification rows are kept in memory, because the subject is how the route is tested.

## Run

```bash
(cd ../domain-model && mvn -B -q install)
mvn -B verify      # route tests on Kafka from Dev Services
./verify.sh        # the same, then the altered copies
```

No compose stack is started. Order ids are `O-36xx`.

## Tests

| Class | What it does |
|---|---|
| `OrderPlacedRouteTest` | Real Kafka from Dev Services; a first-match read and drains of the whole 8 s window; the order source is `StubOrderService` on the test port |
| `OrderPlacedAdviceTest` | `AdviceWith`: from replaced by `direct:in`, the Kafka producer by a mock; its own `AdviceProfile` so the application restarts for it |

## Tests and checks in detail

`verify.sh` runs `mvn -B -ntp verify`, then copies the module to `target/bites/<name>`, applies `bites/<name>.patch` and runs `OrderPlacedRouteTest` on the copy. `results.py` reads the Surefire reports and prints how many tests ran and which failed. It prints `7/7 checks passed`:

1. The unaltered module runs 4 broker-backed tests and none fails.
2. The advised route test runs 1 test and passes.
3. `duplicate-allowed` fails only the drain that counts notifications for a repeated event.
4. The first-match test stays green on `duplicate-allowed` (the vacuous-check witness).
5. `guard-removed` fails only the drain that waits for a cancelled order's notification.
6. `key-changed` fails only the test that reads the key.
7. The route source is byte-identical after the bites.

## Experiments

| Switch | Effect | Failing checks | Measured result |
|---|---|---|---|
| A new `bites/*.patch` | Adds a bite | none until its check is added to `verify.sh` | - |

There are no environment switches. The bites are the experiments: each is an altered copy and the module is never changed.

## Layout

| Path | Purpose |
|---|---|
| `notification-service/.../OrderPlacedNotificationRoute` | The route under test |
| `notification-service/src/test/.../StubOrderService` | JAX-RS stub of order-service on port 8183 |
| `bites/*.patch` | One removed or changed guard each |
| `results.py` | Failed tests of one class from Surefire XML |
