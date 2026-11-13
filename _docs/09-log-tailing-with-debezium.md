---
title: "Log tailing with Debezium"
order: 9
part: getting-events-out-reliably
permalink: /docs/09-log-tailing-with-debezium/
description: "Reading a database's transaction log turns every committed change into an event without touching the application: slots, snapshots, replica identity, tombstones, heartbeats and the embedded engine."
duration: "50 minutes"
---

The warehouse system keeps its stock in `inventory.inventory_item`, a table another team owns. It has a SKU, a quantity on hand and a timestamp, and nothing in it publishes anything. Order and shipping need to know when a quantity changes, as `InventoryItem` documents on a topic. The table cannot be changed, the legacy application cannot be asked to publish, and the obvious workaround, a job that selects rows changed since the last run, never sees a delete and sees only the last of several updates between two runs.

{% include excalidraw.html file="09-wal-publication-slot" alt="Writes to the captured table, the uncaptured busy table and the heartbeat table land in the write-ahead log; the publication filters three tables for the connector, and the replication slot records how far it has confirmed" caption="Figure 9.1: The log holds every table's changes; the slot holds the reader's place" %}

## Pattern: Transaction Log Tailing

### The problem

A service needs the changes of data it does not own and cannot modify. Polling the table needs a column that records change, cannot see a deleted row and reads the table the application is writing. A trigger that copies changes into another table puts work inside the legacy application's transactions. What is needed is a record of every committed change, in commit order, produced by the database itself.

### The solution

**Transaction Log Tailing reads the database's own transaction log and publishes each committed change as an event, without the application taking part.**

Every relational database writes each change to a log before it changes the table, to recover after a crash. PostgreSQL's is the write-ahead log (WAL). With logical decoding the server turns the committed part of it into row-level changes for a client that asks. A replication slot is the server's record of one such client: it remembers the last position the client confirmed, and the server keeps all WAL after that position, even while the client is gone. A publication names the tables whose changes the client receives. Debezium is the client: it reads the slot, turns each change into an envelope and writes it to Kafka.

An envelope has four parts that matter here: `op` (`r` for a row read during a snapshot, `c`, `u` and `d`), `before`, `after` and a `source` block with the log position (`lsn`) and transaction id. The connector starts with a snapshot of the captured tables, published as `r` events, and then streams from the log. Richardson describes the pattern for the inserts of an outbox table; here the log carries the application's own tables, the case Bellemare describes for integrating an existing system.

The events come in commit order and nothing in the application changes. But they describe rows, not business facts, and the slot holds the log on disk until the reader confirms it, so a reader that stops makes the database keep its log indefinitely.

{% include excalidraw.html file="09-snapshot-then-stream" alt="An initial snapshot publishes one r event per row, streaming from the slot follows, and an insert into the signal table starts an incremental snapshot that reads rows in chunks while streaming continues" caption="Figure 9.2: A snapshot gives the table's state once; the log keeps it current; a signal reads it again" %}

### How Quarkus and Camel model it

Kafka Connect runs the connector. `inventory-service` does not call Debezium; it reads the topic the connector writes, with Camel. The connector is registered as JSON with `PUT /connectors/inventory-cdc/config`:

```json
"slot.name": "edp_inventory",
"publication.name": "edp_inventory",
"publication.autocreate.mode": "filtered",
"table.include.list": "inventory.inventory_item,inventory.dbz_signal,inventory.dbz_heartbeat",
"signal.data.collection": "inventory.dbz_signal",
"heartbeat.interval.ms": "5000",
"heartbeat.action.query": "INSERT INTO inventory.dbz_heartbeat (id, ts) VALUES (1, now()) ON CONFLICT (id) DO UPDATE SET ts = EXCLUDED.ts",
"transforms": "items,signals,heartbeats",
"transforms.items.regex": "edp\\.inventory\\.inventory_item",
"transforms.items.replacement": "edp.inventory.item-changes"
```

The `signals` and `heartbeats` transforms are the same `RegexRouter` for the two helper tables.

The slot is named for its reader, one slot per reader: a second reader of the same table gets its own slot and position. A `filtered` publication holds only the listed tables. Debezium names a table's topic after the server, schema and table, which leaves the `edp.<domain>.<event>` convention, so a `RegexRouter` renames it to `edp.inventory.item-changes`, and one for each helper table renames `dbz_signal` and `dbz_heartbeat`, whose names carry an underscore, to `edp.inventory.signals` and `edp.inventory.heartbeats`. The values are JSON without schemas, and the key columns are the record key.

`InventoryItemDocumentRoute` reads that topic and republishes each row as an `InventoryItem`, the record from the domain model, keyed by SKU on `edp.inventory.items`:

