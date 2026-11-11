#!/usr/bin/env bash
# Starts the stack, registers the outbox connector and checks the observable effects of the transactional outbox:
# an event per committed order, none for a refused one, an event that survives a crash after the commit, and a
# backlog that survives a Connect outage. Two comparison runs (dual write, publish first and commit first) show
# what the outbox prevents. KEEP=1 leaves the stack running afterwards.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-08

COMPOSE=(docker compose -f compose.yaml)
ORDERS=http://localhost:8080
CONNECT=http://localhost:8083
CONNECTOR=order-outbox
CONNECTOR_JSON="${CONNECTOR_JSON:-connect/order-outbox.json}"
TOPIC=edp.orders.placed
SLOT=edp_order_outbox
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
ready() { curl -sf "$ORDERS/q/health/ready" >/dev/null; }
order_json() { printf '{"id":"%s","customerId":"CU-77","itemSku":"SKU-42","quantity":3,"amount":59.90}' "$1"; }
# The HTTP status of a POST, or 000 when the service goes away before it answers.
post() { # post <path> <orderId>
    curl -s -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' -d "$(order_json "$2")" "$ORDERS$1" || true
}
register_connector() {
    curl -sf -X PUT -H 'Content-Type: application/json' --data @"$CONNECTOR_JSON" "$CONNECT/connectors/$CONNECTOR/config"
}
connector_running() {
    curl -sf "$CONNECT/connectors/$CONNECTOR/status" | python3 -I -c "
import json, sys
d = json.load(sys.stdin)
sys.exit(0 if d['connector']['state'] == 'RUNNING' and d['tasks'] and all(t['state'] == 'RUNNING' for t in d['tasks']) else 1)"
}
plugin_listed() { local out; out="$(curl -sf "$CONNECT/connector-plugins")" || return 1; [[ "$out" == *io.debezium.connector.postgresql.PostgresConnector* ]]; }
slot_is() { [[ "$(sql "SELECT active FROM pg_replication_slots WHERE slot_name='$SLOT'")" == "$1" ]]; }

# Every record of the order topic as "headers<TAB>key<TAB>value", read from the beginning.
dump() {
    docker exec edp-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic "$TOPIC" \
        --from-beginning --timeout-ms 5000 --formatter-property print.headers=true --formatter-property print.key=true 2>/dev/null | grep -F $'\t' || true
}
records_for() { dump | awk -F'\t' -v k="$1" '$(NF-1) == k'; }
count_for() { records_for "$1" | wc -l; }
has_count() { [[ "$(count_for "$1")" == "$2" ]]; } # has_count <orderId> <n>
settles_at() { sleep "$3"; has_count "$1" "$2"; } # settles_at <orderId> <n> <seconds>: still n after a wait
orders_rows() { sql "SELECT count(*) FROM orders.orders WHERE id='$1'"; }
outbox_rows() { sql "SELECT count(*) FROM orders.outboxevent"; }
all_topics() { docker exec edp-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list; }
no_default_topic() { [[ "$(all_topics | grep -c '^outbox\.event\.' || true)" == 0 ]]; }

# The record of an order has an id header (a UUID) and eventType OrderPlaced, and its value is the order as a JSON object.
record_is_the_order() {
    local line headers value
    line="$(records_for "$1" | head -1)"
    headers="${line%%$'\t'*}"; value="${line##*$'\t'}"
    [[ "$headers" =~ (^|,)id:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(,|$) ]] || return 1
    [[ "$headers" == *eventType:OrderPlaced* ]] || return 1
    python3 -I -c "
import json, sys
d = json.loads(sys.argv[1])
assert isinstance(d, dict) and d['orderId'] == sys.argv[2] and d['customerId'] == 'CU-77' and d['status'] == 'PLACED'" "$value" "$1"
}
# The keys of the topic, in topic order, as one comma-separated string, for the orders O-4001 to O-4005.
keys_in_topic_order() { dump | awk -F'\t' '$(NF-1) ~ /^O-400[1-5]$/ {print $(NF-1)}' | paste -sd, -; }

