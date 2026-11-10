# Order-to-delivery domain model

The shared records for the order-to-delivery domain used by every example in the book. The module is plain Java with no framework. Each record validates in its compact constructor, so an invalid value never becomes an object. Money is `BigDecimal` held at two decimals, rounded HALF_UP.

## Records

- `Order(id, customerId, itemSku, quantity, amount)` and `OrderStatus` (PLACED, PAID, SHIPPED, DELIVERED, CANCELLED). `canMoveTo` allows one step forward; CANCELLED only from PLACED or PAID; DELIVERED and CANCELLED are terminal.
- `InventoryItem(sku, quantityOnHand)`.
- `Payment(id, orderId, amount, status)` and `PaymentStatus` (PENDING, COMPLETED, FAILED, REFUNDED).
- `Shipment(id, orderId, carrier, trackingNumber, status)` and `ShipmentStatus` (PENDING, SCHEDULED, IN_TRANSIT, DELIVERED). The tracking number may be null only while PENDING.
- `Carrier` (FASTLINE, AIRCO), an enum, so an unknown carrier fails at unmarshal.
- `Notification(id, orderId, customerId, eventType)`.
- `Topics`: constants for the nine event catalog topics, the list `Topics.ALL`, and `SHIPPING_SHIPMENTS` for the ordered shipment stream.

Event payload records are not here. Each example defines its own.

## Build

```bash
mvn -B -ntp verify
```

## Use from an example

Examples depend on `com.patterncatalyst.shipping:domain-model:2.0.0`. Install it into the local Maven repository from the repository root, then build the example:

```bash
mvn -B -ntp -f examples/domain-model install
```

An example's Containerfile uses `examples/` as the build context, copies `domain-model/` and the example, installs the model first, then packages the example.
