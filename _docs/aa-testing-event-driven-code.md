---
title: "Appendix A: Testing event-driven code"
order: 36
marker: "A"
label: "Appendix A"
part: appendices
permalink: /docs/aa-testing-event-driven-code/
description: "Event-driven code fails by doing too much, too little or in the wrong order, and a test that only waits for the first record sees none of that. Real brokers from Dev Services, a check proved by breaking the code it guards, topic assertions that wait out the window, ordering and concurrency tests, Camel route tests with AdviceWith, registry contracts and end-to-end checks."
duration: "45 minutes"
---

A synchronous service fails loudly: an exception, a status code, a stack trace. An event-driven one fails by omission. The notification that never left, the second one that did, the record keyed by the wrong field, the event applied before the one it depends on. A test that sends a message and waits for an answer passes on all of these.

Event-driven tests run on a real broker.

{% include excalidraw.html file="aa-bite-cycle" alt="A green suite, a patch that alters one guard on a copy, a run of the broker-backed class, and exactly one named test failing, after which the copy is dropped; a dashed arrow notes that a test that stays green is not guarding what it names" caption="Figure A.1: A check is proved by breaking the code it guards" %}

## Real brokers in tests

A mock of a broker answers the way its author expects, which is the one thing a test of messaging cannot afford. Quarkus Dev Services starts the real dependency for a Quarkus test and wires its address into the configuration, so the tests meet a real Kafka and a real PostgreSQL. The Kafka image is the compose stack's, and PostgreSQL has the stack's version from the stock image:

```properties
quarkus.kafka.devservices.image-name=docker.io/apache/kafka:4.3.2
quarkus.datasource.devservices.image-name=docker.io/library/postgres:18.6
```

Camel reads `camel.component.kafka.brokers`, which Dev Services does not fill, so the test profile copies the address across: `%test.camel.component.kafka.brokers=${kafka.bootstrap.servers}`.

All the tests of one application share its topics. Each test therefore uses order ids of its own and filters what it reads by them, and a topic that needs an exact record order gets one partition (`quarkus.kafka.devservices.topic-partitions.<topic>=1`).

A dependency that is not Kafka or a database is served by the test application itself. The route fetches the order over HTTP, and in `src/test` a JAX-RS class answers on the test port:

```java
@Path("/orders")
public class StubOrderService {

    static final Map<String, OrderStatus> STATUS = new ConcurrentHashMap<>();

    @GET
    @Path("{id}")
    public RestResponse<FetchedOrder> get(@RestPath String id) {
        return RestResponse.ok(new FetchedOrder("C-36", new BigDecimal("59.90"),
                STATUS.getOrDefault(id, OrderStatus.PLACED)));
    }
}
```

The `%test` profile points `order-service.url` at `http://localhost:8183`. A test decides what the source answers by writing to the map, and nothing outside the JVM has to run.

## A check that bites

The route has three guards: a repeated event id is dropped, a cancelled order gets no notification, and the notification is keyed by the order id. Each guard is a line a refactoring can delete while every test stays green, if the test for it is weak.

So a guard is proved the other way round, which is mutation testing: DeMillo, Lipton and Sayward proposed it in 1978, and PIT automates it for the JVM. A tool generates its mutants; here three hand-written patches are the mutants. Each patch removes a guard from a copy of the module, the tests run against the copy, and the named test must fail:

```diff
{% raw %}
@@ -27,7 +27,6 @@
                 .toD("{{order-service.url}}/orders/${header.orderId}?bridgeEndpoint=true")
                 .unmarshal().json(FetchedOrder.class)
-                .filter(simple("${body.status} != 'CANCELLED'")) // GUARD
                 .bean("notificationLog", "record(...)")
{% endraw %}
```

Each patch is an altered copy of the route, applied under `target/bites/` and never to the source. `verify.sh` runs the broker-backed class on each copy and reads the Surefire reports: the set of failed tests must equal the one test that names the guard. A copy that fails two tests means a test is coupled to something it does not name. A copy that fails none means a guard is unprotected.

The harness costs one Maven run per patch, about the length of the suite itself, so it runs from `verify.sh` and not on every save. Write a bite for the guards a wrong release would hurt most: deduplication, the opt-out, the key, the retry policy.

## Asserting on topics

The output of an event-driven component is a topic, and a topic assertion has two shapes that look alike.

The first match returns as soon as a record satisfying a predicate arrives:

```java
ConsumerRecord<String, String> first = companion.consumeStrings()
        .fromTopics("edp.orders.notified",
                records -> records.filter(r -> r.value().contains("O-3601")).select().first())
        .awaitCompletion(Duration.ofSeconds(15)).getFirstRecord();
```

