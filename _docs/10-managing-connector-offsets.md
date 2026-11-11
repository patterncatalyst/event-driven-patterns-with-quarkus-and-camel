---
title: "Managing connector offsets"
order: 10
part: getting-events-out-reliably
permalink: /docs/10-managing-connector-offsets/
description: "A source connector remembers its place in the database log. Stopping it, reading that place, moving it forward, resetting it and recovering a lost replication slot, with what PostgreSQL does to each."
duration: "45 minutes"
---

A connector tails the `inventory_item` table of a legacy database and publishes every change to `edp.inventory.item-changes`. One night someone loads a hundred thousand rows into a neighbouring table, a bad batch of updates corrupts stock quantities, and the connector is stopped for a day for maintenance. Each of these ends with the same question: where does the connector start reading when it runs again, and who decides?

{% include excalidraw.html file="10-offset-lifecycle" alt="A running connector is stopped with PUT stop; while stopped its offsets can be altered with PATCH or reset with DELETE, and PUT resume starts it again; PATCH or DELETE while running is refused with HTTP 400" caption="Figure 10.1: Offsets change only while the connector is stopped" %}

## Where a source connector keeps its place

A source connector has no consumer group. Kafka Connect stores its position in an offsets topic, here `edp-connect-offsets`, as one record per source partition. For the PostgreSQL connector the source partition is the logical server name, which is the connector's `topic.prefix`:

```bash
curl -s localhost:8083/connectors/inventory-cdc/offsets
```

```json
{"offsets":[{"partition":{"server":"edp"},"offset":{"lsn":29748248,"txId":762,"ts_usec":1794407704219520}}]}
```

`lsn` is a position in PostgreSQL's write-ahead log (WAL), written as a bigint. After streaming starts the offset also carries `lsn_proc`, `lsn_commit` and `messageType`. The Debezium documentation does not list these keys; they come from the connector's source. The record on the offsets topic has the key `["inventory-cdc",{"server":"edp"}]` and the same offset as its value.

Connect writes the offsets topic on a timer, `offset.flush.interval.ms`, which defaults to 60 seconds. Until the next flush, a GET returns the previous position, and a Connect process that dies between flushes restarts from it, so the changes after it arrive a second time. A consumer of the topic has to tolerate duplicates in that case anyway. The example sets one second so that every read matches the connector's real position.

## Stopping, listing, altering, resetting

The offsets API of Kafka Connect (KIP-875) has three verbs on `/connectors/{name}/offsets`: GET lists, PATCH alters and DELETE resets. PATCH and DELETE are accepted only when the connector is stopped. A running connector refuses both with HTTP 400 and a message that names the `STOPPED` state. `PUT /connectors/{name}/stop` is the way there: it stops the connector and its tasks, releases their resources and reports state `STOPPED` with an empty task list. `PUT /connectors/{name}/resume` starts it again.

```bash
curl -X PUT localhost:8083/connectors/inventory-cdc/stop
curl -X PATCH -H 'Content-Type: application/json' localhost:8083/connectors/inventory-cdc/offsets \
  -d '{"offsets":[{"partition":{"server":"edp"},"offset":{"lsn":30131256}}]}'
curl -X PUT localhost:8083/connectors/inventory-cdc/resume
```

An alter replaces the stored offset. For this connector `lsn` alone is enough. A reset deletes the stored offset, so the connector behaves as if it had never run: with `snapshot.mode=initial` it takes a snapshot again. Neither call touches the database, and both return before anything has been read, so the effect shows only after the resume.

Deleting a connector with `DELETE /connectors/{name}` does not delete its offsets. A connector created again under the same name finds them and continues.

## Offsets and the replication slot

A PostgreSQL connector has a second position that it does not own. The logical replication slot `edp_inventory` lives in the database and records `confirmed_flush_lsn`, the last position the connector acknowledged. The slot also decides which WAL the server keeps. The offset is where the connector asks to start; the slot is where the server is willing to start.

When a client starts streaming from a slot, PostgreSQL begins at the requested position or at the slot's `confirmed_flush_lsn`, whichever is greater. Two consequences follow, and they are the whole of the behaviour:

- **Altering forward skips changes.** Set the offset to the current end of the WAL and everything written in between is never published. The slot moves up to match once the connector acknowledges.
- **Altering backward does not rewind.** An older offset is clamped to the slot's position, so nothing before `confirmed_flush_lsn` can be asked for again. It is not free of duplicates, though: the altered offset holds only an `lsn`, and the last transaction the connector delivered before the stop arrives a second time, even when the slot had acknowledged it.