tempo_has_trace() { local out; out="$(curl -sf -G "http://localhost:3200/api/search" --data-urlencode "tags=service.name=$1")" || return 1; [[ "$out" == *'"traceID"'* ]]; }
loki_has_log() { local out; out="$(curl -sf -G "http://localhost:3100/loki/api/v1/query_range" --data-urlencode "query={service_name=\"$1\"}")" || return 1; [[ "$out" == *'"values"'* ]]; }
exited_by_halt() { [[ "$(docker inspect -f '{{.State.Status}} {{.State.ExitCode}}' edp-order-service)" == "exited 137" ]]; }

echo "Starting the stack"
# Fresh start: remove any containers and volumes left by an earlier run (KEEP=1, an aborted run),
# then create every container and anonymous volume anew.
"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
"${COMPOSE[@]}" up -d --build --force-recreate --renew-anon-volumes --wait
poll 60 ready || fail_loudly "order-service is not ready"
poll 30 plugin_listed || fail_loudly "Connect does not list the PostgreSQL connector"
register_connector >/dev/null || fail_loudly "the connector was not accepted by Connect"

echo "Infrastructure"
check "Connect lists the Debezium PostgreSQL connector and order-service is ready" bash -c "curl -sf $CONNECT/connector-plugins | grep -q PostgresConnector && curl -sf $ORDERS/q/health/ready"
check "the order-outbox connector and its task are RUNNING" poll 60 connector_running
check "the replication slot is active" poll 30 slot_is t
check "the publication holds only the outbox table" \
    expect "$(sql "SELECT string_agg(schemaname || '.' || tablename, ',') FROM pg_publication_tables WHERE pubname='$SLOT'")" orders.outboxevent

echo "Happy path"
check "placing O-1001 returns 201" expect "$(post /orders O-1001)" 201
check "exactly one record for O-1001 reaches the topic, keyed by the order id" poll 30 has_count O-1001 1
check "the record has an id header, eventType OrderPlaced and the order as its JSON value" record_is_the_order O-1001
check "the order row exists and the outbox table is empty again" \
    expect "$(orders_rows O-1001)/$(outbox_rows)" "1/0"
check "no default outbox.event.* topic was created: the router used the topic column" no_default_topic

echo "A refused order"
check "a second O-1001 is refused with 409" expect "$(post /orders O-1001)" 409
check "the topic still holds one record for O-1001 and the table one row, after a wait" \
    expect "$(sleep 6; count_for O-1001)/$(orders_rows O-1001)" "1/1"

echo "Comparison: publish first, then commit"
check "dual write, publish first: O-3001 returns 201 and one record" \
    expect "$(post '/orders/dual-write?mode=publish-first' O-3001)/$(count_for O-3001)" "201/1"
check "dual write, publish first: the same O-3001 fails to commit" \
    expect "$([[ "$(post '/orders/dual-write?mode=publish-first' O-3001)" == 5* ]] && echo failed)" failed
check "dual write, publish first: two records announce one order that exists once" \
    expect "$(count_for O-3001)/$(orders_rows O-3001)" "2/1"

echo "Crash after the commit"
export CRASH_AFTER_COMMIT_FOR=O-2001,O-2002
"${COMPOSE[@]}" up -d --no-deps --force-recreate --wait order-service
check "order-service runs with the crash switch set for O-2001 and O-2002" \
    expect "$(docker exec edp-order-service printenv DEMO_CRASH_AFTER_COMMIT_FOR)" "$CRASH_AFTER_COMMIT_FOR"
poll 60 ready || fail_loudly "order-service is not ready with the crash switch set"
check "outbox: placing O-2001 never gets a response and the service is gone" \
    expect "$(post /orders O-2001)/$(exited_by_halt && echo halted)" "000/halted"
"${COMPOSE[@]}" start order-service >/dev/null
poll 60 ready || fail_loudly "order-service did not come back"
check "outbox: the committed order O-2001 exists and exactly one event for it arrives" \
    expect "$(orders_rows O-2001)/$(poll 30 has_count O-2001 1 && echo one)" "1/one"
check "comparison, commit first: placing O-2002 never gets a response and the service is gone" \
    expect "$(post '/orders/dual-write?mode=commit-first' O-2002)/$(exited_by_halt && echo halted)" "000/halted"
"${COMPOSE[@]}" start order-service >/dev/null
poll 60 ready || fail_loudly "order-service did not come back"
check "comparison, commit first: the order O-2002 exists and no event for it arrives, after a wait" \
    expect "$(orders_rows O-2002)/$(sleep 10; count_for O-2002)" "1/0"

echo "Connect down"
"${COMPOSE[@]}" stop connect >/dev/null
check "the slot is inactive while Connect is down" poll 30 slot_is f
ORDERS_DURING_OUTAGE=""
for i in 1 2 3 4 5; do
    ORDERS_DURING_OUTAGE+="$(post /orders "O-400$i") "
    sleep 0.2
done
check "five orders are accepted while Connect is down" expect "${ORDERS_DURING_OUTAGE% }" "201 201 201 201 201"
check "no event for them reaches the topic while Connect is down" expect "$(sleep 5; keys_in_topic_order)" ""
"${COMPOSE[@]}" start connect >/dev/null
check "Connect is healthy again and the connector is RUNNING" poll 90 connector_running
check "all five events are delivered, in the order the orders committed" \
    poll 60 expect "$(keys_in_topic_order)" "O-4001,O-4002,O-4003,O-4004,O-4005"

echo "Observability"
check "Tempo has a trace for order-service" poll 90 tempo_has_trace order-service
check "Loki has logs for order-service" poll 90 loki_has_log order-service

echo "Control: commit first without the crash"
check "comparison, commit first: with no crash O-2003 returns 201 and exactly one event arrives" \
    expect "$(post '/orders/dual-write?mode=commit-first' O-2003)/$(poll 30 has_count O-2003 1 && echo one)" "201/one"

echo "$PASSED/$TOTAL checks passed"
[[ "$PASSED" == "$TOTAL" ]]
