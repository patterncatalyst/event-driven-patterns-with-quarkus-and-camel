---
title: "Optimizing subscribers"
order: 6
part: events-between-services
permalink: /docs/06-optimizing-subscribers/
description: "How a Kafka consumer fetches, polls and shares partitions, which settings change drain time and duplicates, and how to measure a subscriber from Camel."
duration: "45 minutes"
---

## The problem

The notification service reads three catalog topics, `edp.orders.placed`, `edp.payments.processed` and `edp.shipping.scheduled`, with three partitions each. After a weekend outage it has a backlog to drain, and every record costs it a template render and a database write. The route works. The questions are how fast it drains, whether it drains each record once, and what happens to the other replicas when one more starts.

The consumer has a handful of settings that answer those questions, and most of them do something other than what their names suggest. Each one below is a Camel option, set from an environment variable, so a run changes one setting and nothing else.

{% include excalidraw.html file="06-fetch-poll-process" alt="A fetch request fills a client buffer, poll returns at most max.poll.records of it, and a batch that takes longer than max.poll.interval.ms to process gets the member removed" caption="Figure 6.1: A fetch fills a buffer; poll hands out a bounded slice" %}

## How the consumer reads

A Kafka consumer does three separate things. It sends fetch requests to the brokers that lead its partitions. It keeps what comes back in a buffer. And the application thread calls `poll`, which returns records from that buffer, and processes them. Camel's Kafka consumer runs this loop on its own thread: the thread polls, hands each record to the route, and polls again.

Two settings govern the fetch, and one governs the poll. A third pair decides when the group considers the thread dead.

## Fetch sizing

`fetch.min.bytes` is the least data the broker should return for a fetch, 1 byte by default. `fetch.max.wait.ms` is how long the broker may hold the request while it waits for that much data, 500 milliseconds by default. The broker answers when the first of the two limits is reached (Strimzi, Optimizing Kafka consumers).

With the defaults, a consumer that has caught up gets an answer as soon as any record arrives, so a steady trickle becomes a steady stream of small fetch requests. Raising `fetch.min.bytes` makes the broker wait to collect more data, so each answer carries more records and the consumer sends fewer requests. The price is latency: a record may wait up to `fetch.max.wait.ms` before it is returned.

This only matters once the consumer has caught up. The broker answers a fetch as soon as `fetch.min.bytes` of data is available, and a consumer with a backlog always has that much waiting.

With 6000 records arriving at 300 per second on one topic, a consumer with the defaults sent about 2,400 fetch requests, and one with `fetch.min.bytes=65536` sent 60. The records arrived at the same rate in both runs, so the larger minimum cut the request rate by a factor of about 40, at the cost of up to 500 ms of delay per record.

## max.poll.records and max.poll.interval.ms

`max.poll.records` limits how many records one `poll` returns, 500 by default. It limits the poll, not the fetch: a fetch can return more records than one poll hands out, and the client keeps the rest in its buffer for the next calls.

The setting matters because of its partner. `max.poll.interval.ms`, 300000 by default, is the longest the client may go between two `poll` calls. When the thread is still busy with a batch after that long, the client leaves the group, the broker reassigns its partitions, and the records of the batch that were not yet committed are delivered again (Strimzi, Optimizing Kafka consumers). The consumer is not slow in the sense of lagging. It is removed, and its work is done twice. Camel does not hide this: the client logs that the poll timeout has expired and that it is leaving the group, and the records of the batch that had not been committed are processed again after it rejoins.

The rule that follows is arithmetic: the time to process `max.poll.records` records must stay well inside `max.poll.interval.ms`. At 50 ms per record, a batch of 500 takes 25 seconds, so an interval of 5 seconds removes the member on every full batch, while a batch of 20 takes one second and never does. The same batch size is harmless when records cost a millisecond. The two numbers have to be sized together, from the cost of one record. Below the limit the poll size does not change throughput: at 5 ms per record, three threads drained the 3000-record backlog in the same time, within 1 percent, with `max.poll.records` at 500 and at 50.

## Concurrency: consumersCount and the partition cap

