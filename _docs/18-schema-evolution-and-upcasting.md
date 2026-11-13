---
title: "Schema evolution and upcasting"
order: 18
part: event-sourcing
permalink: /docs/18-schema-evolution-and-upcasting/
description: "Customers can ask for express delivery, and OrderPlaced gains a field. Stored events keep the shape they were written in and are upcast when read; published events change only in ways the schema registry accepts against every earlier version."
duration: "60 minutes"
---

Customers can now ask for express delivery, so `OrderPlaced` gains a `shippingPriority`. Two kinds of reader already hold the old shape. The aggregate loads `OrderPlaced` rows that were written before the field existed and can never be rewritten. Every consumer of `edp.orders.placed` may replay the topic from its first record, and the first records have no `shippingPriority` either. `O-1801` was placed by the previous release, `O-1803` by the current one, and both have to work.

{% include excalidraw.html file="18-upcast-on-read" alt="Two rows in the append-only events table, one stored at schema_version 1 and one at 2; a load copies the payload, the upcaster chain adds shippingPriority STANDARD to the version 1 copy, and the aggregate sees only the current OrderPlaced record; an UPDATE of a stored row is refused by the trigger" caption="Figure 18.1: A stored event is never rewritten; a copy is upcast on every read" %}

## Pattern: Upcaster

### The problem

The event store of chapter 14 is append-only. A trigger refuses `UPDATE` and `DELETE`, because an event is a fact that happened and the history is only worth keeping while nobody edits it. A new field is then a problem with no obvious place to fix it.

Each way out gives something up. Rewriting the old rows with a migration breaks the rule that made the store trustworthy, and it cannot be undone. Teaching the aggregate every shape, one branch per version in every decision, spreads the history of the schema through code that should only know the order. Making every field optional and defaulting it where it is used scatters the same default across the code base. Young's *Versioning in an Event Sourced System* names these options and more in its chapter titles: type based versioning, a weak schema, copy and replace.

### The solution

**An upcaster turns an event stored in an earlier shape into the same event in the current shape, on a copy, when the event is read, so the store keeps what was written and the code sees one shape.**

Each row records the `schema_version` it was written in. The chain holds one step per version, each a small transform from one shape to the next, and a row stored at version 1 passes through every step up to the current version. An unknown version is an error, not a guess. The default a step supplies is a business decision, the priority an order had before customers could choose, so it is written once, next to the step, and the published contract states the same value.

Upcasting happens on every load and costs a copy of the payload. In exchange the stored history stays exactly as it was recorded, and a bug in a step is fixed in code, with nothing to repair.

### How Quarkus and Camel model it

`Upcasters` is an application-scoped bean. The chain is a map from event type and version to a transform of the JSON node, and `toCurrent` applies the steps that lie between the stored version and the current one:

```java
private static final Map<String, Map<Integer, UnaryOperator<ObjectNode>>> CHAIN = Map.of(
        "OrderPlaced", Map.of(1, node -> node.put("shippingPriority", DEFAULT_PRIORITY)));

ObjectNode node = ((ObjectNode) stored).deepCopy();
for (int from = schemaVersion; from < CURRENT; from++) {
    UnaryOperator<ObjectNode> step = CHAIN.getOrDefault(type, Map.of()).get(from);
    if (step != null) {
        node = step.apply(node);
        meters.counter("order.upcasts", "from", String.valueOf(from)).increment();
    }
}
```

`EventStore.load` upcasts each row and only then maps the node to a record, so the aggregate never sees an old shape. The current record refuses a missing field:

```java
record OrderPlaced(Order order, String shippingPriority) implements OrderEvent {
    public OrderPlaced {
        Objects.requireNonNull(shippingPriority, "shippingPriority is missing: ...");
    }
}
```

That constructor is what fails when the chain is skipped, with a message that names the cause. `append` always writes the current `schema_version`, and a command on a stream that starts with a version 1 row appends version 2 events behind it. A stream can hold both shapes for as long as it lives.

