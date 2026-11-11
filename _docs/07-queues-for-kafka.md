---
title: "Queues for Kafka"
order: 7
part: events-between-services
permalink: /docs/07-queues-for-kafka/
description: "A consumer group gives each partition to one worker, so one slow job holds back the jobs behind it. A share group lets any worker take any record and acknowledge it one at a time."
duration: "40 minutes"
---

The notification service sends an email for every order placed. The email provider is usually quick, but one customer's mailbox takes eight seconds to answer. Thirty orders arrive on `edp.orders.placed`, which has three partitions, and the slow one is the first record of its partition. Twelve more jobs sit behind it in that partition, and nothing about them is slow.

{% include excalidraw.html file="07-head-of-line" alt="In a consumer group worker-1 owns the partition and the later jobs wait behind the slow one; in a share group worker-1 acquires only the slow job and another worker takes the later jobs of the same partition one at a time" caption="Figure 7.1: Who waits behind one slow job" %}

## Pattern: Competing Consumers

### The problem

A topic's partitions are split among the members of a consumer group, and each partition has exactly one owner in the group. The owner delivers its records one after another. A job that takes eight seconds therefore keeps every later job of its partition waiting for eight seconds, even when other workers are idle. Adding workers beyond the number of partitions does not help: the extra ones own nothing.

Partitioned ordering is what makes this the right design when order per key matters. When the jobs are independent, as emails are, the per-partition order limits parallelism and gives the jobs nothing they need.

### The solution

**Competing Consumers are several workers that take jobs from one channel concurrently, each job going to whichever worker is free.**

Hohpe and Woolf answer the problem of a client that must handle many messages at once by putting several receivers on the same channel, so that each message is taken by one of them and the receivers work in parallel. Kafka's share groups implement it on a partitioned log: partitions may be assigned to several consumers at once, the number of consumers can exceed the number of partitions, and records are acknowledged individually instead of by offset. The broker hands a record to one consumer and locks it. The consumer answers with one of three acknowledgements:

- accept: the job is done and is not delivered again.
- release: the job goes back to the share group and is delivered again, with a higher delivery count.
- reject: the job is archived and is never delivered again.

If the consumer gives no answer before the lock duration ends (30 seconds by default), the lock expires and the record becomes available again. After a number of deliveries (5 by default) the broker archives the record. Kafka states no ordering guarantee for share groups, and the delivery count is approximate.

{% include excalidraw.html file="07-share-acquire-ack" alt="A record is available, then acquired and locked with its delivery count increased, then accepted, released, rejected or released by lock expiry" caption="Figure 7.2: The life of one record in a share group" %}

Delivery is at-least-once. A worker that is stalled for longer than the lock duration loses its lock, and the job becomes available again: any worker, including the same one, may receive it. If the first attempt then completes as well, the job has run twice, so a job that must not run twice needs an idempotent receiver. A worker that is only slow keeps its lock: the Quarkus connector renews the lock of a job still in progress.

### How Quarkus and Camel model it

`notification-service` runs as four replicas, `worker-1` to `worker-4`, in one group. There are more workers than partitions, because a share group splits the partitions among its members like a consumer group does until members outnumber partitions, and only then do two members read the same partition. Every job is an order-placed event, keyed by order id, and the recipient is the order's customer. `EmailGateway` stands in for the provider, and the customer id picks the behaviour:

- `C-SLOW` customers take `GATEWAY_SLOW_MS` (8000 ms by default).
- `C-RETRY` customers fail on the first attempt and succeed on the second.
- `C-BOUNCE` customers fail on every attempt, permanently.

Every other customer takes 50 ms. The gateway receives the attempt number from its caller instead of remembering earlier attempts, so a retry works on whichever worker receives it. `DeliveryService` makes one attempt and writes one `DeliveryRow` to `notifications.delivery_log`: order, worker, partition, offset, delivery count, start and finish time, and outcome (sent, failed or rejected).

The share side is SmallRye Reactive Messaging, not Camel. The Camel Kafka component has no share-group option, so a Camel route cannot join a share group. The Quarkus Kafka connector supports one, and it needs only a channel attribute:

```properties
mp.messaging.incoming.delivery-jobs.share-group.enabled=true
mp.messaging.incoming.delivery-jobs.group.id=notification-share
mp.messaging.incoming.delivery-jobs.share.acquire.mode=batch_optimized
mp.messaging.incoming.delivery-jobs.max.poll.records=1
mp.messaging.incoming.delivery-jobs.max.partition.fetch.bytes=1
mp.messaging.incoming.delivery-jobs.retry-max-wait=1
mp.messaging.incoming.delivery-jobs.poll-timeout=100
```

