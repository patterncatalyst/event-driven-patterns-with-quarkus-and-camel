#!/usr/bin/env bash
# Starts the stack, loads three streams of 1,000, 10,000 and 100,000 events and checks the observable effects of
# snapshots: what a load replays, what a snapshot costs and saves, that deleting or outdating one changes only the
# cost, and what the Camel snapshotter does during a burst. SNAPSHOT_EVERY=0, SNAPSHOT_GUARD=false and the other
# settings of the README are the negative witnesses. KEEP=1 leaves the stack running afterwards.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-15

COMPOSE=(docker compose -f compose.yaml)
APP=edp-inventory-service
N="${SNAPSHOT_EVERY:-100}"
DELAY_MS="${SNAPSHOTTER_DELAY_MS:-1000}"
R=20 # the snapshot load must be at least R times faster than the full load on the 100,000 event stream
BASE=http://localhost:8080
PSQL=(docker exec -e PGPASSWORD=edp edp-postgres psql -h 127.0.0.1 -U edp -d edp -tA)
TOTAL=0
PASSED=0
WORK="$(mktemp -d)"

cleanup() {
    rm -rf "$WORK"
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

precondition() { # precondition "<description>" <command...>: counted as a check; failing it stops the run
    local desc="$1"; shift
    TOTAL=$((TOTAL + 1))
    if "$@" >/dev/null 2>&1; then
        PASSED=$((PASSED + 1)); printf 'PASS %d: %s\n' "$TOTAL" "$desc"
    else
        printf 'PRECONDITION FAILED %d: %s\n' "$TOTAL" "$desc" >&2; exit 1
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
fail_loudly() { printf 'PRECONDITION FAILED: %s\n' "$1" >&2; exit 1; }
note() { printf 'NOTE: %s\n' "$*"; }

sql() { "${PSQL[@]}" -c "$1"; }
ready() { curl -sf "$BASE/q/health/ready" >/dev/null; }
field() { python3 -I -c 'import json, sys; print(json.load(sys.stdin)[sys.argv[1]])' "$1"; } # field <key> < json
item() { curl -sf "$BASE/items/$1${2:+?$2}"; }                 # item <sku> [query]
get() { item "$1" "${3:-}" | field "$2"; }                      # get <sku> <field> [query]
reserve() { curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -d "{\"orderId\":\"O-$2\",\"quantity\":${3:-1}}" "$BASE/items/$1/reservations"; }
receive() { curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' -d "{\"quantity\":$2}" "$BASE/items/$1/receipts"; }
reserve_n() { # reserve_n <sku> <count>: sequential reservations, prints how many were not 201
    local i bad=0
    for ((i = 1; i <= $2; i++)); do [[ "$(reserve "$1" "S$RANDOM$i")" == 201 ]] || bad=$((bad + 1)); done
    echo "$bad"
}
burst() { # burst <sku> <count>: count reservations from 8 parallel clients, prints "<status> <how many>" lines
    seq 1 "$2" | xargs -P 8 -I{} curl -s -o /dev/null -w '%{http_code}\n' -X POST -H 'Content-Type: application/json' \
        -d "{\"orderId\":\"O-B{}-$RANDOM\",\"quantity\":1}" "$BASE/items/$1/reservations" | sort | uniq -c
}

head_version() { sql "SELECT max(version) FROM inventory.events WHERE stream_id = '$1'"; }
event_count() { sql "SELECT count(*) FROM inventory.events WHERE stream_id = '$1'"; }
snap_version() { sql "SELECT coalesce((SELECT version FROM inventory.snapshots WHERE stream_id = '$1'), 0)"; }
snap_by() { sql "SELECT coalesce((SELECT taken_by FROM inventory.snapshots WHERE stream_id = '$1'), '-')"; }
snap_print() { sql "SELECT coalesce((SELECT version || '/' || schema_version || '/' || taken_at FROM inventory.snapshots WHERE stream_id = '$1'), '-')"; }
sql_quantity() { # the quantity on hand, folded in SQL from the payloads
    sql "SELECT sum(CASE payload->>'kind' WHEN 'StockReceived' THEN (payload->>'quantity')::int ELSE -(payload->>'quantity')::int END)
         FROM inventory.events WHERE stream_id = '$1'"
}

prom_holds() { curl -sf -G "http://localhost:19090/api/v1/query" --data-urlencode "query=$1" | grep -q '"value"'; }
prom_value() { curl -sf -G "http://localhost:19090/api/v1/query" --data-urlencode "query=$1" \
    | python3 -I -c 'import json, sys; r = json.load(sys.stdin)["data"]["result"]; print(r[0]["value"][1] if r else "none")'; }
tempo_has_trace() { local out; out="$(curl -sf -G "http://localhost:3200/api/search" --data-urlencode "tags=service.name=$1")" || return 1; [[ "$out" == *'"traceID"'* ]]; }
loki_has_log() { local out; out="$(curl -sf -G "http://localhost:3100/loki/api/v1/query_range" --data-urlencode "query={service_name=\"$1\"}")" || return 1; [[ "$out" == *'"values"'* ]]; }
app_logged() { docker logs "$APP" 2>&1 | grep -qF "$1"; }

# --- seed and state checks -----------------------------------------------------------------------------------------
seed_counts() { expect "$(event_count SKU-1501)/$(event_count SKU-1502)/$(event_count SKU-1503)/$(sql 'SELECT count(*) FROM inventory.snapshots')" "1000/10000/100000/0"; }
seed_loaded() { seed_counts && triggers_present; }
triggers_present() { expect "$(sql "SELECT count(*) FROM pg_trigger WHERE tgrelid = 'inventory.events'::regclass AND NOT tgisinternal")" 2; }
seed_read_identically() {
    local json; json="$(item SKU-1503)"
    expect "$(field quantityOnHand <<<"$json")/$(field version <<<"$json")/$(field eventsReplayed <<<"$json")/$(field source <<<"$json")" \
        "$(sql_quantity SKU-1503)/100000/100000/full"
}
first_reserve_snapshots() {
    expect "$(reserve SKU-1503 FIRST)" 201 && expect "$(snap_version SKU-1503)/$(snap_by SKU-1503)/$(sql "SELECT schema_version FROM inventory.snapshots WHERE stream_id = 'SKU-1503'")" "100001/inline/1"
}
load_from_snapshot() {
    local json; json="$(item SKU-1503)"
    expect "$(field eventsReplayed <<<"$json")/$(field snapshotVersion <<<"$json")/$(field quantityOnHand <<<"$json")" "0/100001/$(( EXPECTED_1503 - 1 ))"
}
tail_is_below_n() {
    expect "$(reserve_n SKU-1503 $((N - 1)))" 0 && expect "$(snap_version SKU-1503)" 100001 \
        && expect "$(get SKU-1503 eventsReplayed)" $((N - 1))
}
nth_moves_snapshot() {
    expect "$(reserve SKU-1503 NTH)" 201 && expect "$(snap_version SKU-1503)" $((100001 + N)) && expect "$(get SKU-1503 eventsReplayed)" 0
}
snapshot_equals_full() { # snapshot_equals_full <sku>...: the state with and without the snapshot is the same
    local sku
    for sku in "$@"; do
        expect "$(get "$sku" quantityOnHand)/$(get "$sku" version)" "$(get "$sku" quantityOnHand ignoreSnapshot=true)/$(get "$sku" version ignoreSnapshot=true)" || return 1
    done
}
only_commands_write_snapshots() {
    expect "$(sql "SELECT count(*) FROM inventory.snapshots WHERE stream_id IN ('SKU-1501','SKU-1502')")" 0 \
        && expect "$(get SKU-1501 eventsReplayed)/$(get SKU-1502 eventsReplayed)" "1000/10000"
}

# --- measurement ---------------------------------------------------------------------------------------------------
bench() { curl -sf "$BASE/demo/bench/$1?warm=5&runs=21&snapshot=$2"; } # bench <sku> <with snapshot>
measure_ratio() {
    local snap_us=$1 full_us=$2
    (( full_us >= R * snap_us ))
}
metric_exported() { prom_holds 'inventory_rehydrate_milliseconds_count{source="full",service_name="inventory-service"}' \
    && prom_holds 'inventory_rehydrate_milliseconds_count{source="snapshot",service_name="inventory-service"}'; }

# --- the snapshot is a cache ---------------------------------------------------------------------------------------
deleted_snapshots_cost_only() {
    local qty version head
    qty="$(get SKU-1503 quantityOnHand)"; version="$(get SKU-1503 version)"; head="$(head_version SKU-1503)"
    sql "DELETE FROM inventory.snapshots" >/dev/null
    expect "$(get SKU-1503 quantityOnHand)/$(get SKU-1503 version)/$(get SKU-1503 eventsReplayed)" "$qty/$version/$head"
}
policy_recovers() {
    expect "$(reserve SKU-1503 RECOVER)" 201 && expect "$(snap_version SKU-1503)/$(snap_by SKU-1503)" "$(head_version SKU-1503)/inline"
}
stale_schema_replays_everything() {
    local qty version head
    qty="$(get SKU-1503 quantityOnHand)"; version="$(get SKU-1503 version)"; head="$(head_version SKU-1503)"
    sql "UPDATE inventory.snapshots SET schema_version = 0 WHERE stream_id = 'SKU-1503'" >/dev/null
    expect "$(get SKU-1503 quantityOnHand)/$(get SKU-1503 version)/$(get SKU-1503 eventsReplayed)" "$qty/$version/$head"
}
refused_reserve_changes_nothing() {
    local events snap
    events="$(event_count SKU-1503)"; snap="$(snap_print SKU-1503)"
    expect "$(reserve SKU-1503 TOOMANY 1000000)" 409 && expect "$(event_count SKU-1503)/$(snap_print SKU-1503)" "$events/$snap"
}
hot_sku_burst() {
    local before after report
    before="$(sql_quantity SKU-1502)"
    report="$(burst SKU-1502 200)"
    after="$(sql_quantity SKU-1502)"
    echo "$report" > "$WORK/burst17"
    [[ "$(awk '$2 == 201 {print $1}' "$WORK/burst17")" == 200 ]] && expect "$after" $((before - 200)) \
        && expect "$(event_count SKU-1502)/$(head_version SKU-1502)/$(sql "SELECT count(DISTINCT version) FROM inventory.events WHERE stream_id = 'SKU-1502'")" "10200/10200/10200"
}
guard_holds_against_stale_take() {
    local out
    expect "$(receive SKU-1501 "$N")" 201 || return 1 # stock for the N reservations the demo endpoint makes
    out="$(curl -sf -X POST "$BASE/demo/stale-take/SKU-1501?moves=$N")"
    echo "$out" > "$WORK/stale"
    expect "$(field rowsWritten <<<"$out")" 0 && (( $(field snapshotAfter <<<"$out") >= $(field snapshotBefore <<<"$out") ))
}

# --- the Camel snapshotter -----------------------------------------------------------------------------------------
route_started() { app_logged "Started snapshotter (scheduler://snapshotter)"; }
sample_snapshot() { # sample_snapshot <sku> <file> <stop file>: every 0.2 s "<snapshot version> <taken_by> <head>"
    while [[ ! -e "$3" ]]; do
        sql "SELECT coalesce((SELECT version || ' ' || taken_by FROM inventory.snapshots WHERE stream_id = '$1'), '0 -') || ' ' || (SELECT max(version) FROM inventory.events WHERE stream_id = '$1')" >> "$2" || true
        sleep 0.2
    done
}
never_inline() { ! grep -q ' inline ' "$WORK/samples" && grep -q ' camel ' "$WORK/samples"; }
version_never_decreases() { awk '{ if ($1 < prev) bad = 1; prev = $1 } END { exit bad }' "$WORK/samples"; }
snapshot_catches_up() { [[ "$(snap_by SKU-1502)" == camel ]] && (( $(head_version SKU-1502) - $(snap_version SKU-1502) < N )); }
no_inline_writes_in_phase() { expect "$(sql "SELECT count(*) FROM inventory.snapshots WHERE taken_by = 'inline'")" 0; }

echo "Starting the stack"
# Fresh start: remove any containers and volumes left by an earlier run (KEEP=1, an aborted run),
# then create every container and anonymous volume anew.
"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
"${COMPOSE[@]}" up -d --build --force-recreate --renew-anon-volumes --wait
poll 60 ready || fail_loudly "inventory-service is not ready"

echo "Infrastructure and seed"
precondition "inventory-service is ready" ready
docker exec -i -e PGPASSWORD=edp edp-postgres psql -h 127.0.0.1 -U edp -d edp -q -v ON_ERROR_STOP=1 < seed.sql >/dev/null \
    || fail_loudly "the seed did not load"
precondition "the streams hold 1,000, 10,000 and 100,000 events, no snapshot exists and the events table refuses updates" seed_loaded
EXPECTED_1503="$(sql_quantity SKU-1503)"
precondition "bulk-loaded SKU-1503 is read as the SQL fold of its payloads: the same quantity, version 100000, all 100000 events replayed" \
    seed_read_identically

echo "Inline snapshots, every $N events"
check "one reservation returns 201 and writes a snapshot at version 100001, schema 1, taken inline" first_reserve_snapshots
check "a load starts from the snapshot: 0 events replayed, quantity $((EXPECTED_1503 - 1))" load_from_snapshot
check "after $((N - 1)) more reservations the snapshot has not moved and a load replays exactly $((N - 1)) events" tail_is_below_n
check "the next reservation moves the snapshot to version $((100001 + N)) and a load replays 0 events" nth_moves_snapshot
check "SKU-1501, SKU-1502 and SKU-1503 give the same quantity and version with and without the snapshot" \
    snapshot_equals_full SKU-1501 SKU-1502 SKU-1503
check "SKU-1501 and SKU-1502 have no snapshot and replay 1,000 and 10,000 events: only a command writes one" only_commands_write_snapshots

echo "Measured"
reserve_n SKU-1503 $((N - 1)) >/dev/null # leave the largest tail the policy allows, so the snapshot load is the worst case
FULL_1501="$(bench SKU-1501 false | field fullMedianUs)"
FULL_1502="$(bench SKU-1502 false | field fullMedianUs)"
BENCH_1503="$(bench SKU-1503 true)"
FULL_1503="$(field fullMedianUs <<<"$BENCH_1503")"
SNAP_1503="$(field snapshotMedianUs <<<"$BENCH_1503")"
note "full replay medians over 21 loads: 1,000 events ${FULL_1501} us, 10,000 events ${FULL_1502} us, 100,000 events ${FULL_1503} us"
note "SKU-1503 snapshot load: median ${SNAP_1503} us replaying $(field snapshotEvents <<<"$BENCH_1503") events; full load range $(field fullMinUs <<<"$BENCH_1503") to $(field fullMaxUs <<<"$BENCH_1503") us"
note "full replay cost per event: $(python3 -I -c "print(round($FULL_1501 / 1000, 2), round($FULL_1502 / 10000, 2), round($FULL_1503 / 100000, 2))") us at 1,000 / 10,000 / 100,000 events"
note "snapshot load is $(python3 -I -c "print(round($FULL_1503 / max($SNAP_1503, 1)))") times faster than the full load on 100,000 events (the check requires $R)"
note "state size: $(sql "SELECT pg_column_size(state) FROM inventory.snapshots WHERE stream_id = 'SKU-1503'") bytes of jsonb at any version"
EXPLAIN_FULL="$(sql "EXPLAIN (ANALYZE, BUFFERS) SELECT payload FROM inventory.events WHERE stream_id = 'SKU-1503' AND version > 0 ORDER BY version" || true)"
EXPLAIN_TAIL="$(sql "EXPLAIN (ANALYZE, BUFFERS) SELECT payload FROM inventory.events WHERE stream_id = 'SKU-1503' AND version > $(snap_version SKU-1503) ORDER BY version" || true)"
note "EXPLAIN full query: $(grep -oE 'actual time=[0-9.]+\.\.[0-9.]+ rows=[0-9]+' <<<"$EXPLAIN_FULL" | head -1), $(grep 'Execution Time' <<<"$EXPLAIN_FULL")"
note "EXPLAIN tail query: $(grep -oE 'actual time=[0-9.]+\.\.[0-9.]+ rows=[0-9]+' <<<"$EXPLAIN_TAIL" | head -1), $(grep 'Execution Time' <<<"$EXPLAIN_TAIL")"
check "the snapshot load of the 100,000 event stream is at least $R times faster than its full load (medians of 21 loads)" \
    measure_ratio "$SNAP_1503" "$FULL_1503"
check "Prometheus holds the rehydrate timer for both sources of inventory-service" poll 90 metric_exported

echo "The snapshot is a cache"
check "deleting every snapshot changes the cost and not the state: the same quantity and version, the whole stream replayed" deleted_snapshots_cost_only
check "the next reservation writes a snapshot again at the head, taken inline" policy_recovers
check "a snapshot whose schema_version is 0 (set by hand) is ignored: the whole stream is replayed and the state is unchanged" stale_schema_replays_everything
check "the service logs 'snapshot schema 0 ignored for SKU-1503'" app_logged "snapshot schema 0 ignored for SKU-1503"
check "a reservation larger than the quantity on hand returns 409 and appends no event, and the snapshot row is unchanged" refused_reserve_changes_nothing

echo "A hot SKU"
check "200 reservations from 8 parallel clients on SKU-1502 all return 201, the quantity falls by exactly 200 and the versions are contiguous" hot_sku_burst
cat "$WORK/burst17" | sed 's/^/NOTE: burst status /'
check "a snapshot written late is refused by the guard: the stored version does not go down" guard_holds_against_stale_take
note "stale take: $(tr -d '\n ' < "$WORK/stale")"

echo "Out of band: the Camel snapshotter writes the snapshots"
sql "DELETE FROM inventory.snapshots" >/dev/null
APP_PROFILE=camel-snapshotter "${COMPOSE[@]}" up -d --no-deps --force-recreate --wait inventory-service
poll 60 ready || fail_loudly "inventory-service is not ready with the camel-snapshotter profile"
precondition "the snapshotter route has started" poll 30 route_started
poll 30 snapshot_catches_up || fail_loudly "the snapshotter did not take its first snapshot of SKU-1502"
BURST=$((10 * N))
expect "$(receive SKU-1502 "$BURST")" 201 || fail_loudly "could not add stock for the burst"
poll 30 snapshot_catches_up || fail_loudly "the snapshotter did not catch up before the burst"
: > "$WORK/samples"
sample_snapshot SKU-1502 "$WORK/samples" "$WORK/stop" &
SAMPLER=$!
burst SKU-1502 "$BURST" > "$WORK/burst-camel"
touch "$WORK/stop"; wait "$SAMPLER" || true
note "burst of $BURST reservations: $(tr -s ' \n' ' ' < "$WORK/burst-camel"); $(wc -l < "$WORK/samples") samples; largest tail sampled $(awk '{ t = $3 - $1; if (t > m) m = t } END { print m + 0 }' "$WORK/samples") events (N = $N)"
check "during the burst every sampled snapshot of SKU-1502 was taken by camel, none inline" never_inline
check "within three scheduler periods after the burst the snapshot is within $N events of the head, taken by camel" poll $((3 * DELAY_MS / 1000 + 5)) snapshot_catches_up
check "the snapshot version sampled every 0.2 s during the burst never decreased" version_never_decreases
check "SKU-1502 gives the same quantity and version with and without the snapshot" snapshot_equals_full SKU-1502
check "the command path wrote no snapshot in this phase: no row was taken inline" no_inline_writes_in_phase

echo "Observability"
check "Tempo has a trace for inventory-service" poll 90 tempo_has_trace inventory-service
check "Loki has logs for inventory-service" poll 90 loki_has_log inventory-service
check "Prometheus holds the counter of ignored snapshots for inventory-service" \
    poll 90 prom_holds 'inventory_snapshot_ignored_total{service_name="inventory-service"} > 0'
note "reservation conflicts retried on the hot SKUs: $(prom_value 'sum(max_over_time(inventory_command_conflicts_total{service_name="inventory-service"}[1h]))')"

echo "$PASSED/$TOTAL checks passed"
[[ "$PASSED" == "$TOTAL" ]]