The step is plain Java in the store and no Camel type appears in it. Camel belongs to the wire, covered next.

## Pattern: Compatible schema evolution

### The problem

`OrderPlaced` also leaves the service. The outbox of chapter 8 carries it to `edp.orders.placed`, and a consumer such as `notification-service` is deployed on its own schedule. When the producer adds a field, some consumers run the old code and some the new, and the topic holds records of both shapes. A reader must be able to read a record it did not expect.

Documentation and code review do not hold this line. A change that looks harmless to its author can break a reader the author has never seen, and the break shows up in a consumer that fails on a record written months earlier. Bellemare's chapter "Schemas and Data Contracts" and Lawrence's "Evolution of Messages and Message Streams" treat the same problem at book length.

### The solution

**A compatible schema evolution registers every new schema version in a registry that checks it against the earlier versions, so a reader built on the newest schema can read every record ever published.**

Avro resolves a record written with one schema into a reader's schema by field name. A field the writer did not have takes the reader's default, and a reader field without a default is an error. So the rule for adding a field is that it always has a default. The registry enforces it, using a compatibility rule that the owner of the contract sets in the registry. The producer does not choose it.

The rule has two strengths. `BACKWARD` checks a new version against the latest one, and `BACKWARD_TRANSITIVE` checks it against all of them. The second is the one a topic that can be replayed from the start needs, because a replaying reader meets the oldest records.

### How Quarkus and Camel model it

The contract is `order-placed.avsc`. The new field has a default, equal to the one `Upcasters` uses:

```json
{ "name": "shippingPriority", "type": "string", "default": "STANDARD" }
```

Quarkus compiles the schema into the `OrderPlaced` class the code uses. `OrderPlacedPublisher` serializes it inside the command's transaction with the Apicurio Avro serializer, which registers the schema in `edp.orders.placed-value` and writes the schema id into the first bytes of the payload. The outbox row keeps those bytes:

```properties
quarkus.debezium-outbox.payload.type=binary
quarkus.debezium-outbox.payload.column-definition=bytea
quarkus.debezium-outbox.additional-fields=topic:string
```

The connector must not turn the bytes into JSON on the way out. It routes the row by its `topic` column and passes the payload through:

```json
"value.converter": "io.debezium.converters.BinaryDataConverter",
"transforms.outbox.route.by.field": "topic",
"transforms.outbox.route.topic.replacement": "${routedByValue}"
```

{% include excalidraw.html file="18-outbox-avro-bytes" alt="Inside one transaction the service appends OrderPlaced as JSON, serializes it through the Avro serializer, which registers the schema in Apicurio Registry, and writes the bytes to the outbox table; after the commit the Debezium Event Router and BinaryDataConverter move the bytes unchanged to edp.orders.placed" caption="Figure 18.2: The schema is checked where the event is written, inside the transaction" %}

The serializer runs before the commit, so a registry that refuses the schema throws and the append and the outbox row roll back together. Serializing in the connector or in a consumer would find out later and leave an event nobody can publish.

`notification-service` reads with the schema it was built on. A SmallRye channel with the generated class as its parameter type is enough, and Quarkus selects the Apicurio deserializer and the specific reader:

```java
@Incoming("placed")
@Blocking
public void on(OrderPlaced placed) {
    recorder.record(placed, "smallrye");
}
```

A record written without the field arrives with `STANDARD`, filled in by Avro resolution. Quarkus applies that detection to SmallRye channels only, so the Camel route names the deserializer, the registry and the specific reader itself:

{% raw %}
```java
from("kafka:" + Topics.ORDERS_PLACED + "?groupId=notification-camel&autoOffsetReset=earliest"
        + "&valueDeserializer=io.apicurio.registry.serde.avro.AvroKafkaDeserializer"
        + "&additionalProperties.apicurio.registry.url=" + registryUrl
        + "&additionalProperties.apicurio.registry.use-specific-avro-reader=true")
        .routeId("order-placed-reader")
        .autoStartup("{{reader.camel.enabled}}")
        .process(exchange -> recorder.record(exchange.getIn().getBody(OrderPlaced.class), "camel"));
```
{% endraw %}

