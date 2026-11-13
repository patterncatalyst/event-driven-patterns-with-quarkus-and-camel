#!/usr/bin/env bash
# Starts the stack, loads three cumulative histories (1,000, 10,000 and 100,000 orders) and checks that the replay,
# the projection and a SQL query agree at each size, what each costs, and what the projection does when a transaction
# commits out of order. PROJECTION_WATERMARK=off and PROJECTION_POLL=5s are the negative witnesses. KEEP=1 leaves the
# stack running afterwards.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-17

COMPOSE=(docker compose -f compose.yaml)
APP=edp-order-service
R=250      # the projection read must be at least R times faster than the replay at 100,000 orders
T_SMALL=10 # seconds for the projection to catch up on 1,000 and 10,000 orders
T_LARGE=60 # and on 100,000 orders
BASE=http://localhost:8080
PSQL=(docker exec -e PGPASSWORD=edp edp-postgres psql -h 127.0.0.1 -U edp -d edp -tA)
TOTAL=0
PASSED=0
WORK="$(mktemp -d)"
HOLD_PID=""

cleanup() {
    [[ -z "$HOLD_PID" ]] || kill "$HOLD_PID" 2>/dev/null || true
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
report() { curl -sf "$BASE/reports/shipments-per-carrier?source=$1"; }                       # report replay|projection
lag() { report projection | field lagEvents; }
lag_zero() { expect "$(lag)" 0; }
# carrier|day=shipments lines of a report, sorted, and the same from SQL over the raw events
lines() {
    report "$1" | python3 -I -c 'import json, sys
for r in json.load(sys.stdin)["rows"]:
    print(r["carrier"] + "|" + r["day"] + "=" + str(r["shipments"]))' | LC_ALL=C sort
}
oracle() {
    sql "SELECT e.payload->>'carrier' || '|' || (e.occurred_at AT TIME ZONE 'UTC')::date || '=' || count(*)
         FROM orders.events e
         WHERE e.type = 'OrderShipped'
           AND NOT EXISTS (SELECT 1 FROM orders.events c WHERE c.stream_id = e.stream_id AND c.type = 'OrderCancelled')
         GROUP BY e.payload->>'carrier', (e.occurred_at AT TIME ZONE 'UTC')::date" | LC_ALL=C sort
}
total() { report "$1" | python3 -I -c 'import json, sys; print(sum(r["shipments"] for r in json.load(sys.stdin)["rows"]))'; } # total replay|projection
command_code() { curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/orders/$1${2:+/$2}${3:+?$3}"; } # command_code <id> [path] [query]

prom_holds() { curl -sf -G "http://localhost:19090/api/v1/query" --data-urlencode "query=$1" | grep -q '"value"'; }
tempo_has_trace() { local out; out="$(curl -sf -G "http://localhost:3200/api/search" --data-urlencode "tags=service.name=$1")" || return 1; [[ "$out" == *'"traceID"'* ]]; }
loki_has_log() { local out; out="$(curl -sf -G "http://localhost:3100/loki/api/v1/query_range" --data-urlencode "query={service_name=\"$1\"}")" || return 1; [[ "$out" == *'"values"'* ]]; }
app_logged() { docker logs "$APP" 2>&1 | grep -qF "$1"; }

# --- the histories -------------------------------------------------------------------------------------------------
events_count() { sql "SELECT count(*) FROM orders.events"; }
load_history() { # load_history <from> <to> <expected events in total>
    docker exec -i -e PGPASSWORD=edp edp-postgres psql -h 127.0.0.1 -U edp -d edp -q -v ON_ERROR_STOP=1 -v lo="$1" -v hi="$2" < seed.sql >/dev/null \
        || fail_loudly "the history of orders $1 to $2 did not load"
    expect "$(events_count)" "$3" || fail_loudly "expected $3 events after loading orders $1 to $2, found $(events_count)"
}
store_is_empty() {
    expect "$(events_count)/$(sql "SELECT count(*) FROM pg_trigger WHERE tgrelid = 'orders.events'::regclass AND NOT tgisinternal")/$(sql 'SELECT count(*) FROM orders.carrier_daily_shipments')/$(sql "SELECT last_tx_id || '/' || last_position FROM orders.projection_checkpoint")" "0/2/0/0/0"
}
replay_equals_oracle() { expect "$(lines replay)" "$(oracle)"; }
projection_equals_replay() { expect "$(lines projection)" "$(lines replay)" && expect "$(lines projection)" "$(oracle)"; }
caught_up_within() { poll "$1" lag_zero; }

# --- measurement ---------------------------------------------------------------------------------------------------
# measure <label>: the bench inside the service, the rebuild time and the sizes. Leaves the projection equal to the oracle.
measure() {
    local label=$1 b rebuild_ms events_bytes proj_bytes rows
    b="$(curl -sf "$BASE/demo/bench?warm=3&runs=11")"
    rebuild_ms="$(curl -sf -X POST "$BASE/demo/projection/rebuild?wait=true" | field millis)"
    expect "$(lines projection)" "$(oracle)" || fail_loudly "the projection differs from the SQL oracle after the rebuild at $label"
    events_bytes="$(sql "SELECT pg_total_relation_size('orders.events')")"
    proj_bytes="$(sql "SELECT pg_total_relation_size('orders.carrier_daily_shipments')")"
    rows="$(sql 'SELECT count(*) FROM orders.carrier_daily_shipments')"
    echo "$rows" >> "$WORK/rows"
    note "$label: $(field events <<<"$b") events, replay median $(field replayMedianMs <<<"$b") ms (min $(field replayMinMs <<<"$b"), max $(field replayMaxMs <<<"$b")), projection read median $(field projectionMedianMs <<<"$b") ms, rebuild $rebuild_ms ms, projection $rows rows $proj_bytes bytes, events $events_bytes bytes"
    echo "$(field replayMedianMs <<<"$b") $(field projectionMedianMs <<<"$b")" > "$WORK/bench-$label"
}
projection_much_faster() {
    local replay_ms proj_ms
    read -r replay_ms proj_ms < "$WORK/bench-100k"
    python3 -I -c 'import sys; sys.exit(0 if float(sys.argv[1]) >= int(sys.argv[3]) * float(sys.argv[2]) else 1)' "$replay_ms" "$proj_ms" "$R"
}
# The projection is dropped at 100,000 orders: a sample taken right after has work left and counts less than the
# replay; after catch-up it equals the replay.
rebuild_catches_up() {
    local replay_total sample
    replay_total="$(total replay)"
    curl -sf -X POST "$BASE/demo/projection/rebuild" >/dev/null
    sample="$(report projection)"
    (( $(field lagEvents <<<"$sample") > 0 )) || return 1
    python3 -I -c 'import json, sys; sys.exit(0 if sum(r["shipments"] for r in json.load(sys.stdin)["rows"]) < int(sys.argv[1]) else 1)' "$replay_total" <<<"$sample" || return 1
    poll "$T_LARGE" lag_zero && projection_equals_replay
}

# --- a command on a large store ------------------------------------------------------------------------------------
live_command_counts() {
    local before
    before="$(total replay)"
    expect "$(command_code O-17L001)/$(command_code O-17L001 payment)/$(command_code O-17L001 shipment carrier=AIRCO)" "201/201/201" || return 1
    expect "$(total replay)" $((before + 1)) || return 1
    poll_total_within_two_seconds $((before + 1))
}
poll_total_within_two_seconds() { # poll_total_within_two_seconds <expected total>
    local end=$(( ${EPOCHREALTIME/./} + 2000000 )) # microseconds
    while (( ${EPOCHREALTIME/./} < end )); do
        [[ "$(total projection)" == "$1" ]] && return 0
        sleep 0.05
    done
    return 1
}
freshness() { # 50 commands, each timed from the shipment's reply to the projection counting it
    python3 -I - "$BASE" <<'PY'
import json, statistics, sys, time, urllib.request
base = sys.argv[1]
def post(path):
    urllib.request.urlopen(urllib.request.Request(base + path, method="POST", data=b""), timeout=30).read()
def projected():
    with urllib.request.urlopen(base + "/reports/shipments-per-carrier?source=projection", timeout=30) as r:
        return sum(row["shipments"] for row in json.load(r)["rows"])
ms = []
for i in range(50):
    order = "O-17F%03d" % i
    post("/orders/" + order)
    post("/orders/" + order + "/payment")
    before = projected()
    start = time.monotonic()
    post("/orders/" + order + "/shipment?carrier=FASTLINE")
    while projected() == before:
        time.sleep(0.005)
        if time.monotonic() - start > 30:
            sys.exit("the projection never counted " + order)
    ms.append((time.monotonic() - start) * 1000)
print("median %.0f ms, min %.0f ms, max %.0f ms" % (statistics.median(ms), min(ms), max(ms)))
PY
}

# --- the commit-order gap ------------------------------------------------------------------------------------------
held_and_open() {
    app_logged "held ShipOrder O-1701 after append" \
        && (( $(sql "SELECT count(*) FROM pg_stat_activity WHERE datname = 'edp' AND state = 'idle in transaction'") >= 1 ))
}
later_commit_is_not_counted() {
    expect "$(command_code O-1702 shipment carrier=FASTLINE)" 201 || return 1
    sleep 3 # several ticks
    local sample; sample="$(report projection)"
    (( $(field lagEvents <<<"$sample") >= 1 )) || return 1
    expect "$(total projection)" "$BEFORE" && expect "$(total replay)" $((BEFORE + 1))
}
both_counted_after_the_hold() {
    wait "$HOLD_PID"; HOLD_PID=""
    poll "$T_SMALL" lag_zero && projection_equals_replay && expect "$(total projection)" $((BEFORE + 2))
}
survives_a_restart() {
    docker restart "$APP" >/dev/null
    poll 60 ready && poll "$T_SMALL" lag_zero && projection_equals_replay && expect "$(total projection)" $((BEFORE + 2))
}
few_rows_at_every_size() { local n; while read -r n; do (( n <= 60 )) || return 1; done < "$WORK/rows"; }

echo "Starting the stack"
# Fresh start: remove any containers and volumes left by an earlier run (KEEP=1, an aborted run),
# then create every container and anonymous volume anew.
"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
"${COMPOSE[@]}" up -d --build --force-recreate --renew-anon-volumes --wait
poll 60 ready || fail_loudly "order-service is not ready"
: > "$WORK/rows"

echo "Infrastructure"
precondition "order-service is ready" ready
precondition "the store starts empty: no events, both append-only triggers present, no projection rows, checkpoint at 0" store_is_empty

echo "1,000 orders"
load_history 1 1000 3600
check "the replay of 1,000 orders equals the SQL oracle" replay_equals_oracle
check "the projection reaches lagEvents=0 within ${T_SMALL}s" caught_up_within "$T_SMALL"
check "the projection equals the replay and the oracle" projection_equals_replay
measure 1k

echo "10,000 orders"
load_history 1001 10000 36000
check "the replay of 10,000 orders equals the SQL oracle" replay_equals_oracle
check "the projection reaches lagEvents=0 within ${T_SMALL}s" caught_up_within "$T_SMALL"
check "the projection equals the replay and the oracle" projection_equals_replay
measure 10k

echo "100,000 orders"
load_history 10001 100000 360000
check "the replay of 100,000 orders equals the SQL oracle" replay_equals_oracle
check "the projection reaches lagEvents=0 within ${T_LARGE}s" caught_up_within "$T_LARGE"
check "the projection equals the replay and the oracle" projection_equals_replay
measure 100k
check "the projection read is at least $R times faster than the replay at 100,000 orders (medians of 11 reads)" projection_much_faster
check "a rebuild at 100,000 orders starts with work left and fewer shipments than the replay, then catches up to equal it" rebuild_catches_up

echo "A command on a large store"
check "a shipment is in the next replay at once and in the projection within 2 seconds" live_command_counts
note "freshness over 50 commands: $(freshness)"

echo "A transaction that commits late"
DEMO_HOLD_FOR=O-1701:ShipOrder "${COMPOSE[@]}" up -d --no-deps --force-recreate --wait order-service
poll 60 ready || fail_loudly "order-service is not ready after the restart with the hold"
poll "$T_SMALL" lag_zero || fail_loudly "the projection is not caught up before the hold"
expect "$(command_code O-1701)/$(command_code O-1701 payment)/$(command_code O-1702)/$(command_code O-1702 payment)" "201/201/201/201" \
    || fail_loudly "could not place and pay O-1701 and O-1702"
poll "$T_SMALL" lag_zero || fail_loudly "the projection is not caught up before the hold"
BEFORE="$(total projection)"
expect "$(total replay)" "$BEFORE" || fail_loudly "the replay and the projection differ before the hold"
curl -s -o /dev/null -X POST "$BASE/orders/O-1701/shipment?carrier=AIRCO" &
HOLD_PID=$!
precondition "ShipOrder O-1701 is held after its append: logged, and the service's connection is idle in transaction" poll 20 held_and_open
check "ShipOrder O-1702 commits while O-1701 is held: lagEvents is at least 1, the projection has not moved and a replay already counts O-1702" later_commit_is_not_counted
check "after the hold the projection equals the replay: both orders are counted" both_counted_after_the_hold

echo "A restart"
check "after a restart the projection resumes from its checkpoint: lagEvents=0, equal to the replay, nothing counted twice" survives_a_restart

echo "Size"
check "the projection holds at most 60 rows (2 carriers x 30 days) at every size" few_rows_at_every_size
note "events table $(sql "SELECT pg_size_pretty(pg_total_relation_size('orders.events'))"), projection $(sql "SELECT pg_size_pretty(pg_total_relation_size('orders.carrier_daily_shipments'))")"

echo "Observability"
check "Prometheus holds projection_lag_events for order-service" poll 90 prom_holds 'projection_lag_events{service_name="order-service"}'
check "Tempo has a trace for order-service" poll 90 tempo_has_trace order-service
check "Loki has logs for order-service" poll 90 loki_has_log order-service

echo "$PASSED/$TOTAL checks passed"
[[ "$PASSED" == "$TOTAL" ]]
