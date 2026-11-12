#!/usr/bin/env bash
# Starts the stack and checks the observable effects of the idempotent consumer: one decrement per event id
# across a duplicate record and across a crash between the commit and the acknowledgement, the double
# decrement without the inbox, and Camel's Idempotent Consumer with a JPA repository, which joins the route's
# transaction. INBOX_ENABLED=false and CAMEL_TRANSACTED=false are the experiments that make guards fail.
# KEEP=1 leaves the stack running afterwards.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-11

COMPOSE=(docker compose -f compose.yaml)
SERVICE=edp-inventory-service
TOPIC=edp.orders.placed
PSQL=(docker exec -e PGPASSWORD=edp edp-postgres psql -h 127.0.0.1 -U edp -d edp -tA)
KAFKA_BIN=/opt/kafka/bin
BROKER=localhost:9092
TOTAL=0
PASSED=0

# Event ids (the id header): A is delivered twice, B once, C is the one that meets the crash, D the failing one.
A=11111111-1111-4111-8111-111111111111
B=22222222-2222-4222-8222-222222222222
C=33333333-3333-4333-8333-333333333333
D=44444444-4444-4444-8444-444444444444

# The inbox switch for the main phases; the comparison phase turns it off for itself.
MAIN_INBOX="${INBOX_ENABLED:-true}"
export INBOX_ENABLED="$MAIN_INBOX"
export CRASH_AFTER_COMMIT_FOR="" FAIL_AFTER_EFFECT_FOR="" INVENTORY_PROFILE=prod INVENTORY_GROUP=inventory-inbox
export CAMEL_TRANSACTED="${CAMEL_TRANSACTED:-true}"

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
precondition() { # precondition "<description>" <command...>: a check that stops the run when it does not hold
    local desc="$1"; shift
    TOTAL=$((TOTAL + 1))
    if "$@" >/dev/null 2>&1; then
        PASSED=$((PASSED + 1)); printf 'PASS %d: precondition: %s\n' "$TOTAL" "$desc"
    else
        printf 'PRECONDITION FAILED %d: %s\n' "$TOTAL" "$desc" >&2
        exit 1
    fi
}

poll() { # poll <seconds> <command...>: retry every 2 s until the command succeeds
    local end=$((SECONDS + $1)); shift
    until "$@"; do
        (( SECONDS < end )) || return 1
        sleep 2
    done
}

expect() { [[ "$1" == "$2" ]]; } # expect <actual> <expected>
fail_loudly() { printf 'PRECONDITION FAILED: %s\n' "$1" >&2; exit 1; }