Both readers end in the same `Recorder`. The route starts with the `camel-reader` profile and has its own consumer group, which reads from the earliest offset. Choose SmallRye when the channel is all the service does, and the route when the record joins other Camel processing. The Avro handling is identical.

## Refusing an incompatible version

The next release, as written by someone in a hurry, removes the default from `shippingPriority`: the field is required, because every order now has one. Against version 2 that is compatible, since a reader on the new schema reads version 2 data, in which the field is present. Against version 1 it is not, because version 1 has no value to give the reader.

{% include excalidraw.html file="18-registry-versions" alt="An artifact with version 1 without shippingPriority, version 2 with the default and a v3 candidate without it; under BACKWARD_TRANSITIVE v3 is checked against versions 2 and 1 and refused; under BACKWARD it is checked against version 2 only and accepted, after which a v3 reader cannot read version 1 records" caption="Figure 18.3: The rule decides which versions can exist" %}

So `BACKWARD` accepts it and `BACKWARD_TRANSITIVE` refuses it. The registry applies a rule when a version is added, to the versions already registered, so version 1 and version 2 are registered before the check means anything: a first version on an empty artifact is accepted whatever it contains.

The refusal happens in two places. A direct registration of the version answers HTTP 400, and the body names the field and says `Incompatible artifact`, without naming the rule. And a service that publishes the bad schema fails at the command:

```text
POST /orders  ->  503  {"reason": "Incompatible artifact: edp.orders.placed-value [AVRO] ..."}
```

The order has no event row, there is no outbox row, nothing reaches the topic and the artifact has no new version. A rule set on one artifact overrides the global rule, so the example sets a global rule from `REGISTRY_RULE`, and the demo that relaxes it changes that value only.

Accepting the broken version has a cost that only shows later. A consumer built on version 3 that rebuilds from the start of the topic meets version 1 records with no `shippingPriority` and nothing to fill in, and Avro fails with `AvroTypeException`. A rebuild that reads every record, as in chapter 17, is where it surfaces.

## Build, run, observe

```bash
cd examples/18-schema-evolution-and-upcasting
mvn -B verify
./verify.sh
```

The Maven build runs 22 tests against PostgreSQL, Kafka and Apicurio Registry from Dev Services. In `order-service` they cover:

- the chain and its default;
- the stored node left untouched;
- an unknown version;
- a mixed stream of version 1 and version 2 rows;
- the append-only trigger;
- an outbox payload that starts with the schema id and decodes to the current record;
- the two rules in the registry, and a first version accepted on an empty artifact;
- Avro resolution with no registry, where a version 2 reader fills the default and a version 3 reader throws;
- a command under the broken release that leaves no row.

In `notification-service` the SmallRye channel and the Camel route each read a version 1 and a version 2 record, and a plain test fails when its copy of `order-placed.avsc` differs from the one in `order-service`. `verify.sh` starts the stack and prints `21/21 checks passed`.

The checks read the effects, release by release:

- With the registry rule read back, the previous release (`ORDER_PLACED_SCHEMA=1`, demo code) places `O-1801` and `O-1802`. Their rows are stored at `schema_version` 1 with no `shippingPriority`, and `edp.orders.placed` holds two records written with registry version 1.
- The current release places `O-1803` with `EXPRESS`: a version 2 row, a second registry version and a third record, written with version 2.
- `GET /orders/O-1801` returns `STANDARD` from a version 1 row, `GET /orders/O-1803` returns `EXPRESS`, and cancelling `O-1801` leaves the stored payload with the same checksum. An `UPDATE` of a stored payload is refused.
- `notification-service` recorded the three priorities, its group has no lag and no deserialization failure is logged.
- Registering version 3 is refused with 400 and the artifact keeps two versions. The broken release (`ORDER_PLACED_SCHEMA=3`) answers 503 for `O-1804`, stores no row, adds no record and no version, and the log shows the refusal.
- Recreated with the `camel-reader` profile, `notification-service` reads the topic from the earliest offset and records the same three priorities.
- `order_upcasts_total` with `from="1"` reaches Prometheus, and a trace and logs reach Tempo and Loki.

