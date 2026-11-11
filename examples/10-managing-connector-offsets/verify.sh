#!/usr/bin/env bash
# Starts the stack, registers the inventory-cdc connector and walks one connector through every offset operation:
# snapshot, GET offsets, stop, retention while stopped, resume, forward alter, backward alter, reset, a dropped slot,
# a lost slot and an incremental snapshot. Every operation is judged by what lands in inventory.item_change and
# what pg_replication_slots reports. OFFSET_FLUSH_INTERVAL_MS=60000 restores Connect's default flush interval.
# KEEP=1 leaves the stack running afterwards.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-10

export OFFSET_FLUSH_INTERVAL_MS="${OFFSET_FLUSH_INTERVAL_MS:-1000}"
COMPOSE=(docker compose -f compose.yaml)
CONNECT=http://localhost:8083
NAME=inventory-cdc
SLOT=edp_inventory
PSQL=(docker exec -i -e PGPASSWORD=edp edp-postgres psql -h 127.0.0.1 -U edp -d edp -tA)
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

expect() { [[ "$1" == "$2" ]]; } # expect <actual> <expected>
at_least() { (( $1 >= $2 )); }
fail_loudly() { printf 'PRECONDITION FAILED: %s\n' "$1" >&2; exit 1; }

kafka() { docker exec edp-kafka "/opt/kafka/bin/$1" --bootstrap-server localhost:9092 "${@:2}"; }
sql() { "${PSQL[@]}" -c "$1"; }
sql_script() { "${PSQL[@]}" -q -f -; }
tempo_has_trace() { curl -sf -G "http://localhost:3200/api/search" --data-urlencode "tags=service.name=$1" | grep -q '"traceID"'; }
py() { python3 -I -c "$@"; }

# --- Kafka Connect REST ---
cstate() { # connector state / task states, for example RUNNING/RUNNING, STOPPED/ or RUNNING/ (no tasks)
    curl -sf "$CONNECT/connectors/$NAME/status" \
        | py "import json,sys; d=json.load(sys.stdin); print(d['connector']['state'] + '/' + ','.join(t['state'] for t in d['tasks']))"
}
state_is() { [[ "$(cstate)" == "$1" ]]; }
task_trace() { curl -sf "$CONNECT/connectors/$NAME/status" | py "import json,sys; d=json.load(sys.stdin); print(d['tasks'][0].get('trace','') if d['tasks'] else '')"; }
trace_says_unavailable() { [[ "$(task_trace)" == *"no longer available on the server"* ]]; }
offsets() { curl -sf "$CONNECT/connectors/$NAME/offsets"; }
offset_lsn() { offsets | py "import json,sys; o=json.load(sys.stdin)['offsets']; print(o[0]['offset']['lsn'] if o else '')"; }
offset_server() { offsets | py "import json,sys; o=json.load(sys.stdin)['offsets']; print(o[0]['partition']['server'] if o else '')"; }
offsets_empty() { [[ "$(offsets | py "import json,sys; print(len(json.load(sys.stdin)['offsets']))")" == 0 ]]; }
http_code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
stop_connector() { http_code -X PUT "$CONNECT/connectors/$NAME/stop"; }
resume_connector() { http_code -X PUT "$CONNECT/connectors/$NAME/resume"; }
delete_offsets() { http_code -X DELETE "$CONNECT/connectors/$NAME/offsets"; }
patch_offset() { # patch_offset <lsn>: prints the HTTP code
    http_code -X PATCH -H 'Content-Type: application/json' "$CONNECT/connectors/$NAME/offsets" \
        -d "{\"offsets\":[{\"partition\":{\"server\":\"edp\"},\"offset\":{\"lsn\":$1}}]}"
}
restart_failed() { http_code -X POST "$CONNECT/connectors/$NAME/restart?includeTasks=true&onlyFailed=true"; }
restart_keeps_failed() { [[ "$(restart_failed)" == 202 ]] && sleep 15 && state_is "RUNNING/FAILED"; }
restart_changes_nothing() { [[ "$(restart_failed)" == 202 ]] && sleep 10 && state_is "RUNNING/"; }
refused_while_running() { # PATCH and DELETE on a running connector: 400 and the STOPPED requirement
    local body
    body=$(curl -s -X DELETE "$CONNECT/connectors/$NAME/offsets")
    [[ "$(patch_offset 1)" == 400 && "$body" == *"STOPPED"* ]]
}
stopped_cleanly() { [[ "$(stop_connector)" == 204 ]] && poll 20 state_is "STOPPED/"; }
resumed_cleanly() { [[ "$(resume_connector)" == 202 ]] && poll 60 state_is "RUNNING/RUNNING"; }
patched_to() { # patched_to <lsn>: PATCH is accepted and GET returns the new lsn
    [[ "$(patch_offset "$1")" == 200 && "$(offset_lsn)" == "$1" ]]
}
reset_cleanly() { [[ "$(delete_offsets)" == 200 ]] && offsets_empty; }
offset_is_stored() { [[ "$(offset_server)" == edp && -n "$(offset_lsn)" ]]; }
offset_moved_past() { (( $(offset_lsn) > $1 )); }

