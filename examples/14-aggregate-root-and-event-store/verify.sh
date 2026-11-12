#!/usr/bin/env bash
# Starts the stack, registers the outbox connector and checks the observable effects of an event-sourced order
# aggregate: the stream is the only state, a redelivered payment changes nothing, a command that loses a version race
# is decided again (refused or accepted), a stale If-Match is answered 412, only OrderPlaced and OrderCancelled reach
# Kafka, and the same payments run through a Camel route. RETRY_MODE=reappend, RETRY_MAX=0 and CAMEL_REDELIVERIES=0 are
# the negative witnesses (see the README). KEEP=1 leaves the stack running afterwards.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-14

COMPOSE=(docker compose -f compose.yaml)
APP=edp-order-service
ORDERS=http://localhost:8080
CONNECT=http://localhost:8083
CONNECTOR=order-outbox
CONNECTOR_JSON="${CONNECTOR_JSON:-connect/order-outbox.json}"
PAYMENTS=edp.payments.processed
SHIPMENTS=edp.shipping.scheduled
PLACED=edp.orders.placed
CANCELLED=edp.orders.cancelled
PSQL=(docker exec -e PGPASSWORD=edp edp-postgres psql -h 127.0.0.1 -U edp -d edp -tA)
TMP="$(mktemp -d)"
TOTAL=0
PASSED=0

# Demo code switches, passed to the service through compose. The pause holds a listed command between deciding and
# appending, so the competing input of each race can be sent while the command is in flight.
export DEMO_PAUSE_FOR="${DEMO_PAUSE_FOR:-O-1402:Cancel,O-1403:Cancel,O-1405:MarkPaid}"
export DEMO_PAUSE_MS="${DEMO_PAUSE_MS:-8000}"

