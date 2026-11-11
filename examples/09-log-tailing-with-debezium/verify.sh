#!/usr/bin/env bash
# Starts the stack, creates and seeds the legacy inventory table, registers the Debezium connector with the
# Connect REST API and checks what the log tail delivers: the snapshot, then inserts, updates and deletes as
# envelopes and as InventoryItem documents, what `before` holds under each replica identity, the heartbeat
# that keeps a quiet slot from holding WAL, an incremental snapshot while streaming, and the same capture
# from Debezium embedded in the service (CDC_MODE=embedded).
# KEEP=1 leaves the stack running afterwards. Requires jq on the host.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-09

COMPOSE=(docker compose -f compose.yaml)
CHANGES=edp.inventory.item-changes
DOCS=edp.inventory.items
CONNECT=http://localhost:8083
SERVICE=edp-09-inventory
OFFSETS_FILE=/tmp/cdc-offsets.dat
PSQL=(docker exec -e PGPASSWORD=edp edp-postgres psql -h 127.0.0.1 -U edp -d edp -tA)
BURST_ROWS=50000                      # about 1 KB each: roughly 55 MB of WAL on a table nothing captures
THRESHOLD=$((40 * 1024 * 1024))       # retained WAL that counts as "the slot is holding the burst"
TOTAL=0
PASSED=0

cleanup() {
    if [[ "${KEEP:-0}" == "1" ]]; then
        echo "KEEP=1: stack left running; run \`docker compose down -v\` in this directory to remove it"
    else
        "${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
    fi
}
trap cleanup EXIT

check() { # check "<description>" <command...>
    local desc="$1"; shift
    TOTAL=$((TOTAL + 1))
    if "$@" >/dev/null 2>&1; then
        PASSED=$((PASSED + 1)); printf 'PASS %d: %s\n' "$TOTAL" "$desc"
    else
        printf 'FAIL %d: %s\n' "$TOTAL" "$desc"
    fi
}

poll() { # poll <seconds> <command...>: retry every second until the command succeeds
    local end=$((SECONDS + $1)); shift
    until "$@"; do
        (( SECONDS < end )) || return 1
        sleep 1
    done
}

note() { printf 'NOTE: %s\n' "$*"; }  # a measured number for the chapter, not a check
mb() { awk -v b="$1" 'BEGIN {if (b < 1048576) printf "%d B", b; else printf "%.1f MB", b / 1048576}'; }
expect() { [[ "$1" == "$2" ]]; } # expect <actual> <expected>
at_least() { (( $1 >= $2 )); }
# Re-evaluated on every poll: is <wanted> <command...> and ge <wanted> <command...> compare the command's output.
is() { local want="$1"; shift; [[ "$("$@")" == "$want" ]]; }
ge() { local want="$1"; shift; (( $("$@") >= want )); }
fail_loudly() { printf 'PRECONDITION FAILED: %s\n' "$1" >&2; exit 1; }

command -v jq >/dev/null || fail_loudly "jq is required on the host"

kafka() { docker exec edp-kafka "/opt/kafka/bin/$1" --bootstrap-server localhost:9092 "${@:2}"; }
sql() { "${PSQL[@]}" -c "$1"; }
tempo_has_trace() { curl -sf -G "http://localhost:3200/api/search" --data-urlencode "tags=service.name=$1" | grep -q '"traceID"'; }

# Kafka Connect.
register() { curl -sf -X PUT -H 'Content-Type: application/json' --data @"connect/$1.json" "$CONNECT/connectors/$1/config" >/dev/null; }
connector_running() {
    curl -sf "$CONNECT/connectors/$1/status" \
        | jq -e '.connector.state == "RUNNING" and (.tasks | length) >= 1 and all(.tasks[]; .state == "RUNNING")'
}

# Topic readers. Records come back as "key<TAB>value", or "headers<TAB>key<TAB>value" for the documents.
# The console consumer prints a notice on stdout before the first record; every record line holds a tab.
dump_changes() { kafka kafka-console-consumer.sh --topic "$CHANGES" --from-beginning --timeout-ms 5000 --property print.key=true 2>/dev/null | grep -P '\t' || true; }
dump_docs() { kafka kafka-console-consumer.sh --topic "$DOCS" --from-beginning --timeout-ms 5000 --property print.headers=true --property print.key=true 2>/dev/null | grep -P '\t' || true; }
end_offsets() { kafka kafka-get-offsets.sh --topic "$1" --time latest 2>/dev/null | awk -F: '{s += $3} END {print s + 0}'; }