`consumersCount` starts that many consumer threads inside one Camel route. Each thread is a separate member of the consumer group, so the group divides the partitions among them. Three topics of three partitions give nine partitions, so nine is the most consumers that can read at once. With twelve threads and an even assignor, nine own one partition each and three own none. Extra members are not an error: they cost memory and a place in every rebalance, and they add no throughput (Strimzi, Optimizing Kafka consumers).

The assignor decides how even the split is. Camel's default `RangeAssignor` deals out the partitions of each topic separately, starting from the first member every time. A group that subscribes to three topics of three partitions therefore gives all nine partitions to the same first three members, three each, and the other nine members of a twelve-thread group own nothing. `RoundRobinAssignor` spreads all nine partitions over nine members.

{% include excalidraw.html file="06-concurrency-cap" alt="Under RoundRobinAssignor, nine partitions are each assigned to one of the first nine of twelve consumers; the last three consumers are idle" caption="Figure 6.2: Parallelism stops at the partition count" %}

Draining a backlog of 3000 records at 5 ms of work per record shows both effects. Five repeats of each profile were measured, and the round-robin profile ran in a separate invocation of the same bench. Taking one thread as 1.0, the median drain time of three threads was 0.33, about a third. Nine and twelve threads under the default assignor also took 0.33, because only three members owned partitions. Twelve threads under `RoundRobinAssignor` took 0.12, about nine times faster than one thread. Repeats of one profile differed by less than 3 percent.

The partition count is the ceiling on how many members of one consumer group read at once; above it, a consumer group needs more partitions.

## Rebalances: classic and KIP-848

When a replica joins a group, the partitions are redistributed. With an eager assignor such as Camel's default `RangeAssignor`, the classic protocol uses a group-wide barrier: every member stops, gives up everything it owns, and rejoins, and the assignor then deals the partitions out again. A replica that started a moment ago interrupts all the others, including the partitions that stay where they were.

`groupProtocol=consumer` selects the new protocol from KIP-848. The coordinator on the broker computes the assignment and tells each member only what changes, so a member gives up the partitions that move and keeps reading the rest. The group listing shows which protocol a group uses: its type is Classic or Consumer. When a second replica with two threads joined a first replica with two threads, the first replica gave up all 9 partitions under the classic protocol and fewer than half under the new one (4 or 5 in recorded runs).

{% include excalidraw.html file="06-eager-vs-incremental" alt="Classic protocol: the replica that owns all partitions revokes all of them when a second replica joins. Consumer protocol: it revokes only the partitions that move" caption="Figure 6.3: A joining replica stops the group under the classic protocol" %}

The classic protocol can also rebalance incrementally. With `partitionAssignor` set to `CooperativeStickyAssignor`, replica 1 gave up 4 of its 9 partitions instead of all of them, without the new protocol, a count close to the new protocol's.

## How Quarkus and Camel model it

`NotificationConsumeRoute` is one route over the three topics:

{% raw %}
```java
from("kafka:" + topics + "?groupId={{notify.group}}&clientId={{notify.client}}")
        .routeId("notification-consume")
        .unmarshal().json(NotificationRequest.class)
        .bean("notificationTemplate", "render")
        .bean("notificationWriter", "write");
```
{% endraw %}

The route has no tuning options. `maxPollRecords`, `fetchMinBytes`, `fetchWaitMaxMs`, `maxPollIntervalMs`, `consumersCount`, `groupProtocol` and `partitionAssignor` are component properties in `application.properties`, each written as an environment variable with its default, for example `camel.component.kafka.max-poll-records=${MAX_POLL_RECORDS:500}`. A run changes one variable and recreates the container.

`NOTIFY_GROUP` names the consumer group. Every run uses a new group, which starts from the earliest offset, so each run drains the identical seeded backlog. `NOTIFY_WORK_MS` is a fixed cost per record that stands in for rendering a template.

`NotificationWriter` stores one row per group, topic, partition and offset with an upsert, `ON CONFLICT ... DO UPDATE SET attempts = attempts + 1`. A record delivered a second time updates its row and raises `attempts`, so a duplicate shows up as a number above 1 instead of an extra row. The row also records which consumer thread wrote it.