`ShareDeliveryWorker` is a `@Blocking` method with `@Incoming("delivery-jobs")`. It reads the delivery count from the record, makes the attempt and answers per record:

```java
return switch (outcome) {
    case SENT -> message.ack();
    case FAILED -> message.nack(new IllegalStateException("transient gateway failure"),
            Metadata.of(ShareGroupAcknowledgement.from(AcknowledgeType.RELEASE)));
    case REJECTED -> message.nack(new IllegalStateException("permanent gateway failure"),
            Metadata.of(ShareGroupAcknowledgement.from(AcknowledgeType.REJECT)));
};
```

An `ack` always accepts. Release and reject travel only on a `nack`, and a bare `nack` uses the channel default, which is release.

A share consumer acquires the records of the batch it fetches, and the broker locks them for that consumer. `share.acquire.mode` chooses how many: the default `batch_optimized` acquires the whole fetched record batch, and `record_limit` acquires at most `max.poll.records`. A job published as its own record batch is acquired alone. A burst of jobs written as one producer batch is acquired by one worker as a whole, and the jobs behind a slow one wait for it on that worker. `max.poll.records=1` and a small `max.partition.fetch.bytes` keep each fetch short.

The consumer runs in explicit acknowledgement mode, so it refuses to poll again while a job it handed out is unanswered. The connector retries the refused poll with a back-off that grows up to `retry-max-wait` seconds, 30 by default. With the connector's defaults (a 30-second cap and a 1-second poll timeout) the waits grow to tens of seconds: in a recorded run the slow job's partition took 98 seconds to finish, and only 2 of its 12 later jobs finished before the slow one. A one-second cap and a 100 ms poll timeout bring a worker back within about a second of its last acknowledgement.

The share group's own settings live on the group, not on the consumer. A share group starts at the end of the topic, so `share.auto.offset.reset=earliest` is set on the group resource with `kafka-configs.sh --entity-type groups` before the first job is published. The same command sets the lock duration and the delivery count limit.

The group side is Camel, the book's tool for consumer groups. `GroupDeliveryRoute` reads the same topic with `groupId=notification-workers`, unmarshals the job and calls the same `DeliveryService`. A transient failure is thrown back into the route, whose redelivery policy retries it in place:

{% raw %}
```java
onException(GatewayFailure.class).maximumRedeliveries(3).redeliveryDelay(200).logStackTrace(false);

from("kafka:" + Topics.ORDERS_PLACED + "?groupId=notification-workers&clientId={{delivery.worker}}")
        .routeId("group-delivery")
        .autoStartup("{{delivery.group.enabled}}")
        .unmarshal().json(DeliveryJob.class)
        .bean("groupDelivery", "deliver");
```
{% endraw %}

`DELIVERY_MODE` chooses the path. `share`, the default, enables the SmallRye channel and leaves the route stopped. `group` disables the channel and starts the route. Both modes read the same records with the same gateway and write the same log, so the only difference between the two sets of rows is how Kafka handed out the records.