events() { dump_changes | cut -f2 | grep -v '^null$' || true; }  # one change envelope per line, no tombstones
event_count() { events | jq -c "select($1)" | wc -l; }           # event_count '<jq condition>'
has_event() { (( $(event_count "$1") >= 1 )); }
r_count() { event_count ".op == \"r\" and .after.item_sku == \"$1\""; }
changes_tombstones() { dump_changes | awk -F'\t' -v k="$1" '$2 == "null" && index($1, k)' | wc -l; }
doc_tombstones() { dump_docs | awk -F'\t' -v k="$1" '$2 == k && $3 == "null"' | wc -l; }
last_doc() { dump_docs | awk -F'\t' -v k="$1" '$2 == k' | tail -1 | cut -f3; }
incremental_r_counts() { echo "$(r_count SKU-1)$(r_count SKU-2)$(r_count SKU-4)"; } # r events of the three rows nobody touches
doc_values() { dump_docs | awk -F'\t' -v k="$1" '$2 == k' | cut -f3 | jq -r 'select(. != null) | .quantityOnHand' | paste -sd, -; }
# The latest document per SKU, as sku:qty, sorted. An optional argument keeps one capture path (cdcMode header).
latest_docs() {
    dump_docs | awk -F'\t' -v m="${1:-}" 'm == "" || index($1, "cdcMode:" m) {last[$2] = $3} END {for (k in last) print last[k]}' \
        | jq -r 'select(. != null) | "\(.sku):\(.quantityOnHand)"' | sort
}
embedded_docs() { dump_docs | grep -c 'cdcMode:embedded' || true; }
table_set() { sql "SELECT item_sku || ':' || qty_on_hand FROM inventory.inventory_item ORDER BY 1"; }
sets_equal() { [[ "$(latest_docs)" == "$(table_set)" ]]; }
doc_is() { [[ "$(latest_docs "${3:-}" | grep -x "$1:$2" || true)" == "$1:$2" ]]; } # doc_is <sku> <qty> [path]

# PostgreSQL.
replica_identity() { sql "SELECT relreplident FROM pg_class WHERE oid = 'inventory.inventory_item'::regclass"; }
slot_active() { [[ "$(sql "SELECT active FROM pg_replication_slots WHERE slot_name = '$1'")" == t ]]; }
retained() { sql "SELECT coalesce(pg_wal_lsn_diff(pg_current_wal_lsn(), confirmed_flush_lsn), 0)::bigint FROM pg_replication_slots WHERE slot_name = '$1'"; }
slot_below() { (( $(retained "$1") < $2 )); }
publication_tables() { sql "SELECT string_agg(tablename, ',' ORDER BY tablename) FROM pg_publication_tables WHERE pubname = 'edp_inventory'"; }
ordered_lsns() { # the three updates of SKU-2 in the order they arrive, with strictly increasing source.lsn
    events | jq -r 'select(.op == "u" and .after.item_sku == "SKU-2" and (.after.qty_on_hand | IN(21, 22, 23))) | "\(.after.qty_on_hand) \(.source.lsn)"' \
        | awk '{q = q $1 ","; if (NR > 1 && $2 <= prev) bad = 1; prev = $2} END {print (bad ? "unordered" : q)}'
}
healthy() { [[ "$(docker inspect -f '{{.State.Health.Status}}' "$SERVICE")" == healthy ]]; }

echo "Starting the stack"
# Fresh start: remove any containers and volumes left by an earlier run (KEEP=1, an aborted run),
# then create every container and anonymous volume anew.
"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
"${COMPOSE[@]}" up -d --build --force-recreate --renew-anon-volumes --wait

# The legacy table, as a team outside this book owns it: no migrations from the service, a busy table nobody
# captures, the signal table incremental snapshots read, and the heartbeat table.
docker exec -i -e PGPASSWORD=edp edp-postgres psql -h 127.0.0.1 -U edp -d edp -q -v ON_ERROR_STOP=1 <<'SQL'
CREATE SCHEMA inventory;
CREATE TABLE inventory.inventory_item (
    item_sku     text PRIMARY KEY,
    qty_on_hand  integer NOT NULL,
    last_changed timestamptz NOT NULL DEFAULT now());