{% include excalidraw.html file="10-offset-vs-slot" alt="An offset altered ahead of the slot makes streaming start at the offset and skip the changes in between; an offset altered behind the slot is ignored and streaming starts at the slot" caption="Figure 10.2: The server streams from the greater of the two positions" %}

Debezium has an `offset.mismatch.strategy` option, in technology preview, that can compare the two positions; its default, `no_validation`, trusts the stored offset and does not notice that a slot was recreated.

A reset has a different shape. With `snapshot.mode=initial` the connector reads the table's current rows as snapshot (`r`) events, not the history of how they got there, and then streams from the new slot position. A SKU updated a hundred times arrives once, with its latest quantity. With `snapshot.mode=no_data` a reset takes no snapshot but still streams from the slot's `confirmed_flush_lsn`, so it replays whatever the slot had not yet confirmed. "Nothing is re-delivered" is true only when the slot has no backlog.

An alter is therefore the tool for "skip this bad batch", and a reset is the tool for "rebuild the downstream copy from the table as it is now". Neither can rewind.

## When the slot is lost

While a connector is stopped, nothing advances its slot, and PostgreSQL keeps every WAL file from the slot's `restart_lsn` onward. That includes WAL written by tables the connector does not capture: a bulk load into an unrelated table is retained too. The cost is disk, and `pg_wal_lsn_diff(pg_current_wal_lsn(), confirmed_flush_lsn)` measures it. When the connector resumes it reads the backlog, acknowledges it, and the retained bytes fall.

Setting `max_slot_wal_keep_size` caps the retention. A slot that needs more WAL than the cap allows is invalidated: its `wal_status` becomes `lost`, and the position it recorded no longer exists on the server. Two things then look alike and differ in what Connect reports:

- **The slot is lost.** The connector stays `RUNNING` with an empty task list and logs "Cannot obtain valid replication slot" on a retry loop that lasts about 30 minutes. Nothing reports `FAILED`, so an alert on failed tasks stays silent while nothing is delivered. `slot.max.retries` and `slot.retry.delay.ms` do not shorten the loop.
- **The slot was dropped and the offsets kept.** The task goes `FAILED` with a trace that says the position is no longer available on the server and asks for a snapshot mode. `POST /connectors/{name}/restart?includeTasks=true&onlyFailed=true` restarts the task into the same failure.

{% include excalidraw.html file="10-lost-slot-recovery" alt="A lost slot leaves the connector running with no task; a dropped slot with offsets kept fails the task; both are recovered by stopping, dropping the slot, deleting the offsets and resuming" caption="Figure 10.3: A slot the server cannot use is replaced, not repaired" %}

Neither is fixed by a restart, because the stored position is what is wrong. The recovery is the same for both: stop the connector, wait until the slot is inactive, drop it with `pg_drop_replication_slot`, delete the offsets, and resume. The connector creates a new slot and takes a snapshot. Dropping a slot that is still active fails, and the slot stays active for a moment after `STOPPED` is reported, so the drop waits for `active = f`. Anything written in the gap between the old position and the snapshot is not replayed; the snapshot is the current state.

## How Quarkus and Camel fit in

The connector is configuration, not code. It lives in `connect/inventory-cdc.json` and is registered with `PUT /connectors/inventory-cdc/config`; the Connect API is plain HTTP, and `curl` is what an operator uses. The application's part is to consume the topic, and to stay correct when offsets change.

`ChangeLogRoute` reads `edp.inventory.item-changes` with the Camel Kafka component and hands each Debezium envelope to a bean:

```java
from("kafka:" + Topics.INVENTORY_ITEM_CHANGES + "?groupId=inventory-change-log")
        .routeId("change-log")
        .unmarshal().json(ChangeEnvelope.class)
        .bean("itemChangeRecorder", "record");
```

`ChangeEnvelope` keeps the operation, the row after the change and `source.lsn`. The recorder persists an `ItemChange` Panache entity: `op`, `sku`, `qty`, `lsn` and `received_at`. The table is the observable record of what the connector delivered, and the experiments read it to tell a replay from a skip. The `lsn` column makes a duplicate visible as the same position twice.

Altering and resetting offsets change what the consumer sees: a skip is a gap, a reset is a second snapshot of the same rows. A consumer that cannot tolerate either needs the safeguards of its own chapters, such as an idempotent receiver. This consumer has none, so that the effects stay visible.