Change one thing and watch a guard fail:

- Run `UPCASTERS=off ./verify.sh` (demo code). The chain is skipped, loading `O-1801` fails because the current record refuses the missing field, the cancellation fails with it, and no upcast is counted. The run ends 18/21 with checks 8, 10 and 19 failing. Check 9 passes, because a version 2 row needs no step.
- Run `REGISTRY_RULE=BACKWARD ./verify.sh` (demo code). Version 3 is registered and then published for `O-1804`. The run ends 18/21 with checks 14 to 16 failing. A reader built on version 3 cannot read the version 1 records already on the topic.

## When not to use it

An upcaster cannot invent a fact. When a change alters what an event means, not only what it carries, a default would state something that was never recorded, and a new event type is the correct answer. A chain that grows a step per release is also a cost. After a dozen steps, copying the stream into a new one at the current shape, as Young's copy and replace does, is cheaper than lifting every load.

Skip the work where nothing is read back. A snapshot from chapter 15 holds derived state and is discarded and rebuilt when its schema changes, so it needs no upcaster.

A registry is one more service to run and to keep up. When producer and consumers always deploy together and nothing replays a topic, a compatibility check in the build can do the same job with less.

> **🤖 Explore with your agent**
> "In examples/18-schema-evolution-and-upcasting, load O-1801 and O-1803 and find where Upcasters, OrderPlacedPublisher and order-placed.avsc decide what each reader sees. Set REGISTRY_RULE to BACKWARD and state which schema version is then accepted and which reader fails on which record."
> Check: stored events are upcast on read and never rewritten, published events change only in ways the registry accepts against every earlier version, and a field added later always has a default.

## References

- Greg Young, [*Versioning in an Event Sourced System*](https://leanpub.com/esversioning), Leanpub: chapters "Why Version?", "Basic Type Based Versioning", "Weak Schema" and "Copy and Replace"
- Alex Lawrence, *Message- and Event-Driven Systems with CQRS and Event Sourcing*, Addison-Wesley, 2026, chapter 8: Evolution of Messages and Message Streams
- Adam Bellemare, *Building Event-Driven Microservices*, 2nd ed., O'Reilly, 2025, chapter 4: Schemas and Data Contracts
- Martin Kleppmann and Chris Riccomini, *Designing Data-Intensive Applications*, 2nd ed., O'Reilly, 2026
- Apache Avro, [Specification](https://avro.apache.org/docs/1.12.0/specification/): schema resolution
- Apicurio Registry, [content rule reference](https://www.apicur.io/registry/docs/apicurio-registry/3.3.x/getting-started/assembly-rule-reference.html), [schema compatibility modes](https://www.apicur.io/registry/docs/apicurio-registry/3.3.x/getting-started/assembly-registry-compatibility-modes.html) and [Kafka serializers and deserializers](https://www.apicur.io/registry/docs/apicurio-registry/3.3.x/getting-started/assembly-configuring-kafka-client-serdes.html)
- Quarkus, [Using Apache Kafka with Schema Registry and Avro](https://quarkus.io/guides/kafka-schema-registry-avro)
- Debezium, [Outbox Event Router](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html) (Avro as the payload format) and [Outbox Quarkus extension](https://debezium.io/documentation/reference/stable/integrations/outbox.html)
- Apache Camel, [Kafka component](https://camel.apache.org/components/4.22.x/kafka-component.html)
- Chapter 1: Events, commands and documents, for the permanent contract; chapter 8 for the outbox; chapter 14 for the event store; chapter 15 for snapshots; chapter 17 for the rebuild