{% raw %}
```java
from("kafka:" + Topics.INVENTORY_ITEM_CHANGES + "?groupId=inventory-documents")
        .routeId("item-changes-to-documents")
        .autoStartup("{{cdc.connect.enabled}}")
        // Debezium follows every delete event with a record that has a key and no value.
        .filter(body().isNotNull())
        .unmarshal().json(ChangeEnvelope.class)
        .setHeader("sku", simple("${body.sku}"))
        .setHeader("cdcMode", constant("connect"))
        .bean("itemDocuments", "fromEnvelope")
        .to("direct:publish-document");
```
{% endraw %}

`ChangeEnvelope` and `LegacyItemRow` keep the legacy column names (`item_sku`, `qty_on_hand`) and ignore the rest of the envelope. `ItemDocuments` is the one place the legacy row becomes the domain's `InventoryItem`, so a change to the legacy schema lands in two small records and one method. `DocumentPublishRoute` sends the document with the SKU as key. The topic is compacted: the latest document per SKU is the state, and a late consumer reads the current stock without replaying every change.

**Deletes.** A delete is two records on the change topic: the `d` envelope and, by default (`tombstones.on.delete=true`), a second record with the same key and no value, which compaction uses to remove the key. The document topic needs one tombstone. The route maps the `d` envelope, keyed from `before`, to a missing document, which `DocumentPublishRoute` publishes as a tombstone, and the `filter` drops Debezium's own record, which has no body to unmarshal.

**What `before` holds.** PostgreSQL logs the old row only as far as the table's replica identity says. Under the default that is the primary key: an update carries no old row, and a delete carries the key with placeholders in the other columns. `ALTER TABLE ... REPLICA IDENTITY FULL` logs the whole old row, at the cost of a larger log. The documents here need only the key; a consumer that audits what a quantity was needs `FULL`.

**The slot and the heartbeat.** The connector confirms a position when an event for a captured table arrives. When the captured table is quiet and another table in the database is busy, the log grows with changes the connector never receives, the slot stays where it was, and so does the WAL behind it. `heartbeat.interval.ms` emits a heartbeat record on a timer, but with a filtered publication that alone does not release the log. `heartbeat.action.query` also runs a statement on the timer. When it writes a table that is in the publication, the change comes back through the slot and the connector confirms a position beyond the burst. `lsn.flush.mode=connector_and_driver` also confirms positions without a heartbeat table. The helper table's own events go to a topic nothing reads, `edp.inventory.heartbeats`. The heartbeat record itself goes to `edp.inventory.connector-heartbeats`, named by `topic.heartbeat.name`.

**Incremental snapshots.** To read a table again, for a consumer that lost its state, the connector watches a signal table. An insert of an `execute-snapshot` row names the tables and starts an incremental snapshot: the table is read in chunks by primary key while streaming goes on. A row read during a chunk is dropped if a change for the same key was streamed in the same window, so the replay cannot overwrite a newer change with an older one.

{% include excalidraw.html file="09-envelope-to-document" alt="Update and delete envelopes enter InventoryItemDocumentRoute, which becomes a document for an update and one tombstone for a delete; Debezium's own tombstone is dropped" caption="Figure 9.3: Camel maps the envelope; the delete is published once, not twice" %}

**Embedded Debezium.** Kafka Connect is a second system to run. Debezium is also a library, and the Camel Debezium PostgreSQL component runs it inside the service. `EmbeddedCdcRoute` reads the same table with `debezium-postgres:`, with its own slot and publication, and keeps its offsets in a file:

{% raw %}
```java
from("debezium-postgres:inventory-embedded"
        + "?databaseHostname={{cdc.pg.host}}&databasePort=5432"
        + "&databaseUser={{cdc.pg.user}}&databasePassword={{cdc.pg.password}}&databaseDbname=edp"
        + "&topicPrefix=embedded&pluginName=pgoutput"
        + "&slotName=edp_inventory_embedded&publicationName=edp_inventory_embedded"
        + "&publicationAutocreateMode=filtered&tableIncludeList=inventory.inventory_item"
        + "&offsetStorageFileName={{cdc.embedded.offsets}}&offsetFlushIntervalMs=1000")
        .routeId("embedded-cdc")
        .autoStartup("{{cdc.embedded.enabled}}")
        .to("direct:embedded-row");
```
{% endraw %}

The body of each exchange is the `after` row, not an envelope, and the operation and key are headers. A second route maps it:

{% raw %}
```java
from("direct:embedded-row")
        .setHeader("sku").simple("${header.CamelDebeziumKey.getString('item_sku')}")
        .choice()
            .when(header("CamelDebeziumOperation").isEqualTo("d"))
                .setBody(simple("${null}"))
            .otherwise()
                .convertBodyTo(Map.class)
                .bean("itemDocuments", "fromMap")
        .end()
        .to("direct:publish-document");
```
{% endraw %}

A delete has no body, and the old row is in the `CamelDebeziumBefore` header; the key comes from the `CamelDebeziumKey` header.