# --- PostgreSQL ---
slot() { sql "SELECT $1 FROM pg_replication_slots WHERE slot_name='$SLOT'"; }
slot_inactive() { [[ "$(slot active)" == f ]]; }
slot_flush_lsn() { slot "pg_wal_lsn_diff(confirmed_flush_lsn,'0/0')::bigint"; }
retained_bytes() { slot "pg_wal_lsn_diff(pg_current_wal_lsn(), confirmed_flush_lsn)::bigint"; }
retained_below() { (( $(retained_bytes) < $1 )); }
current_lsn() { sql "SELECT pg_wal_lsn_diff(pg_current_wal_lsn(),'0/0')::bigint"; }
offset_matches_slot() { [[ "$(offset_lsn)" == "$(slot_flush_lsn)" ]]; }
wal_status() { slot wal_status; }
slot_is_lost() { [[ "$(wal_status)" == lost ]]; }
slot_is_usable() { [[ -n "$(wal_status)" && "$(wal_status)" != lost ]]; }
rows() { sql "SELECT count(*) FROM inventory.item_change ${1:+WHERE $1}"; }
max_id() { sql "SELECT coalesce(max(id),0) FROM inventory.item_change"; }
rows_are() { [[ "$(rows "$1")" == "$2" ]]; } # rows_are "<where>" <count>
rows_stay() { stays "$3" rows_are "$1" "$2"; } # rows_stay "<where>" <count> <seconds>
repeat_is_last_change() { rows_are "id > 0 AND qty=7777" 2 && rows_stay "id > 0" $(($1 + 3)) 5; }
updates() { # updates <first quantity> <count>: one transaction each, spread over SKU-1 to SKU-5
    local i
    for i in $(seq 0 $(($2 - 1))); do
        printf "UPDATE inventory.inventory_item SET qty_on_hand=%d, last_changed=now() WHERE item_sku='SKU-%d';\n" $(($1 + i)) $((i % 5 + 1))
    done | sql_script
}
bulk_insert() { sql "INSERT INTO inventory.legacy_audit(note) SELECT repeat('x',1000) FROM generate_series(1,20000)" >/dev/null; }
snapshot_matches_table() { # the r rows after id $1 are exactly the current rows of inventory_item
    [[ "$(sql "SELECT count(*) FROM (
        (SELECT sku, qty FROM inventory.item_change WHERE id > $1 AND op='r' EXCEPT SELECT item_sku, qty_on_hand FROM inventory.inventory_item)
        UNION ALL
        (SELECT item_sku, qty_on_hand FROM inventory.inventory_item EXCEPT SELECT sku, qty FROM inventory.item_change WHERE id > $1 AND op='r')) d")" == 0 ]]
}
set_keep_size() { sql "ALTER SYSTEM SET max_slot_wal_keep_size='1MB'" >/dev/null; sql "SELECT pg_reload_conf()" >/dev/null; }
reset_keep_size() { sql "ALTER SYSTEM RESET max_slot_wal_keep_size" >/dev/null; sql "SELECT pg_reload_conf()" >/dev/null; }
make_slot_lost() { # one batch of WAL is enough; the loop is a bound, not an expectation
    local n
    for n in 1 2 3 4 5; do
        bulk_insert; sql "SELECT pg_switch_wal()" >/dev/null; sql "CHECKPOINT" >/dev/null
        slot_is_lost && return 0
    done
    return 1
}
offsets_topic_has_key() {
    kafka kafka-console-consumer.sh --topic edp-connect-offsets --from-beginning --timeout-ms 5000 --property print.key=true 2>/dev/null \
        | grep -qF "[\"$NAME\",{\"server\":\"edp\"}]"
}
connect_logged_unobtainable_slot() { docker logs edp-connect 2>&1 | grep -q "Cannot obtain valid replication slot"; }
stays() { # stays <seconds> <command...>: the command succeeds now and keeps succeeding for the whole period
    local end=$((SECONDS + $1)); shift
    while (( SECONDS < end )); do "$@" || return 1; sleep 1; done
    "$@"
}
fresh_snapshot_after() { # five r rows after id $1 and they are the current table
    [[ "$(rows "id > $1 AND op='r'")" == 5 ]] && snapshot_matches_table "$1"
}
recovered_from() { # recovered_from <id>: RUNNING, a usable slot and a fresh snapshot of the five rows
    poll 60 state_is "RUNNING/RUNNING" && slot_is_usable && poll 60 fresh_snapshot_after "$1"
}

