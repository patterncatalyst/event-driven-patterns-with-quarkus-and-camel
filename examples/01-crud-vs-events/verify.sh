#!/usr/bin/env bash
# Starts the stack with the crud-vs-events app and checks the row, the stream and the guards.
# KEEP=1 leaves the stack running afterwards.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-01

COMPOSE=(docker compose -f compose.yaml)
BASE=http://localhost:8080
PSQL=(docker exec -e PGPASSWORD=edp edp-postgres psql -h 127.0.0.1 -U edp -d edp -tA)
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

poll() { # poll <seconds> <command...>: retry every 3 s until the command succeeds
    local end=$((SECONDS + $1)); shift
    until "$@"; do
        (( SECONDS < end )) || return 1
        sleep 3
    done
}

status() { curl -s -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' -d "{\"to\":\"$2\"}" "$BASE/consignments/$1/status"; }
statuses() { grep -o '"status":"[A-Z_]*"' | cut -d'"' -f4 | paste -sd, -; }
count_events() { "${PSQL[@]}" -c "SELECT count(*) FROM consignment_event WHERE stream_id='$ID'"; }
count_all() { "${PSQL[@]}" -c "SELECT count(*) FROM consignment_event"; }
tempo_has_trace() { curl -sf -G "http://localhost:3200/api/search" --data-urlencode 'tags=service.name=crud-vs-events' | grep -q '"traceID"'; }
expect() { [[ "$1" == "$2" ]]; } # expect <actual> <expected>
refused() { ! "${PSQL[@]}" -c "$1" ; } # the statement must fail

"${COMPOSE[@]}" up -d --build --wait

ID="C-$(date +%s)"
curl -sf -H 'Content-Type: application/json' \
    -d "{\"id\":\"$ID\",\"origin\":\"NLRTM\",\"destination\":\"SGSIN\",\"weightKg\":1250.5,\"carrier\":\"FASTLINE\"}" \
    "$BASE/consignments" >/dev/null
for s in LOADED IN_TRANSIT CLEARED; do status "$ID" "$s" >/dev/null; done

check "one consignment row for the stream" expect "$("${PSQL[@]}" -c "SELECT count(*) FROM consignment WHERE id='$ID'")" 1
check "four events (booked plus three changes) for the stream" expect "$(count_events)" 4
check "replayed document equals the row document" \
    expect "$(curl -s "$BASE/consignments/$ID")" "$(curl -s "$BASE/consignments/$ID/replayed")"
check "row shows only the latest status" expect "$(curl -s "$BASE/consignments/$ID" | statuses)" CLEARED
check "history lists BOOKED, LOADED, IN_TRANSIT, CLEARED" \
    expect "$(curl -s "$BASE/consignments/$ID/history" | statuses)" BOOKED,LOADED,IN_TRANSIT,CLEARED
check "illegal transition returns 409" expect "$(status "$ID" LOADED)" 409
check "illegal transition appended nothing" expect "$(count_events)" 4
check "UPDATE on consignment_event is refused" refused "UPDATE consignment_event SET type='X' WHERE stream_id='$ID'"
check "events unchanged after the refused UPDATE" expect "$(count_events)" 4
ALL=$(count_all)
check "TRUNCATE consignment_event is refused" refused "TRUNCATE consignment_event"
check "events unchanged after the refused TRUNCATE" expect "$(count_all)" "$ALL"
check "Tempo has a trace for crud-vs-events" poll 90 tempo_has_trace

echo "$PASSED/$TOTAL checks passed"
[[ "$PASSED" == "$TOTAL" ]]