It is the right shape for "the notification arrives", and it is fast. It cannot see anything that arrives after the record it stopped at, so it passes on a route that notifies twice.

The drain reads for the whole window and returns everything:

```java
companion.consumeStrings()
        .withGroupId("drain-" + System.nanoTime())
        .withOffsetReset("earliest")
        .fromTopics("edp.orders.notified", Duration.ofSeconds(8))
        .awaitCompletion().getRecords();
```

The `Duration` overload is the one that ends. `fromTopics(topic)` with a timeout on `awaitCompletion` has no end condition and fails with "no completion". Counting what the drain returns turns "notified exactly once" and "not notified at all" into assertions. The second is a statement about the absence of a record, and only a read that waits can make it.

{% include excalidraw.html file="aa-drain-vs-first-match" alt="Two records for one order on the output topic and the end of the wait window; the first match stops at record 1 and passes, the drain for the whole window counts two records, expects one and fails" caption="Figure A.2: Only a read that waits can see a second record" %}

The window has to outlast the route, and every drain costs the whole window, so a suite pays it once per negative assertion. Keep the window as short as the route's slowest legitimate path allows, and use the first match wherever the question is only whether something arrives. KafkaCompanion is the Quarkus test client for this (`quarkus-test-kafka-companion`, with `@InjectKafkaCompanion`). With Kafka Dev Services it reuses the broker the application talks to, so no second broker starts.

## Ordering tests that cannot pass by luck

An ordering test that passes because the records happened to arrive in order proves nothing, and on a three-partition topic it is easy to write one. Two habits prevent it.

The oracle must not be the code under test. Compute the partition a key must land on with Kafka's own `Utils.murmur2` in a test helper, publish six different order ids and compare each record's partition with the prediction. A key that hashes everything to one partition would pass a test that compared the application with itself.

The arrival order must be written down. Send a delivered event before a scheduled one through a producer of the test's own and assert the order's final status. Waiting for the application to produce the interleaving it needs is how a test of ordering becomes a test of timing.

## Concurrency tests and their limit

A race has to be provoked. An inbox test starts two threads that apply the same event id, releases them together with a `CyclicBarrier` and repeats for 20 rounds, asserting after each that the effect happened once and the stock fell by one.

A green run of such a test means 20 rounds produced no failure. It does not mean there is no race. The inbox is correct because of what the database does with `INSERT ... ON CONFLICT DO NOTHING` on a primary key, and the test exercises that guarantee without proving it. A test profile that switches the inbox off and applies the same duplicate twice supplies the comparison. A concurrency test is a bite: a race that cannot be made to fail on a broken version should not be trusted on the working one.

## Camel route tests

The routes are tested as the application runs them, with the dependencies they cannot have in a unit test replaced. `camel-quarkus-junit` provides `CamelQuarkusTestSupport`, and `AdviceWith` rewrites an existing route without touching production code: the Camel Quarkus documentation recommends defining routes in `src/main/java` and advising them in tests.

```java
@QuarkusTest
@TestProfile(AdviceProfile.class)
class OrderPlacedAdviceTest extends CamelQuarkusTestSupport {

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    @Test
    void routeWithoutKafka() throws Exception {
        AdviceWith.adviceWith(context, "notify-order", route -> {
            route.replaceFromWith("direct:in");
            route.weaveByToUri("kafka:edp.orders.notified*").replace().to("mock:out");
        });
        context.start();
        // send to direct:in, then assert on getMockEndpoint("mock:out")
    }
}
```

The consumer becomes `direct:in`, the producer becomes a mock endpoint (`camel-quarkus-mock`), and the test sends an exchange by hand and asserts on the mock: no broker, no consumer group, no wait. This is the quick test for what the route does to one exchange, the key header and the guard among them. The broker-backed class answers a different question: what a consumer group does with real records. The route needs both.

Advice rewrites the route of the running application, so the advised class has a test profile of its own. The profile makes Quarkus restart the application for that class, and the classes that follow see the route as written.

## Event contracts in the registry

A schema is a contract between a producer and consumers that do not deploy together. Its test belongs against the registry the producer uses, because the rule lives there. The registry test starts Apicurio Registry as a Dev Service, sets the `BACKWARD_TRANSITIVE` rule, registers version 1 and version 2 of the order event, and asserts that a version 3 without the default is refused with HTTP 400 and that the artifact still holds two versions. A first version on an empty artifact is accepted whatever it contains, so a contract test registers the history before it tests the candidate. Apicurio applies a rule by checking a new version against the versions already registered.