echo "Starting the stack"
# Fresh start: remove any containers and volumes left by an earlier run (KEEP=1, an aborted run),
# then create every container and anonymous volume anew.
"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
"${COMPOSE[@]}" up -d --build --force-recreate --renew-anon-volumes --wait

# The legacy tables belong to the legacy system: a table with five SKUs, one with a heavy write load that the
# connector does not capture, and the signal table.
sql "CREATE SCHEMA IF NOT EXISTS inventory" >/dev/null
sql_script <<'SQL'
CREATE TABLE inventory.inventory_item (item_sku text PRIMARY KEY, qty_on_hand int NOT NULL, last_changed timestamptz NOT NULL DEFAULT now());
INSERT INTO inventory.inventory_item (item_sku, qty_on_hand) SELECT 'SKU-' || g, 100 FROM generate_series(1, 5) g;
CREATE TABLE inventory.legacy_audit (id bigserial PRIMARY KEY, note text);
CREATE TABLE inventory.dbz_signal (id varchar(42) PRIMARY KEY, type varchar(32) NOT NULL, data varchar(2048));
SQL
[[ "$(sql "SELECT count(*) FROM inventory.inventory_item")" == 5 ]] || fail_loudly "the legacy table was not seeded"

[[ "$(http_code -X PUT -H 'Content-Type: application/json' --data @connect/inventory-cdc.json "$CONNECT/connectors/$NAME/config")" =~ ^20[01]$ ]] \
    || fail_loudly "the connector was not registered"

echo "Snapshot and offsets"
check "inventory-service is ready" curl -sf http://localhost:8080/q/health/ready
check "the connector and its task are RUNNING" poll 60 state_is "RUNNING/RUNNING"
check "the snapshot delivered 5 rows, all op r" poll 60 rows_are "op='r'" 5
check "the offset partition is {\"server\":\"edp\"} and holds an lsn" poll 30 offset_is_stored
SNAPSHOT_LSN=$(offset_lsn)
[[ -n "$SNAPSHOT_LSN" ]] || fail_loudly "no offset lsn was stored after the snapshot"
check "on an idle stream the offset lsn equals the slot's confirmed_flush_lsn" poll 30 offset_matches_slot
check "the offsets topic holds the same key" offsets_topic_has_key

echo "Stopped connector"
check "PATCH and DELETE on a running connector are refused with 400 and the STOPPED requirement" refused_while_running
check "PUT /stop returns 204 and the connector is STOPPED with no tasks" stopped_cleanly
check "precondition: the slot is inactive while the connector is stopped" poll 20 slot_inactive
[[ "$(rows)" == 5 ]] || fail_loudly "expected the 5 snapshot rows before the stopped phase"
bulk_insert
updates 1001 100
sleep 5
check "while stopped, 20,000 uncaptured rows and 100 updates keep more than 15 MB of WAL" at_least "$(retained_bytes)" 15000000
check "while stopped, no change reached item_change" expect "$(rows)" 5

echo "Resume"
check "PUT /resume returns 202 and the task is RUNNING" resumed_cleanly
check "all 100 updates are delivered after the resume" poll 90 rows_are "op='u'" 100
check "the retained WAL falls below 1 MB once the offset is flushed" poll 60 retained_below 1000000

echo "Forward alter"
stopped_cleanly || fail_loudly "the connector did not stop"
BEFORE_ALTER=$(rows)
updates 9001 10
FORWARD_LSN=$(current_lsn)
check "PATCH to the current WAL position is accepted and GET returns it" patched_to "$FORWARD_LSN"
resumed_cleanly || fail_loudly "the connector did not resume after the alter"
updates 7777 1
check "the update made after the alter is delivered" poll 30 rows_are "qty=7777" 1
check "the 10 updates made before the alter are skipped" rows_stay "qty BETWEEN 9001 AND 9010" 0 5
check "exactly one change arrived since the stop" expect "$(rows)" $((BEFORE_ALTER + 1))