## Build, run, observe

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/07-queues-for-kafka
mvn -B verify
./verify.sh
```

The Maven build runs the gateway behaviours and both delivery paths against Kafka and PostgreSQL from Dev Services, with the share protocol enabled on the broker. `verify.sh` starts Kafka, PostgreSQL, the LGTM stack and four workers, sets the share group's settings, publishes 30 jobs and prints `27/27 checks passed`.

The jobs are published with producer batching turned off, so every job is its own record batch. Job 1 is the slow customer. It is the first record of its partition, and the script asserts that at least five later jobs share the partition before it relies on the result. Checks 4 to 16 run on the share group's rows, which come from four workers sharing three partitions:

- Twenty-nine jobs are sent, once each, and the bounce job is rejected. The retry job has a failed attempt with delivery count 1 and a sent attempt with delivery count 2, and the bounce job has one rejected attempt with count 1.
- The slow job took at least the gateway's slow time, at least half of the later jobs of its partition finished before it, and the last of them finished no more than 3 seconds after it. At least three of the four workers delivered jobs, and at least one partition was delivered by two or more different workers.
- The bounce job is not delivered again after the lock duration has passed, and `kafka-share-groups.sh --describe` lists four members.

Checks 18 to 27 run the same jobs through the consumer group. The workers are replaced with `DELIVERY_MODE=group`, and once the four members hold their partitions the same 30 jobs are published again. The retry and bounce outcomes are the same. The control relation is the opposite of check 9: no later job of the slow job's partition finishes before the slow job, each partition was delivered by one worker, and exactly three of the four workers delivered, because the fourth owns no partition.

Change one thing and watch a guard fail:

- Export `DELIVERY_MODE=group` before `./verify.sh`. The first run now delivers through the consumer group, so the share relations have nothing to hold on: check 9 fails because no later job finishes before the slow one, and check 16 fails because the share group has no members. Checks 4 to 8 and 10 to 15, which read the share rows, fail with it, and checks 20 to 22 fail because both runs wrote group rows. Check 12 is the share-only counterpart of check 26: it asks for a partition delivered by two or more workers, and a consumer group has none.
- Export `SEED_LINGER_MS=500` before `./verify.sh`. The jobs of each partition are now written as one producer batch. Checks 9 and 12 fail: the worker that receives the slow job's batch acquires all twelve jobs behind it and runs them after the slow one, and every partition is delivered by a single worker.
- Export `RETRY_MAX_WAIT=30` and `POLL_TIMEOUT_MS=1000`, the connector's defaults. Checks 9 and 10 fail: workers wait longer and longer between jobs, so few later jobs finish before the slow one and its partition is done more than a minute after it.
- Export `SHARE_RECORD_LOCK_MS=15000` and `GATEWAY_SLOW_MS=20000`. The slow job now runs longer than the lock, and every check still passes: the connector renews the lock while the job runs, and the job is delivered once, with delivery count 1.
- Export `SHARE_ACQUIRE_MODE=record_limit`. Each worker then acquires at most one record per fetch, whatever the batch. Every check still passes, because with one job per batch the default mode acquires one record as well.

## When not to use it

Use a consumer group when order per key matters. A share group gives no ordering within a partition, so two events of one order can be processed at the same time on different workers. Use it when jobs are independent and an occasional slow job would otherwise hold up the jobs behind it.

A share group also costs time on every job. A worker answers each job before it fetches the next, so in the recorded runs a partition of eight to ten fast jobs took about 2 to 7 seconds through the share group and under 1 second through the consumer group. The partition with the slow job was done in 8 to 9.4 seconds either way; what changed is that most of its jobs no longer waited for the slow one.

The share group gives up the simple offset commit as well: acknowledgement is per record, and every record has a lock, a delivery count and a limit. Work that must run exactly once needs idempotence on top, because a lock that expires produces a second delivery. And if the load is many fast jobs on few slow workers, more partitions in a consumer group may be all you need.

> **🤖 Explore with your agent**
> "In examples/07-queues-for-kafka, run ShareDeliveryWorker and GroupDeliveryRoute on the same 30 jobs with DELIVERY_MODE share and then group, using EmailGateway's C-SLOW customer. Compare which jobs of the slow job's partition finish before it in each mode, and state when a share group is the right choice and when a consumer group is."
> Check: in share mode most later jobs of the slow partition finish first; in group mode none does.

## References

- Gregor Hohpe and Bobby Woolf, *Enterprise Integration Patterns*, Addison-Wesley, 2003: [Competing Consumers](https://www.enterpriseintegrationpatterns.com/patterns/messaging/CompetingConsumers.html)
- Apache Kafka, KIP-932: [Queues for Kafka](https://cwiki.apache.org/confluence/display/KAFKA/KIP-932%3A+Queues+for+Kafka)
- Apache Kafka documentation, Design, "The Share Consumer": [Design](https://kafka.apache.org/43/design/design/)
- Apache Kafka documentation, group configs: [kafka.apache.org/43/configuration/group-configs](https://kafka.apache.org/43/configuration/group-configs/)
- Strimzi blog, [Using Queues for Apache Kafka with Strimzi](https://strimzi.io/blog/2025/08/20/queues-for-kafka/), 2025
- SmallRye Reactive Messaging, [Receiving records](https://smallrye.io/smallrye-reactive-messaging/latest/kafka/receiving-kafka-records/)
- Apache Camel, Kafka component documentation: [camel.apache.org/components/latest/kafka-component.html](https://camel.apache.org/components/latest/kafka-component.html)