## Build, run, observe

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/10-managing-connector-offsets
mvn -B verify
./verify.sh
```

`mvn verify` runs against Kafka and PostgreSQL from Dev Services and checks that envelopes become rows in order with their LSN, once each. `verify.sh` brings up Kafka, PostgreSQL, Connect and the LGTM stack, seeds five SKUs, registers the connector and finishes with 39/39 checks passed. Checks 1 to 6 cover the snapshot, the offset shape and its relation to the slot. Check 7 is the refusal while running. Checks 8 to 14 stop the connector, grow the retained WAL and show the resume delivering every change. The forward alter (checks 15 to 18) skips ten updates and delivers the next; the backward alter (checks 19 to 22) replays nothing older than the slot, but repeats the last change before the stop once; the reset (checks 23 to 26) delivers five snapshot rows and no history. The dropped slot (checks 27 to 30) and the lost slot (checks 31 to 37) show the two failure shapes and recover from each. Check 38 re-reads the table with a signal instead of a reset.

Change one thing and watch a guard fail:

- Export `OFFSET_FLUSH_INTERVAL_MS=60000` before `./verify.sh`, Connect's default. A GET now lags the connector: no offset is stored within 30 seconds of the snapshot, check 4 fails and the run stops at its precondition.
- Kill Connect with `docker kill edp-connect` between flushes under the default interval and start it again. The changes written after the last flush are delivered a second time: 21 updates, 21 duplicates.
- Stop the connector, replace its configuration with `connect/inventory-cdc-no-data.json`, make three updates, delete the offsets and resume. No snapshot row arrives, and the slot's unconfirmed backlog is replayed: 4 `u` rows, the three updates plus a repeat of the last change delivered before the stop, as with a backward alter.
- Alter the offset with the connector running. HTTP 400, and nothing changes.
- Send the incremental snapshot signal instead of resetting. The connector stays running, the offsets are untouched and the five rows arrive again as `r` events: check 38.

## When not to touch offsets

Do not alter an offset to repair a consumer. The change goes into the connector for every reader of the topic, and a skipped batch is a gap in every one of them. A consumer that needs to reprocess reads the topic again from an earlier position with its own group.

Do not reset to rewind. A reset rebuilds from the table as it is, and it discards the connector's history of the table's changes. A downstream system that expects every intermediate update sees only the latest state.

Do not leave a stopped connector stopped. The retained WAL grows until the disk fills or a cap invalidates the slot, and the cap turns a pause into a snapshot. Set `max_slot_wal_keep_size` knowing that its price is a lost slot, and alert on `wal_status` and the retained bytes rather than on failed tasks.

> **🤖 Explore with your agent**
> "In examples/10-managing-connector-offsets, stop the inventory-cdc connector, update SKU-1 ten times, alter its offset to the current WAL position, resume and read inventory.item_change written by ChangeLogRoute and ItemChange. Then delete the offsets and resume, and compare the rows. State when an alter is the right tool and when a reset is."
> Check: the ten updates are missing after the alter, and the reset delivers exactly five `r` rows with the latest quantities and no `u` rows.

## References

- Apache Kafka, KIP-875: [First-class offsets support in Kafka Connect](https://cwiki.apache.org/confluence/display/KAFKA/KIP-875%3A+First-class+offsets+support+in+Kafka+Connect)
- Apache Kafka documentation, [Kafka Connect user guide, REST API](https://kafka.apache.org/43/kafka-connect/user-guide/) and [Kafka Connect configs](https://kafka.apache.org/43/configuration/kafka-connect-configs/)
- Strimzi blog, Kate Stanley, [Managing Connector Offsets](https://strimzi.io/blog/2024/11/04/managing-connector-offsets/), 2024
- Debezium documentation, [Debezium connector for PostgreSQL](https://debezium.io/documentation/reference/stable/connectors/postgresql.html), snapshot modes and WAL disk space
- Debezium documentation, [Signaling and Notifications](https://debezium.io/documentation/reference/stable/configuration/signalling.html), ad hoc incremental snapshots
- PostgreSQL documentation, [Logical Decoding Concepts](https://www.postgresql.org/docs/18/logicaldecoding-explanation.html), [Streaming Replication Protocol](https://www.postgresql.org/docs/18/protocol-replication.html), [pg_replication_slots](https://www.postgresql.org/docs/18/view-pg-replication-slots.html) and [Replication settings](https://www.postgresql.org/docs/18/runtime-config-replication.html)
- Enterprise Integration Patterns with Camel, [Appendix R: Managing Kafka Connect Offsets](https://patterncatalyst.github.io/enterprise-integration-patterns-with-camel/docs/36-appendix-kafka-connect-offsets/): the same REST calls on a file connector
