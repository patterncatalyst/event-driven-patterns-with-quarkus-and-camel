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

status() { curl -s -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' -d "{\"to\":\"$2\"}" "$BASE/orders/$1/status"; }
statuses() { grep -o '"status":"[A-Z_]*"' | cut -d'"' -f4 | paste -sd, -; }
count_events() { "${PSQL[@]}" -c "SELECT count(*) FROM order_event WHERE stream_id='$ID'"; }
count_all() { "${PSQL[@]}" -c "SELECT count(*) FROM order_event"; }
tempo_has_trace() { curl -sf -G "http://localhost:3200/api/search" --data-urlencode 'tags=service.name=crud-vs-events' | grep -q '"traceID"'; }
expect() { [[ "$1" == "$2" ]]; } # expect <actual> <expected>
refused() { ! "${PSQL[@]}" -c "$1" ; } # the statement must fail

# Fresh start: remove any containers and volumes left by an earlier run (KEEP=1, an aborted run),
# then create every container and anonymous volume anew.
"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
"${COMPOSE[@]}" up -d --build --force-recreate --renew-anon-volumes --wait

ID="O-$(date +%s)"
PLACE_CODE=$(curl -s -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' \
    -d "{\"id\":\"$ID\",\"customerId\":\"CU-77\",\"itemSku\":\"SKU-42\",\"quantity\":3,\"amount\":19.999}" \
    "$BASE/orders")
PAY_CODE=$(status "$ID" PAID)
SHIP_CODE=$(status "$ID" SHIPPED)
DELIVER_CODE=$(status "$ID" DELIVERED)

check "place returns 201" expect "$PLACE_CODE" 201
check "pay returns 204" expect "$PAY_CODE" 204
check "ship returns 204" expect "$SHIP_CODE" 204
check "deliver returns 204" expect "$DELIVER_CODE" 204
check "one order row for the stream" expect "$("${PSQL[@]}" -c "SELECT count(*) FROM orders WHERE id='$ID'")" 1
check "four events (placed plus three changes) for the stream" expect "$(count_events)" 4
check "amount 19.999 stored at two decimals as 20.00" \
    expect "$("${PSQL[@]}" -c "SELECT amount FROM orders WHERE id='$ID'")" 20.00
check "replayed document equals the row document" \
    expect "$(curl -s "$BASE/orders/$ID")" "$(curl -s "$BASE/orders/$ID/replayed")"
check "row shows only the latest status" expect "$(curl -s "$BASE/orders/$ID" | statuses)" DELIVERED
check "history lists PLACED, PAID, SHIPPED, DELIVERED" \
    expect "$(curl -s "$BASE/orders/$ID/history" | statuses)" PLACED,PAID,SHIPPED,DELIVERED
check "illegal transition DELIVERED to PAID returns 409" expect "$(status "$ID" PAID)" 409
check "illegal transition appended nothing" expect "$(count_events)" 4
check "placing the same id again returns 409" expect "$(curl -s -o /dev/null -w '%{http_code}' \
    -H 'Content-Type: application/json' \
    -d "{\"id\":\"$ID\",\"customerId\":\"CU-77\",\"itemSku\":\"SKU-42\",\"quantity\":1,\"amount\":5}" \
    "$BASE/orders")" 409
check "UPDATE on order_event is refused" refused "UPDATE order_event SET type='X' WHERE stream_id='$ID'"
check "events unchanged after the refused UPDATE" expect "$(count_events)" 4
check "DELETE on order_event is refused" refused "DELETE FROM order_event WHERE stream_id='$ID'"
ALL=$(count_all)
check "TRUNCATE order_event is refused" refused "TRUNCATE order_event"
check "events unchanged after the refused TRUNCATE" expect "$(count_all)" "$ALL"
check "Tempo has a trace for crud-vs-events" poll 90 tempo_has_trace

echo "$PASSED/$TOTAL checks passed"
[[ "$PASSED" == "$TOTAL" ]]