INSERT INTO inventory.inventory_item (item_sku, qty_on_hand) SELECT 'SKU-' || g, 100 FROM generate_series(1, 5) g;
CREATE TABLE inventory.legacy_audit (id bigserial PRIMARY KEY, note text);
CREATE TABLE inventory.dbz_signal (id varchar(42) PRIMARY KEY, type varchar(32) NOT NULL, data varchar(2048));
CREATE TABLE inventory.dbz_heartbeat (id integer PRIMARY KEY, ts timestamptz);
SQL

register inventory-cdc
register interval-only
echo "Registered the connectors"

check "the connector inventory-cdc and its task are RUNNING" poll 90 connector_running inventory-cdc
check "the replication slot edp_inventory exists and is active" poll 30 slot_active edp_inventory
check "the publication holds the legacy table, the signal table and the heartbeat table, and not the busy table" \
    expect "$(publication_tables)" "dbz_heartbeat,dbz_signal,inventory_item"
check "the documents topic is compacted" \
    expect "$(kafka kafka-configs.sh --describe --entity-type topics --entity-name "$DOCS" 2>/dev/null | grep -c 'cleanup.policy=compact')" 1

echo "Snapshot, then streaming"
check "the snapshot published 5 r events, one per SKU" poll 60 is 5 event_count '.op == "r"'
check "5 documents, and the latest document per SKU matches the table" poll 60 sets_equal

sql "UPDATE inventory.inventory_item SET qty_on_hand = 150, last_changed = now() WHERE item_sku = 'SKU-1'" >/dev/null
check "an update arrives as a u event with the new quantity" \
    poll 60 has_event '.op == "u" and .after.item_sku == "SKU-1" and .after.qty_on_hand == 150'
check "the document uses the domain names and is keyed by SKU" \
    poll 60 is '{"sku":"SKU-1","quantityOnHand":150}' last_doc SKU-1

echo "Deletes under REPLICA IDENTITY DEFAULT"
check "precondition: the table uses REPLICA IDENTITY DEFAULT" expect "$(replica_identity)" d
sql "DELETE FROM inventory.inventory_item WHERE item_sku = 'SKU-5'" >/dev/null
check "a delete arrives as a d event whose before holds the key and not the old quantity" \
    poll 60 has_event '.op == "d" and .before.item_sku == "SKU-5" and .before.qty_on_hand != 100'
check "precondition: Debezium wrote its own tombstone for the deleted key" poll 60 is 1 changes_tombstones SKU-5
sleep 3
check "the delete is published as exactly 1 tombstone, not 2" expect "$(doc_tombstones SKU-5)" 1
check "the service dropped Debezium's tombstone without logging a failure" expect "$(docker logs "$SERVICE" 2>&1 | grep -c 'NoTypeConversionAvailableException' || true)" 0

echo "Deletes under REPLICA IDENTITY FULL"
sql "ALTER TABLE inventory.inventory_item REPLICA IDENTITY FULL" >/dev/null
check "precondition: the table uses REPLICA IDENTITY FULL" expect "$(replica_identity)" f
sql "INSERT INTO inventory.inventory_item (item_sku, qty_on_hand) VALUES ('SKU-6', 60)" >/dev/null
poll 60 doc_is SKU-6 60 || true
sql "DELETE FROM inventory.inventory_item WHERE item_sku = 'SKU-6'" >/dev/null
check "a delete arrives as a d event whose before holds the whole old row" \
    poll 60 has_event '.op == "d" and .before.item_sku == "SKU-6" and .before.qty_on_hand == 60'
sleep 3
check "that delete is also published as exactly 1 tombstone" expect "$(doc_tombstones SKU-6)" 1

echo "Order within a transaction"
sql "BEGIN; UPDATE inventory.inventory_item SET qty_on_hand = 21 WHERE item_sku = 'SKU-2'; UPDATE inventory.inventory_item SET qty_on_hand = 22 WHERE item_sku = 'SKU-2'; UPDATE inventory.inventory_item SET qty_on_hand = 23 WHERE item_sku = 'SKU-2'; COMMIT;" >/dev/null
check "three updates of one row in one transaction arrive as documents in commit order" poll 60 is "100,21,22,23" doc_values SKU-2
check "and their source.lsn values increase in the same order" expect "$(ordered_lsns)" "21,22,23,"