sql() { "${PSQL[@]}" -c "$1"; }
ready() { curl -sf http://localhost:8080/q/health/ready >/dev/null; }
stock() { sql "SELECT quantity_on_hand FROM inventory.inventory_item WHERE sku='SKU-1'"; }
inbox_ids() { sql "SELECT coalesce(string_agg(event_id::text, ',' ORDER BY event_id), '') FROM inventory.inbox"; }
repository_ids() { sql "SELECT coalesce(string_agg(messageid, ',' ORDER BY messageid), '') FROM inventory.camel_messageprocessed WHERE processorname='inventory-reservations'"; }
repository_count() { sql "SELECT count(*) FROM inventory.camel_messageprocessed WHERE processorname='inventory-reservations'"; }
reset_data() { sql "UPDATE inventory.inventory_item SET quantity_on_hand=100 WHERE sku='SKU-1'; TRUNCATE inventory.inbox, inventory.camel_messageprocessed" >/dev/null; }

# Start (or restart) the service with the current environment and wait until it is ready.
restart_service() {
    "${COMPOSE[@]}" up -d --no-deps --force-recreate --wait inventory-service >/dev/null
    poll 60 ready || fail_loudly "inventory-service is not ready"
}

# Produce records in one call: lines of "<id>|<orderId>|<quantity>".
produce() {
    local line id order qty
    for line in "$@"; do
        IFS='|' read -r id order qty <<<"$line"
        printf 'id:%s\t%s\t{"orderId":"%s","customerId":"CU-77","itemSku":"SKU-1","quantity":%s,"amount":59.90}\n' "$id" "$order" "$order" "$qty"
    done | docker exec -i edp-kafka "$KAFKA_BIN/kafka-console-producer.sh" --bootstrap-server "$BROKER" --topic "$TOPIC" \
        --reader-property parse.key=true --reader-property parse.headers=true >/dev/null 2>&1
}

# Every record of the topic as "Offset:<n><TAB>headers<TAB>key<TAB>value", from the beginning.
dump() {
    docker exec edp-kafka "$KAFKA_BIN/kafka-console-consumer.sh" --bootstrap-server "$BROKER" --topic "$TOPIC" \
        --from-beginning --timeout-ms 6000 --formatter-property print.offset=true --formatter-property print.headers=true \
        --formatter-property print.key=true 2>/dev/null | grep -a '^Offset:' || true
}
records_with_id() { dump | awk -v h="id:$1" 'index($0, h) { n++ } END { print n + 0 }'; }
offset_of() { dump | awk -F'\t' -v h="id:$1" 'index($2, h) { sub(/^Offset:/, "", $1); print $1; exit }'; }

# The row of the consumer group for the topic: CURRENT-OFFSET is $4, LAG $6 and CONSUMER-ID $7.
group_row() { docker exec edp-kafka "$KAFKA_BIN/kafka-consumer-groups.sh" --bootstrap-server "$BROKER" --describe --group "$1" 2>/dev/null | awk -v t="$TOPIC" '$2 == t { print; exit }'; }
group_lag() { group_row "$1" | awk '{ print $6 }'; }
group_committed() { group_row "$1" | awk '{ print $4 }'; }
lag_is_zero() { [[ "$(group_lag "$1")" == "0" ]]; }
member_assigned() { local c; c="$(group_row "$1" | awk '{ print $7 }')"; [[ -n "$c" && "$c" != "-" ]]; }
committed_at_most() { local c; c="$(group_committed "$1")"; [[ "$c" =~ ^[0-9]+$ ]] && (( c <= $2 )); } # an empty or "-" describe output fails

exited_by_halt() { [[ "$(docker inspect -f '{{.State.Status}} {{.State.ExitCode}}' "$SERVICE")" == "exited 137" ]]; }
stock_is() { [[ "$(stock)" == "$1" ]]; }
records_are() { [[ "$(records_with_id "$1")/$(records_with_id "$3")" == "$2/$4" ]]; } # records_are <idA> <nA> <idB> <nB>
log_has() { [[ "$(log_count "$1")" -ge 1 ]]; }
reset_camel_offsets() {
    docker exec edp-kafka "$KAFKA_BIN/kafka-consumer-groups.sh" --bootstrap-server "$BROKER" --group inventory-camel --topic "$TOPIC" \
        --reset-offsets --to-earliest --execute >/dev/null 2>&1
}
log_count() { docker logs "$SERVICE" 2>&1 | awk -v p="$1" 'index($0, p) { n++ } END { print n + 0 }'; }

tempo_has_trace() { local out; out="$(curl -sf -G "http://localhost:3200/api/search" --data-urlencode "tags=service.name=$1")" || return 1; [[ "$out" == *'"traceID"'* ]]; }
prom_holds() { curl -sf -G "http://localhost:19090/api/v1/query" --data-urlencode "query=$1" | grep -q '"value"'; }
loki_has_log() { local out; out="$(curl -sf -G "http://localhost:3100/loki/api/v1/query_range" --data-urlencode "query={service_name=\"$1\"}")" || return 1; [[ "$out" == *'"values"'* ]]; }

echo "Starting the stack"
# Fresh start: remove any containers and volumes left by an earlier run (KEEP=1, an aborted run),
# then create every container and anonymous volume anew.
"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
"${COMPOSE[@]}" up -d --build --force-recreate --renew-anon-volumes --wait
poll 60 ready || fail_loudly "inventory-service is not ready"

echo "Infrastructure"
check "inventory-service is ready and group inventory-inbox has partition 0 assigned" poll 60 member_assigned inventory-inbox
check "SKU-1 starts at 100 and the inbox is empty" expect "$(stock)/$(inbox_ids)" "100/"

echo "A duplicate record"
produce "$A|O-1101|3" "$A|O-1101|3" "$B|O-1102|2"
precondition "the topic holds two records with id A and one with id B" \
    poll 30 records_are "$A" 2 "$B" 1
check "group inventory-inbox has no lag" poll 60 lag_is_zero inventory-inbox
check "SKU-1 is 95: A was applied once" expect "$(stock)" 95
check "the inbox holds exactly A and B" expect "$(inbox_ids)" "$A,$B"
check "the log shows A skipped once" expect "$(log_count "duplicate event $A skipped")" 1

echo "A crash between the commit and the acknowledgement"
BEFORE_C="$(stock)"
export CRASH_AFTER_COMMIT_FOR=O-1103
restart_service
produce "$C|O-1103|4"
check "the service exits with 137 after reserving O-1103" poll 60 exited_by_halt
OFFSET_C="$(offset_of "$C")"
precondition "the committed offset of inventory-inbox is not past the record of C" committed_at_most inventory-inbox "$OFFSET_C"
precondition "the reservation of C was committed before the crash" expect "$(stock)" "$((BEFORE_C - 4))"
export CRASH_AFTER_COMMIT_FOR=""
restart_service
check "group inventory-inbox has no lag after the restart" poll 90 lag_is_zero inventory-inbox
check "SKU-1 is 91: C was applied once" expect "$(stock)" 91
check "the redelivery of C was skipped and its id is in the inbox" \
    expect "$(log_count "duplicate event $C skipped")/$(inbox_ids)" "1/$A,$B,$C"

echo "Comparison: without the inbox"
reset_data
export INBOX_ENABLED=false INVENTORY_GROUP=inventory-no-inbox
restart_service
check "group inventory-no-inbox read the same four records and has no lag" poll 90 lag_is_zero inventory-no-inbox
check "SKU-1 is 88: A was applied twice" expect "$(stock)" 88
check "the inbox is still empty" expect "$(inbox_ids)" ""

echo "Camel Idempotent Consumer with a JPA repository"
reset_data
export INBOX_ENABLED="$MAIN_INBOX" INVENTORY_PROFILE=camel-repository
restart_service
check "group inventory-camel has no lag" poll 90 lag_is_zero inventory-camel
check "SKU-1 is 91: A was applied once" expect "$(stock)" 91
check "the repository holds exactly A, B and C for the route" expect "$(repository_ids)" "$A,$B,$C"
check "the topic holds four records and the repository three keys: the duplicate of A was skipped; the inbox is untouched" \
    expect "$(dump | wc -l)/$(repository_count)/$(inbox_ids)" "4/3/"

echo "Camel: a reservation that fails after the decrement"
export FAIL_AFTER_EFFECT_FOR=O-1104
restart_service
produce "$D|O-1104|5"
precondition "the reservation of O-1104 failed after the decrement" poll 60 log_has "demo failure after the effect for O-1104"
check "SKU-1 is still 91: the failed effect rolled back" expect "$(stock)" 91
check "the repository does not hold D: the key rolled back with the effect" expect "$(repository_ids)" "$A,$B,$C"
"${COMPOSE[@]}" stop inventory-service >/dev/null
poll 40 reset_camel_offsets || fail_loudly "the offsets of inventory-camel could not be reset"
export FAIL_AFTER_EFFECT_FOR=""
restart_service
check "group inventory-camel has no lag after the redelivery" poll 90 lag_is_zero inventory-camel
check "SKU-1 is 86: O-1104 was reserved on its redelivery" poll 30 stock_is 86
check "the repository holds A, B, C and D" expect "$(repository_ids)" "$A,$B,$C,$D"

echo "Observability"
check "Tempo has a trace for inventory-service" poll 90 tempo_has_trace inventory-service
check "Loki has logs for inventory-service" poll 90 loki_has_log inventory-service
check "Prometheus holds inventory.inbox.duplicates above 0 for inventory-service" \
    poll 90 prom_holds 'max_over_time(inventory_inbox_duplicates_total{service_name="inventory-service"}[1h]) > 0'

echo "$PASSED/$TOTAL checks passed"
[[ "$PASSED" == "$TOTAL" ]]
