#!/usr/bin/env bash
# Proves the row-versus-event difference against the real stack.
set -uo pipefail
cd "$(dirname "$0")"
source ../../scripts/stack-guard.sh edp-01 || exit 1

BASE=http://localhost:8080
PSQL=(docker exec -e PGPASSWORD=edp edp-postgres psql -h 127.0.0.1 -U edp -d edp -tA)
pass=0; total=0
check() { # check <description> <command...>
    total=$((total + 1)); local d="$1"; shift
    if "$@"; then pass=$((pass + 1)); echo "PASS $total: $d"; else echo "FAIL $total: $d"; fi
}
cleanup() { [[ "${KEEP:-0}" == 1 ]] || docker compose down -v >/dev/null 2>&1; }
trap cleanup EXIT

docker compose up -d --build --wait || { echo "compose up failed"; exit 1; }

ID="C-$(date +%s)"
status() { curl -s -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' -d "{\"to\":\"$2\"}" "$BASE/consignments/$1/status"; }
count_events() { "${PSQL[@]}" -c "SELECT count(*) FROM consignment_event WHERE stream_id='$ID'"; }
statuses() { grep -o '"status":"[A-Z_]*"' | cut -d'"' -f4 | paste -sd, -; }

curl -sf -H 'Content-Type: application/json' \
    -d "{\"id\":\"$ID\",\"origin\":\"NLRTM\",\"destination\":\"SGSIN\",\"weightKg\":1250.5,\"carrier\":\"FASTLINE\"}" \
    "$BASE/consignments" >/dev/null
for s in LOADED IN_TRANSIT CLEARED; do status "$ID" "$s" >/dev/null; done

check "one consignment row for the stream" \
    test "$("${PSQL[@]}" -c "SELECT count(*) FROM consignment WHERE id='$ID'")" = 1
check "four events (booked plus three changes) for the stream" test "$(count_events)" = 4
check "replayed document equals the row document" \
    test "$(curl -s "$BASE/consignments/$ID")" = "$(curl -s "$BASE/consignments/$ID/replayed")"
check "row shows only the latest status" \
    test "$(curl -s "$BASE/consignments/$ID" | statuses)" = CLEARED
check "history lists BOOKED, LOADED, IN_TRANSIT, CLEARED" \
    test "$(curl -s "$BASE/consignments/$ID/history" | statuses)" = BOOKED,LOADED,IN_TRANSIT,CLEARED
check "illegal transition returns 409" test "$(status "$ID" LOADED)" = 409
check "illegal transition appended nothing" test "$(count_events)" = 4
check "UPDATE on consignment_event is refused" \
    bash -c "! \"\${@}\" -c \"UPDATE consignment_event SET type='X' WHERE stream_id='$ID'\" >/dev/null 2>&1" _ "${PSQL[@]}"
check "events unchanged after the refused UPDATE" test "$(count_events)" = 4
check "Tempo has a trace for crud-vs-events" bash -c '
    for _ in $(seq 40); do
        curl -s "http://localhost:3200/api/search?tags=service.name%3Dcrud-vs-events" | grep -q traceID && exit 0
        sleep 3
    done; exit 1'

echo "$pass/$total checks passed"
[[ "$pass" == "$total" ]]