cleanup() {
    rm -rf "$TMP"
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

kafka() { docker exec edp-kafka "/opt/kafka/bin/$1" --bootstrap-server localhost:9092 "${@:2}"; }
sql() { "${PSQL[@]}" -c "$1"; }
ready() { curl -sf http://localhost:8080/q/health/ready >/dev/null; }
logs() { docker logs "$APP" 2>&1; }
log_count() { logs | grep -cF -- "$1" || true; }
# grep without -q: under pipefail an early-exiting grep would fail the pipeline with SIGPIPE.
log_has() { logs | grep -F -- "$1" >/dev/null; }

# --- the service
place() { # place <id>: prints "<status> <etag>"
    curl -s -D "$TMP/head" -o /dev/null -H 'Content-Type: application/json' \
        -d "{\"id\":\"$1\",\"customerId\":\"CU-14\",\"itemSku\":\"SKU-14\",\"quantity\":1,\"amount\":25.00}" "$ORDERS/orders"
    head_summary
}
head_summary() { tr -d '\r' < "$TMP/head" | awk 'NR == 1 {s = $2} tolower($1) == "etag:" {e = $2} END {print s, e}'; }
cancel() { # cancel <id> [If-Match tag]: prints "<status> <etag>"
    local extra=()
    [[ -n "${2:-}" ]] && extra=(-H "If-Match: $2")
    curl -s -D "$TMP/head" -o "$TMP/body" -X POST "${extra[@]}" "$ORDERS/orders/$1/cancellation"
    head_summary
}
pay_json() { printf '{"payment":{"id":"P-%s","orderId":"%s","amount":25.00,"status":"COMPLETED"}}' "$1" "$1"; }
ship_json() { printf '{"shipment":{"id":"S-%s","orderId":"%s","carrier":"FASTLINE","trackingNumber":"FL-%s","status":"SCHEDULED"}}' "$1" "$1" "$1"; }
produce() { # produce <topic> then "<key>|<json>" lines on stdin: one producer call
    docker exec -i edp-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic "$1" \
        --reader-property parse.key=true --reader-property 'key.separator=|' >/dev/null 2>&1
}
send_payment() { printf '%s|%s\n' "$1" "$(pay_json "$1")" | produce $PAYMENTS; }
send_shipment() { printf '%s|%s\n' "$1" "$(ship_json "$1")" | produce $SHIPMENTS; }

# --- the stream
stream() { sql "SELECT coalesce(string_agg(version || ':' || type, ',' ORDER BY version), '') FROM orders.events WHERE stream_id = '$1'"; }
stream_is() { [[ "$(stream "$1")" == "$2" ]]; }
event_count() { sql "SELECT count(*) FROM orders.events WHERE stream_id = '$1'"; }
has_events() { [[ "$(event_count "$1")" == "$2" ]]; } # has_events <id> <count>
tables() { sql "SELECT string_agg(table_name, ',' ORDER BY table_name) FROM information_schema.tables WHERE table_schema = 'orders'"; }
snapshot_of_streams() { sql "SELECT stream_id || '=' || count(*) || '@' || max(version) FROM orders.events GROUP BY stream_id ORDER BY stream_id"; }
# Every stream is contiguous from version 1 and every step is one the order lifecycle allows.
histories_legal() {
    sql "SELECT stream_id || '|' || string_agg(version || ':' || type, ',' ORDER BY version, position) FROM orders.events GROUP BY stream_id" |
    python3 -I -c "
import sys
allowed = {'OrderPlaced': {'OrderPaid', 'OrderCancelled'}, 'OrderPaid': {'OrderShipped', 'OrderCancelled'}, 'OrderShipped': set(), 'OrderCancelled': set()}
bad = 0
for line in sys.stdin:
    line = line.strip()
    if not line:
        continue
    stream_id, rest = line.split('|', 1)
    steps = [s.split(':') for s in rest.split(',')]
    versions = [int(v) for v, _ in steps]
    types = [t for _, t in steps]
    if versions != list(range(1, len(steps) + 1)) or types[0] != 'OrderPlaced':
        bad += 1
    for a, b in zip(types, types[1:]):
        if b not in allowed.get(a, set()):
            bad += 1
sys.exit(1 if bad else 0)"
}

# --- Kafka
normalise() {
    python3 -I -c "
import re, sys
text = sys.stdin.buffer.read().decode('utf-8', 'replace')
for rec in re.split(r'(?m)^(?=Offset:[0-9]+\t)', text):
    if rec.startswith('Offset:'):
        print(rec.rstrip('\n').replace('\n', ' '))
"
}
dump() { # dump <topic>: every record as "Offset:N<TAB>headers<TAB>key<TAB>value"
    kafka kafka-console-consumer.sh --topic "$1" --from-beginning --timeout-ms 6000 \
        --formatter-property print.key=true --formatter-property print.offset=true \
        --formatter-property print.headers=true 2>/dev/null | normalise || true
}
records_for() { dump "$1" | awk -F'\t' -v k="$2" '$(NF - 1) == k' | wc -l; } # records_for <topic> <key>
total_records() { dump "$1" | grep -c '^Offset:' || true; }
group_assigned() { kafka kafka-consumer-groups.sh --describe --group "$1" 2>/dev/null | awk -v t="$2" '$2 == t && $3 == 0 && $7 != "-" {f = 1} END {exit !f}'; }
group_lag_zero() { # an inactive group prints "-" for its current offset: that is not lag 0
    kafka kafka-consumer-groups.sh --describe --group "$1" 2>/dev/null | awk -v t="$2" '
        $2 == t && $3 == 0 {seen = 1; if ($4 == "-" || $6 != 0) bad = 1} END {exit !(seen && !bad)}'
}
group_inactive() { # no partition of the topic is assigned to a member
    kafka kafka-consumer-groups.sh --describe --group "$1" 2>/dev/null | awk -v t="$2" '$2 == t && $3 == 0 {seen = 1; if ($7 != "-") active = 1} END {exit !(seen && !active)}'
}
partitions_of() { kafka kafka-topics.sh --describe --topic "$1" 2>/dev/null | grep -c 'Partition: [0-9]' || true; }

# --- the connector
connector_running() {
    curl -sf "$CONNECT/connectors/$CONNECTOR/status" | python3 -I -c "
import json, sys
d = json.load(sys.stdin)
sys.exit(0 if d['connector']['state'] == 'RUNNING' and d['tasks'] and all(t['state'] == 'RUNNING' for t in d['tasks']) else 1)"
}
plugin_listed() { local out; out="$(curl -sf "$CONNECT/connector-plugins")" || return 1; [[ "$out" == *io.debezium.connector.postgresql.PostgresConnector* ]]; }

# --- races
request_open() { kill -0 "$1" 2>/dev/null; }
paused() { log_has "paused $1 $2 at version $3"; } # paused <Command> <id> <version>
# A background request that is still open while the version it lost to already exists.
start_cancel() { ( curl -s -D "$TMP/$1.head" -o "$TMP/$1.body" -X POST "$ORDERS/orders/$1/cancellation" ) & CANCEL_PID=$!; }
cancel_result() { tr -d '\r' < "$TMP/$1.head" | awk 'NR == 1 {s = $2} tolower($1) == "etag:" {e = $2} END {print s, e}'; }

# --- observability
prom_holds() { curl -sf -G "http://localhost:19090/api/v1/query" --data-urlencode "query=$1" | grep '"value"' >/dev/null; }
tempo_has_trace() { local out; out="$(curl -sf -G "http://localhost:3200/api/search" --data-urlencode "tags=service.name=$1")" || return 1; [[ "$out" == *'"traceID"'* ]]; }
loki_has_log() { local out; out="$(curl -sf -G "http://localhost:3100/loki/api/v1/query_range" --data-urlencode "query={service_name=\"$1\"}")" || return 1; [[ "$out" == *'"values"'* ]]; }

get_summary() { # etag and status of GET /orders/<id>
    curl -s -D - "$ORDERS/orders/$1" | tr -d '\r' | awk 'tolower($1) == "etag:" {e = $2} /"status"/ {s = ($0 ~ /PAID/) ? "PAID" : "OTHER"} END {print e, s}'
}
stream_and_lifecycle() { stream_is "$1" "$2" && histories_legal; }
race3_settled() {
    stream_is O-1405 "1:OrderPlaced,2:OrderCancelled" && refusal_and_conflict_logged O-1405 MarkPaid "CANCELLED to PAID" &&
        group_lag_zero order-payments $PAYMENTS
}
camel_route_ready() { log_has "payment-commands" && group_assigned order-payments-camel $PAYMENTS; }
camel_race_settled() { # one redelivery: the payment is loaded at version 1, conflicts, is loaded at version 2 and refused
    stream_is O-1406 "1:OrderPlaced,2:OrderCancelled" && refusal_and_conflict_logged O-1406 MarkPaid "CANCELLED to PAID" &&
        [[ "$(log_count 'load O-1406 at version 2')" == 1 ]] && log_has "On delivery attempt" &&
        log_has "camel acknowledged a refused command" && group_lag_zero order-payments-camel $PAYMENTS
}

# Predicates the checks poll.
one_placed_record() { # edp.orders.placed holds one record keyed O-1401: id header, eventType OrderPlaced, amount 25
    local line; line="$(dump $PLACED | awk -F'\t' '$(NF - 1) == "O-1401"')"
    [[ "$(printf '%s\n' "$line" | grep -c .)" == 1 && "$line" == *eventType:OrderPlaced* && "$line" == *id:* ]] &&
        printf '%s\n' "$line" | grep -E '"amount":25(\.0+)?[,}]' >/dev/null
}
payment_twice() { [[ "$(dump $PAYMENTS | awk -F'\t' '$(NF - 1) == "O-1401" && index($NF, "P-O-1401")' | wc -l)" == 2 ]]; }
no_paid_record_anywhere() {
    ! { dump $PLACED; dump $CANCELLED; } | grep OrderPaid >/dev/null && ! kafka kafka-topics.sh --list | grep '^outbox\.event\.' >/dev/null
}
topics_have_one_partition() { [[ "$(for t in $PLACED $CANCELLED $PAYMENTS $SHIPMENTS; do partitions_of "$t"; done | tr '\n' ' ')" == "1 1 1 1 " ]]; }
connector_and_topics() { connector_running && topics_have_one_partition; }
all_groups_assigned() { group_assigned order-payments $PAYMENTS && group_assigned order-shipments $SHIPMENTS; }
cancelled_records() { [[ "$(records_for $CANCELLED "$1")" == "$2" ]]; } # cancelled_records <id> <count>
record_count_is() { [[ "$(total_records "$1")" == "$2" ]]; }            # record_count_is <topic> <count>
payments_settled() { group_lag_zero order-payments $PAYMENTS && stream_is O-1401 "1:OrderPlaced,2:OrderPaid"; }
shipped_while_open() { stream_is "$1" "1:OrderPlaced,2:OrderPaid,3:OrderShipped" && request_open "$CANCEL_PID"; }
paid_while_open() { paused Cancel "$1" 1 && stream_is "$1" "1:OrderPlaced,2:OrderPaid" && request_open "$CANCEL_PID"; }
refusal_and_conflict_logged() { # refusal_and_conflict_logged <id> <Command> <reason>
    [[ "$(log_count "refused $2 $1: $3")" == 1 && "$(log_count "conflict $2 $1")" == 1 ]]
}

echo "Starting the stack"
# Fresh start: remove any containers and volumes left by an earlier run (KEEP=1, an aborted run),
# then create every container and anonymous volume anew.
"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
"${COMPOSE[@]}" up -d --build --force-recreate --renew-anon-volumes --wait
poll 60 ready || fail_loudly "order-service is not ready"
poll 30 plugin_listed || fail_loudly "Connect does not list the PostgreSQL connector"
curl -sf -X PUT -H 'Content-Type: application/json' --data @"$CONNECTOR_JSON" "$CONNECT/connectors/$CONNECTOR/config" >/dev/null \
    || fail_loudly "the connector was not accepted by Connect"

echo "Infrastructure"
precondition "order-service is ready and its groups own partition 0 of both input topics" poll 60 all_groups_assigned
precondition "the connector order-outbox is RUNNING and the four topics have one partition each" poll 60 connector_and_topics
check "the schema holds the events, the outbox and the migration history, and no table of order state" \
    expect "$(tables)" "events,flyway_schema_history,outboxevent"

echo "Place O-1401"
check "POST /orders returns 201 with ETag \"1\"" expect "$(place O-1401)" '201 "1"'
check "the stream O-1401 has one row: version 1, OrderPlaced" stream_is O-1401 "1:OrderPlaced"
check "edp.orders.placed holds one record keyed O-1401 with an id header, eventType OrderPlaced and the amount" \
    poll 60 one_placed_record
second="$(place O-1401)"
sleep 4
check "a second POST for O-1401 returns 409 and leaves one row and one record" \
    expect "$second/$(event_count O-1401)/$(records_for $PLACED O-1401)" "409 /1/1"

echo "A payment delivered twice"
# One producer call, two records with the same payment id.
printf '%s|%s\n%s|%s\n' O-1401 "$(pay_json O-1401)" O-1401 "$(pay_json O-1401)" | produce $PAYMENTS
precondition "edp.payments.processed holds two records for O-1401 with the same payment id" poll 30 payment_twice
check "the group order-payments has no lag and O-1401 is versions 1 and 2 with exactly one OrderPaid" poll 60 payments_settled
check "GET /orders/O-1401 returns PAID with ETag \"2\"" expect "$(get_summary O-1401)" '"2" PAID'
check "no record for OrderPaid on any edp.orders topic and no outbox.event topic" no_paid_record_anywhere

echo "Race 1: a cancellation that loses to a shipment is decided again and refused"
place O-1402 >/dev/null
send_payment O-1402
poll 60 has_events O-1402 2 || fail_loudly "O-1402 was not paid"
start_cancel O-1402
precondition "the Cancel for O-1402 is paused after it decided at version 2" poll 30 paused Cancel O-1402 2
send_shipment O-1402
precondition "version 3 OrderShipped exists while the cancel request is still open" poll 30 shipped_while_open O-1402
wait "$CANCEL_PID" || true
check "the cancellation returns 409 with the reason SHIPPED to CANCELLED" \
    expect "$(cancel_result O-1402 | cut -d' ' -f1)/$(grep -c 'SHIPPED to CANCELLED' "$TMP/O-1402.body")" "409/1"
check "the stream O-1402 has versions 1 to 3 and no OrderCancelled, and every history obeys the lifecycle" \
    stream_and_lifecycle O-1402 "1:OrderPlaced,2:OrderPaid,3:OrderShipped"
check "exactly one conflict was logged for O-1402" expect "$(log_count 'conflict Cancel O-1402')" 1
check "no record for O-1402 on edp.orders.cancelled" cancelled_records O-1402 0

echo "Race 2: a cancellation that loses to a payment is decided again and accepted"
place O-1403 >/dev/null
start_cancel O-1403
poll 30 paused Cancel O-1403 1 || fail_loudly "the Cancel for O-1403 was not paused"
send_payment O-1403
precondition "the Cancel for O-1403 paused after it decided at version 1, and version 2 OrderPaid exists while the request is open" \
    poll 30 paid_while_open O-1403
wait "$CANCEL_PID" || true
check "the cancellation returns 200 with ETag \"3\"" expect "$(cancel_result O-1403)" '200 "3"'
check "the stream O-1403 is OrderPlaced, OrderPaid, OrderCancelled at versions 1 to 3" \
    stream_is O-1403 "1:OrderPlaced,2:OrderPaid,3:OrderCancelled"
check "edp.orders.cancelled holds exactly one record for O-1403" poll 60 cancelled_records O-1403 1

echo "A client that names the version it saw"
place O-1404 >/dev/null
send_payment O-1404
poll 60 has_events O-1404 2 || fail_loudly "O-1404 was not paid"
check "a cancellation with If-Match \"1\" returns 412, adds no event and logs no conflict or retry" \
    expect "$(cancel O-1404 '"1"' | cut -d' ' -f1)/$(event_count O-1404)/$(log_count 'conflict Cancel O-1404')/$(log_count 'load O-1404 at version 2')" "412/2/0/0"
check "a cancellation with If-Match \"2\" returns 200 with ETag \"3\" and leaves one cancelled record" \
    expect "$(cancel O-1404 '"2"')/$(poll 60 cancelled_records O-1404 1 && echo one)" '200 "3"/one'

echo "Race 3: a payment from Kafka that loses to a cancellation is decided again and refused"
place O-1405 >/dev/null
send_payment O-1405
precondition "the MarkPaid for O-1405 is paused after it decided at version 1" poll 30 paused MarkPaid O-1405 1
check "the cancellation of O-1405 returns 200 while the payment is paused" expect "$(cancel O-1405 | cut -d' ' -f1)" 200
check "after the pause O-1405 is OrderPlaced, OrderCancelled; the refusal CANCELLED to PAID and one conflict are logged; no lag" \
    poll 60 race3_settled

echo "Camel: the same payments through a route"
BEFORE="$(snapshot_of_streams)"
COMMAND_PROFILE=camel-commands DEMO_PAUSE_FOR=O-1406:MarkPaid "${COMPOSE[@]}" up -d --no-deps --force-recreate --wait order-service
poll 60 ready || fail_loudly "order-service is not ready with the camel-commands profile"
precondition "the Camel route has started and order-payments-camel owns partition 0" poll 60 camel_route_ready
poll 90 group_lag_zero order-payments-camel $PAYMENTS || fail_loudly "order-payments-camel did not reach the end of the topic"
# Reset the group to the earliest offset while it is inactive, then read every payment again.
"${COMPOSE[@]}" stop order-service >/dev/null
poll 60 group_inactive order-payments-camel $PAYMENTS || fail_loudly "order-payments-camel is still active"
kafka kafka-consumer-groups.sh --group order-payments-camel --topic $PAYMENTS --reset-offsets --to-earliest --execute >/dev/null 2>&1 \
    || fail_loudly "could not reset order-payments-camel"
COMMAND_PROFILE=camel-commands DEMO_PAUSE_FOR=O-1406:MarkPaid "${COMPOSE[@]}" up -d --no-deps --wait order-service
poll 60 ready || fail_loudly "order-service is not ready after the reset"
poll 90 group_lag_zero order-payments-camel $PAYMENTS || fail_loudly "order-payments-camel did not read the topic again"
sleep 3
check "after the replay of every payment, every earlier stream is unchanged" expect "$(snapshot_of_streams)" "$BEFORE"
place O-1406 >/dev/null
send_payment O-1406
poll 30 paused MarkPaid O-1406 1 || fail_loudly "the MarkPaid for O-1406 was not paused"
cancel O-1406 >/dev/null
check "O-1406 is OrderPlaced, OrderCancelled; the route redelivered the payment once and the refusal was acknowledged" \
    poll 60 camel_race_settled
check "edp.orders.placed holds 6 records and edp.orders.cancelled 4" \
    expect "$(total_records $PLACED)/$(total_records $CANCELLED)" "6/4"

echo "Observability"
check "Prometheus holds order_command_conflicts_total for order-service" \
    poll 90 prom_holds 'max_over_time(order_command_conflicts_total{service_name="order-service"}[1h]) > 0'
check "Tempo has a trace for order-service" poll 90 tempo_has_trace order-service
check "Loki has logs for order-service" poll 90 loki_has_log order-service

if [[ "$PASSED" != "$TOTAL" ]]; then
    echo "order-service log, lines of the Camel phase:" >&2
    logs | grep -E "O-1406|camel|Camel" | tail -40 | cut -c1-260 >&2 || true
fi
echo "$PASSED/$TOTAL checks passed"
[[ "$PASSED" == "$TOTAL" ]]