Both paths end in the same `ItemDocuments` and the same publisher, so they publish the same documents. `CDC_MODE=embedded` starts the engine and stops the Connect route.

The two differ in what they keep. Connect stores offsets in a Kafka topic, has a REST API, and leaves the envelopes on a topic other consumers can replay. The embedded engine has no envelope topic, so only this service sees the changes, and its offsets are a file that must survive a restart: when the file is lost, the engine takes a new snapshot. The engine and a large snapshot run in the service's own memory, and the Debezium library the Camel BOM manages can trail the Connect image.

{% include excalidraw.html file="09-connect-vs-embedded" alt="Kafka Connect reads slot edp_inventory and writes envelopes to a topic that a Camel route reads; the embedded engine reads its own slot inside the service and publishes the same documents" caption="Figure 9.4: Two readers, two slots: each one holds its own place in the log" %}

## Build, run, observe

```bash
(cd examples/domain-model && mvn -B -q install)
cd examples/09-log-tailing-with-debezium
mvn -B verify
./verify.sh
```

`verify.sh` registers the connectors, seeds a legacy table and prints `35/35 checks passed`. It needs `jq` on the host. Watch inserts, updates and deletes of the legacy table arrive as documents with the domain's names, and the retained WAL fall when the heartbeat runs.

Try one change and rerun:

- Remove `.filter(body().isNotNull())` from `InventoryItemDocumentRoute`: each delete's tombstone logs a failed change.
- Delete the `ALTER TABLE ... REPLICA IDENTITY FULL` line in `verify.sh`: a delete's `before` carries the key and not the old quantity.
- Remove `heartbeat.action.query` from `connect/inventory-cdc.json`: the burst stays in the log after the wait.

## When not to use it

Log tailing publishes what the log contains, which is the legacy schema. Every consumer of the change topic is coupled to column names and types that the table's owner can change without telling anyone. A mapping route limits the coupling to one place, and it still breaks when a column is renamed. When the application can be changed, write an event to an outbox in the same transaction and capture that: the event is then a designed business fact, not a row.

The pattern also puts a reader inside the database's recovery machinery. A slot that stops advancing makes PostgreSQL keep its WAL until the disk fills. Every slot needs an owner who watches `pg_replication_slots`, a heartbeat for a quiet table in a busy database, and a decision for when the reader is gone for good: drop the slot. It is also specific to one database, with different settings and failure modes on MySQL, SQL Server and Oracle. Without access to the log, as on some managed databases, polling or a change table is what remains. A table without a primary key produces no update or delete events unless its replica identity is `FULL`: PostgreSQL rejects an UPDATE or DELETE on such a table once it is in a publication that publishes them.

> **🤖 Explore with your agent**
> "In examples/09-log-tailing-with-debezium, trace one update of inventory.inventory_item through ChangeEnvelope and InventoryItemDocumentRoute to the document on edp.inventory.items, then do the same with EmbeddedCdcRoute under CDC_MODE=embedded. Apply the rule that each reader owns one slot and a slot nobody advances keeps WAL forever: list the slots on the database and say what each one retains."
> Check: the Connect path and the embedded path publish the same sku:qty set, and each slot name in pg_replication_slots belongs to exactly one reader.

## References

- Chris Richardson, [Pattern: Transaction log tailing](https://microservices.io/patterns/data/transaction-log-tailing.html) and [Pattern: Transactional outbox](https://microservices.io/patterns/data/transactional-outbox.html), microservices.io
- Adam Bellemare, *Building Event-Driven Microservices*, 2nd ed., O'Reilly, 2025, chapter 6: Integrating Event-Driven Architectures with Existing Systems
- Alex Lawrence, *Message- and Event-Driven Systems with CQRS and Event Sourcing*, Addison-Wesley, 2026, chapter 9: Composing Data from Different Sources
- Debezium, [Connector for PostgreSQL](https://debezium.io/documentation/reference/stable/connectors/postgresql.html): snapshot modes, replica identity, WAL disk space, heartbeats, incremental snapshots
- Debezium, [Signaling and notifications](https://debezium.io/documentation/reference/stable/configuration/signalling.html)
- Debezium, [New record state extraction](https://debezium.io/documentation/reference/stable/transformations/event-flattening.html): the flattening transform, an alternative to mapping the envelope in a route
- PostgreSQL documentation, [Logical replication publications](https://www.postgresql.org/docs/18/logical-replication-publication.html): replica identity
- PostgreSQL documentation, [Logical decoding concepts](https://www.postgresql.org/docs/18/logicaldecoding-explanation.html) and [pg_replication_slots](https://www.postgresql.org/docs/18/view-pg-replication-slots.html)
- Apache Camel, [Debezium PostgreSQL component](https://camel.apache.org/components/4.22.x/debezium-postgres-component.html)
