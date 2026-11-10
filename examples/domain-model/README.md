# Shipping domain model

The shared records for the shipping domain used by every example in the book: `Consignment`, `HandlingEvent`, `TrackingHistory`, `ConsignmentStatus`, `Tariff`, `RateQuote`, `Invoice`, `Settlement`, `Carrier`, `CustomsDeclaration`, `Manifest`, `Vessel`, `Sailing`, `PortCall` and `SlotId`. The known carrier codes are `Carrier.FASTLINE` and `Carrier.AIRCO`.

The module is plain Java 25 with no framework. Each record validates in its compact constructor: required text is non-blank, amounts are `BigDecimal` and never negative, a consignment weighs more than zero, and lists are copied defensively. An invalid value never becomes an object.

## Build

```bash
mvn -B verify
```

## Use from an example

Examples depend on `com.patterncatalyst.shipping:domain-model:1.0.0`. Two ways to get it onto the build path:

- Install it into the local Maven repository: run `mvn -q install` in this directory, then build the example.
- Build it inside the example's Containerfile, with `examples/` as the build context: copy `domain-model/` and the example, run `mvn -q install` on the model first, then package the example.