echo "Backward alter"
# Precondition: the slot has acknowledged everything already delivered, so the repeat below is not a
# flush race: the bare lsn written by PATCH still makes the last transaction before the stop come again.
poll 30 offset_matches_slot || fail_loudly "the slot did not acknowledge the delivered changes"
stopped_cleanly || fail_loudly "the connector did not stop"
BEFORE_BACKWARD=$(rows)
updates 6001 2
check "PATCH to the snapshot lsn, far behind the slot, is accepted and GET returns it" patched_to "$SNAPSHOT_LSN"
resumed_cleanly || fail_loudly "the connector did not resume after the backward alter"
check "the 2 new changes and one repeat of the last change before the stop arrive, nothing older" poll 30 rows_are "id > 0" $((BEFORE_BACKWARD + 3))
check "the repeat is the qty 7777 change, and no further replay follows" repeat_is_last_change "$BEFORE_BACKWARD"
check "the stored offset moves forward again past the lsn that was written" poll 30 offset_moved_past "$SNAPSHOT_LSN"

echo "Reset"
stopped_cleanly || fail_loudly "the connector did not stop"
updates 5001 3
MARK=$(max_id)
check "DELETE returns 200 and GET offsets is empty" reset_cleanly
resumed_cleanly || fail_loudly "the connector did not resume after the reset"
check "the reset re-snapshots: exactly 5 r rows with the current values" poll 60 fresh_snapshot_after "$MARK"
check "no history is replayed: no u row follows the reset" rows_stay "id > $MARK AND op<>'r'" 0 5
check "the offset is stored again after the snapshot" poll 30 expect "$([[ -n "$(offset_lsn)" ]] && echo yes)" yes

echo "Dropped slot, offsets kept"
stopped_cleanly || fail_loudly "the connector did not stop"
poll 20 slot_inactive || fail_loudly "the slot is still active after the stop"
sql "SELECT pg_drop_replication_slot('$SLOT')" >/dev/null
updates 4001 3
MARK=$(max_id)
resume_connector >/dev/null
check "the task FAILED" poll 90 state_is "RUNNING/FAILED"
check "the trace says the position is no longer available on the server" trace_says_unavailable
check "restart with includeTasks and onlyFailed is accepted and the task is FAILED again" restart_keeps_failed
stopped_cleanly || fail_loudly "the failed connector did not stop"
reset_cleanly || fail_loudly "the offsets were not reset"
resume_connector >/dev/null
check "stop, DELETE offsets and resume recover it: RUNNING, a new slot and a fresh snapshot of 5 rows" recovered_from "$MARK"

echo "Lost slot"
stopped_cleanly || fail_loudly "the connector did not stop"
poll 20 slot_inactive || fail_loudly "the slot is still active after the stop"
set_keep_size
make_slot_lost || fail_loudly "the slot did not become lost"
check "the slot's wal_status is lost" slot_is_lost
updates 3001 2
MARK=$(max_id)
check "PUT /resume returns 202" expect "$(resume_connector)" 202
sleep 60
check "60 seconds later the connector is RUNNING with no task and no FAILED state" state_is "RUNNING/"
check "nothing is delivered" rows_are "id > $MARK" 0
check "restart with includeTasks and onlyFailed is accepted and changes nothing" restart_changes_nothing
check "Connect logs that it cannot obtain a valid replication slot" connect_logged_unobtainable_slot
stopped_cleanly || fail_loudly "the connector did not stop"
poll 20 slot_inactive || fail_loudly "the slot is still active after the stop"
sql "SELECT pg_drop_replication_slot('$SLOT')" >/dev/null
reset_cleanly || fail_loudly "the offsets were not reset"
reset_keep_size
resume_connector >/dev/null
check "dropping the slot, DELETE offsets and resume recover it: RUNNING, a usable slot and a fresh snapshot of 5 rows" recovered_from "$MARK"

echo "Incremental snapshot"
MARK=$(max_id)
sql "INSERT INTO inventory.dbz_signal VALUES ('ad-hoc-1', 'execute-snapshot', '{\"data-collections\":[\"inventory.inventory_item\"],\"type\":\"incremental\"}')" >/dev/null
check "a signal re-reads the 5 rows while the connector keeps running" poll 60 fresh_snapshot_after "$MARK"

echo "Observability"
check "Tempo has a trace for inventory-service" poll 90 tempo_has_trace inventory-service

echo "$PASSED/$TOTAL checks passed"
[[ "$PASSED" == "$TOTAL" ]]