## End to end: verify.sh and LGTM

The tests above run inside one JVM. What crosses processes needs a stack: a connector that has to read a replication slot, a trace that has to reach Tempo, a consumer that has to survive a recreate. For these, `verify.sh` starts the compose stack fresh, checks the observable effect and keeps a negative witness for each guard, a check that fails when the guard is removed.

Observability follows the same split. Spans and log correlation are asserted in process with an in-memory exporter, and meters are read from a `SimpleMeterRegistry`, where the test controls everything. `verify.sh` then asks Tempo and Loki, and for metrics the Prometheus query API and Grafana's rules API, over HTTP, which covers the whole path from the application through the collector. The in-process test finds a wrong attribute at once; the end-to-end check finds a collector that drops it.

## Build, run, observe

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/aa-testing-event-driven-code
./verify.sh
```

Dev Services starts a fresh Kafka for the module's tests and for each altered copy, and no compose stack runs. Watch each altered copy fail the one test that names its guard while the first-match test stays green; the run ends with the pass count, `7/7 checks passed`.

Try one change and rerun:

- Add a fourth patch to `bites/` that drops `breakOnFirstError=true` and name the test you expect to fail.
- Shorten the drain window to one second and see which negative assertion stops being able to fail.
- Delete `StubOrderService` from `src/test` and see which tests lose their source.

> **🤖 Explore with your agent**
> "In examples/aa-testing-event-driven-code, write a bites/ patch that makes OrderPlacedNotificationRoute notify a cancelled order, run only the broker-backed class on the altered copy and name the one test that fails. Then change the drain window and say which assertions a shorter window can no longer make."
> Check: the altered copy fails the test that waits for a cancelled order's notification and no other, and the first-match test still passes on a copy that notifies twice.

## References

- Quarkus, [Testing your application](https://quarkus.io/version/3.40/guides/getting-started-testing) and [Dev Services overview](https://quarkus.io/version/3.40/guides/dev-services)
- Quarkus, [Apache Kafka reference guide](https://quarkus.io/version/3.40/guides/kafka): the Kafka Companion library
- R. A. DeMillo, R. J. Lipton and F. G. Sayward, [*Hints on Test Data Selection: Help for the Practicing Programmer*](https://doi.org/10.1109/C-M.1978.218136), IEEE Computer 11(4), 1978: mutation testing
- [PIT](https://pitest.org/): mutation testing for Java and the JVM
- Apache Camel Quarkus, [Testing](https://camel.apache.org/camel-quarkus/3.40.x/user-guide/testing.html): `CamelQuarkusTestSupport` and AdviceWith
- Apicurio Registry, [Registry content rules](https://www.apicur.io/registry/docs/apicurio-registry/3.3.x/getting-started/assembly-intro-to-registry-rules.html) and [compatibility modes](https://www.apicur.io/registry/docs/apicurio-registry/3.3.x/getting-started/assembly-registry-compatibility-modes.html)
- Adam Bellemare, *Building Event-Driven Microservices*, 2nd ed., O'Reilly, 2025, chapter 20: Testing Event-Driven Microservices
- *Enterprise Integration Patterns with Camel*, [Appendix S: Testing Strategies for Camel Quarkus](https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/37-appendix-testing-strategies/)
- The stubbed source: `examples/02-event-notification`, with the [Event notification]({{ '/docs/02-event-notification/' | relative_url }}) page
- Ordering tests: `examples/04-ordering-and-partitioning`, with [Ordering and partitioning]({{ '/docs/04-ordering-and-partitioning/' | relative_url }})
- The inbox race test: `examples/11-idempotent-consumer-and-inbox`, with [Idempotent consumer and inbox]({{ '/docs/11-idempotent-consumer-and-inbox/' | relative_url }})
- The registry rule test: `examples/18-schema-evolution-and-upcasting`, with [Schema evolution and upcasting]({{ '/docs/18-schema-evolution-and-upcasting/' | relative_url }})
- KafkaCompanion: `examples/21-aggregate`, with [Aggregate]({{ '/docs/21-aggregate/' | relative_url }})
- Traces, metrics and logs: `examples/25-tracing-across-async-hops`, `examples/26-metrics-for-event-flows` and `examples/27-correlated-logs-and-traces`, with their pages ([Tracing across async hops]({{ '/docs/25-tracing-across-async-hops/' | relative_url }}), [Metrics for event flows]({{ '/docs/26-metrics-for-event-flows/' | relative_url }}), [Correlated logs and traces]({{ '/docs/27-correlated-logs-and-traces/' | relative_url }}))