echo "Heartbeat: a quiet slot beside a busy table"
CHANGES_BEFORE=$(end_offsets "$CHANGES")
check "precondition: the interval-only connector and its task are RUNNING" poll 60 connector_running interval-only
sql "INSERT INTO inventory.legacy_audit (note) SELECT repeat('x', 1000) FROM generate_series(1, $BURST_ROWS)" >/dev/null
RETAINED_QUERY=$(retained edp_inventory)
RETAINED_INTERVAL=$(retained edp_interval_only)
note "right after the burst the slot with the heartbeat query retains $(mb "$RETAINED_QUERY"), the interval-only slot $(mb "$RETAINED_INTERVAL")"
check "precondition: the burst on the uncaptured table is retained by the slot with the heartbeat query" at_least "$RETAINED_QUERY" "$THRESHOLD"
check "precondition: the burst is retained by the slot with the interval only" at_least "$RETAINED_INTERVAL" "$THRESHOLD"
check "with heartbeat.action.query the slot releases the burst" poll 150 slot_below edp_inventory 1048576
note "after the wait the slot with the heartbeat query retains $(mb "$(retained edp_inventory)"), the interval-only slot $(mb "$(retained edp_interval_only)")"
check "with only heartbeat.interval.ms the slot still holds the burst" at_least "$(retained edp_interval_only)" "$THRESHOLD"
check "the burst produced no change event" expect "$(end_offsets "$CHANGES")" "$CHANGES_BEFORE"

echo "Incremental snapshot while streaming"
check "precondition: the table holds 4 rows and each has had one r event" expect "$(table_set | wc -l)/$(incremental_r_counts)" "4/111"
sql "INSERT INTO inventory.dbz_signal (id, type, data) VALUES ('adhoc-1', 'execute-snapshot', '{\"data-collections\": [\"inventory.inventory_item\"], \"type\": \"incremental\"}')" >/dev/null
sql "UPDATE inventory.inventory_item SET qty_on_hand = 777 WHERE item_sku = 'SKU-3'" >/dev/null
check "the signal re-emits each untouched row as an r event" poll 120 is 222 incremental_r_counts
check "the update made meanwhile arrives as a u event" poll 60 has_event '.op == "u" and .after.item_sku == "SKU-3" and .after.qty_on_hand == 777'
check "the latest document per SKU matches the table, one document per row" poll 60 sets_equal
check "Tempo has a trace for inventory-service" poll 90 tempo_has_trace inventory-service

echo "Embedded Debezium"
CONNECT_SET=$(latest_docs connect)
export CDC_MODE=embedded
"${COMPOSE[@]}" up -d --no-deps --force-recreate --wait inventory-service
check "the embedded engine holds its own slot, edp_inventory_embedded" poll 90 slot_active edp_inventory_embedded
embedded_matches() { [[ "$(latest_docs embedded)" == "$CONNECT_SET" ]]; }
check "the embedded engine publishes the same sku:qty documents as the Connect path" poll 90 embedded_matches
sql "UPDATE inventory.inventory_item SET qty_on_hand = 999 WHERE item_sku = 'SKU-1'" >/dev/null
check "the embedded engine streams an update" poll 60 doc_is SKU-1 999 embedded
ROWS=$(table_set | wc -l)
BEFORE=$(embedded_docs)
check "precondition: the embedded engine has written its offset file" docker exec "$SERVICE" test -f "$OFFSETS_FILE"
docker exec "$SERVICE" rm -f "$OFFSETS_FILE"
docker kill "$SERVICE" >/dev/null
docker start "$SERVICE" >/dev/null
poll 90 healthy || true
check "after the offset file is deleted the engine publishes every row again" poll 90 ge "$((BEFORE + ROWS))" embedded_docs

topic_names_conform() { # every topic the connectors write is edp.<domain>.<event>: lower case, digits, hyphens
    local list t want
    list=$(kafka kafka-topics.sh --list) || return 1
    for want in edp.inventory.item-changes edp.inventory.connector-heartbeats edp.inventory.interval-only-heartbeats edp.inventory.signals; do
        grep -qx "$want" <<<"$list" || { echo "missing $want"; return 1; }
    done
    while read -r t; do [[ "$t" =~ ^edp\.[a-z0-9-]+\.[a-z0-9-]+$ ]] || { echo "$t"; return 1; }; done < <(grep '^edp\.' <<<"$list")
}
check "every topic under edp. has the name edp.<domain>.<event>, including the heartbeat, signal and helper-table topics" topic_names_conform

echo "$PASSED/$TOTAL checks passed"
[[ "$PASSED" == "$TOTAL" ]]