## Measuring a subscriber

Camel creates its Kafka consumers itself, so Quarkus does not bind their client metrics. `KafkaClientMetricsFactory` is a Camel `KafkaClientFactory` that returns a consumer which registers Micrometer's `KafkaClientMetrics`, and `camel.component.kafka.kafka-client-factory=#meteredFactory` selects it. The metrics reach Prometheus through the OpenTelemetry exporter, for example `kafka_consumer_fetch_manager_fetch_total`, the number of fetch requests.

Two details matter. The consumer must remove its meters in every `close` overload, because Camel does not call the same one on every shutdown path. And the threads of `consumersCount` share one `client.id`, which makes Micrometer drop the meters of every thread after the first, so each consumer gets its own `client_seq` tag.

The broker's view of progress is the lag that `kafka-consumer-groups.sh --describe` prints per partition. Drain time comes from the stored rows, and the timer `edp.notification.event.age` records how long a record waited between its Kafka timestamp and its stored notification.

## Build, run, observe

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/06-optimizing-subscribers
mvn -B verify
./verify.sh
```

`verify.sh` drains 200 records from each of three topics under different consumer settings and prints `36/36 checks passed`. It asserts relations between runs, never durations. `bench.sh` repeats the profiles five times and writes the timings to `results/`.

Try one change and rerun:

- `FIXED_MAX_POLL_RECORDS=500 ./verify.sh`: the run that should not be evicted is evicted like the failing one and the client leaves the group.
- `CLASSIC_ASSIGNOR=org.apache.kafka.clients.consumer.CooperativeStickyAssignor ./verify.sh`: replica 1 gives up 4 partitions under the classic protocol instead of 9.
- `TRICKLE_MIN_BYTES=1 ./verify.sh`: the run meant to send fewer fetch requests sends as many as the default.

## When not to tune

The defaults drain a backlog correctly. Tune when a measurement says so: a lag that does not shrink, a log line that says the client left the group, or a fetch rate that costs the broker more than the traffic is worth. A larger `max.poll.records` or a longer `max.poll.interval.ms` delays the eviction of a slow consumer without making it faster, and threads beyond the partition count sit idle. When the work per record is the problem, make it cheaper or move it out of the poll loop, and when the partition count is the ceiling, change the partition count.

> **🤖 Explore with your agent**
> "In examples/06-optimizing-subscribers, run NotificationConsumeRoute against the seeded backlog with NOTIFY_WORK_MS=50 and MAX_POLL_INTERVAL_MS=5000, once with MAX_POLL_RECORDS=500 and once with 20, and compare the attempts column that NotificationWriter keeps. Then state the largest max.poll.records that stays inside max.poll.interval.ms for a given NOTIFY_WORK_MS."
> Check: with 500 some rows have attempts above 1 and the log shows the client leaving the group; with 20 every row has attempts 1.

## References

- Gregor Hohpe and Bobby Woolf, *Enterprise Integration Patterns*, Addison-Wesley, 2003: [Polling Consumer](https://www.enterpriseintegrationpatterns.com/patterns/messaging/PollingConsumer.html)
- Paul Mellor, Strimzi blog, 2021: [Optimizing Kafka consumers](https://strimzi.io/blog/2021/01/07/consumer-tuning/)
- Strimzi blog, 2023: [Developing Kafka client applications: A simple consumer](https://strimzi.io/blog/2023/11/09/kafka-consumer-client-essentials/)
- Apache Kafka documentation, [Consumer configs](https://kafka.apache.org/43/configuration/consumer-configs/)
- [KIP-848: The Next Generation of the Consumer Rebalance Protocol](https://cwiki.apache.org/confluence/display/KAFKA/KIP-848%3A+The+Next+Generation+of+the+Consumer+Rebalance+Protocol)
- Adam Bellemare, *Building Event-Driven Microservices*, Second Edition, O'Reilly, 2025: Chapter 11, Basic Producer and Consumer Microservices
- Apache Camel, [Kafka component](https://camel.apache.org/components/latest/kafka-component.html)
