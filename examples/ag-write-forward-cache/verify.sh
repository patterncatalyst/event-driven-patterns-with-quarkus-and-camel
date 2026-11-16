#!/usr/bin/env bash
# Starts the stack, registers the outbox connector and checks the observable effects of a write-forward cache:
# the order and its event commit together, Redis is written only from the events, a cache loss is rebuilt by replay,
# and an older event never replaces a newer one. Two demo switches show what the pattern prevents:
# CACHE_WRITE=dual (the request writes Redis before the commit) and CACHE_GUARD=off (no version guard).
# CACHE_GUARD=off is applied only to the two restarts of the older-events-after-newer-ones scenario (check 18), so it is
# the one check that fails; every other check runs with the guard on and cannot depend on the order topics are fetched in.
# KEEP=1 leaves the stack running afterwards.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
GUARD_SWITCH="${CACHE_GUARD:-on}" # demo code: the value the caller asked for, applied to one scenario only
export CACHE_GUARD=on
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-ag

COMPOSE=(docker compose -f compose.yaml)
ORDERS=http://localhost:8080
CONNECT=http://localhost:8083
CONNECTOR=order-outbox
CONNECTOR_JSON=connect/order-outbox.json
PLACED=edp.orders.placed
CANCELLED=edp.orders.cancelled
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
redis() { docker exec edp-redis redis-cli "$@"; }
ready() { curl -sf "$ORDERS/q/health/ready" >/dev/null; }
order_json() { printf '{"id":"%s","customerId":"CU-77","itemSku":"SKU-42","quantity":3,"amount":59.90}' "$1"; }
post() { # post <path> [orderId]: the HTTP status of a POST, or 000 when nothing answers
    curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' ${2:+-d "$(order_json "$2")"} "$ORDERS$1" || true
}
place() { post /orders "$1"; }
cancel() { post "/orders/$1/cancel"; }
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
redis_ping() { [[ "$(redis ping)" == PONG ]]; }
http_code() { curl -s -o /dev/null -w '%{http_code}' "$ORDERS$1" || true; }
http_is() { [[ "$(http_code "$1")" == "$2" ]]; } # http_is <path> <status>
# "STATUS VERSION" of an order, read from the cache (/orders/{id}) or from PostgreSQL (/orders/{id}/db).
state_at() { curl -sf "$ORDERS$1" | python3 -I -c 'import json, sys; d = json.load(sys.stdin); print(d["status"], d["version"])'; }
cache_state() { state_at "/orders/$1"; }
db_state() { state_at "/orders/$1/db"; }
cache_is() { [[ "$(cache_state "$1" 2>/dev/null)" == "$2" ]]; } # cache_is <orderId> "<STATUS> <version>"
cache_matches_db() { # every order in PostgreSQL is in the cache with the same status and version
    local id
    while read -r id; do
        [[ -n "$id" ]] || continue
        [[ "$(cache_state "$id" 2>/dev/null)" == "$(db_state "$id")" ]] || return 1
    done < <(sql "SELECT id FROM orders.orders ORDER BY id")
}
orders_rows() { sql "SELECT count(*) FROM orders.orders WHERE id='$1'"; }
order_count() { sql "SELECT count(*) FROM orders.orders"; }
cache_size_is_order_count() { [[ "$(redis dbsize)" == "$(order_count)" ]]; }

# Every record of a topic as "headers<TAB>key<TAB>value", read from the beginning.
dump() { # dump <topic>
    docker exec edp-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic "$1" \
        --from-beginning --timeout-ms 5000 --formatter-property print.headers=true --formatter-property print.key=true 2>/dev/null | grep -F $'\t' || true
}
records_for() { dump "$1" | awk -F'\t' -v k="$2" '$(NF-1) == k'; } # records_for <topic> <orderId>
count_for() { records_for "$1" "$2" | wc -l; }
has_count() { [[ "$(count_for "$1" "$2")" == "$3" ]]; } # has_count <topic> <orderId> <n>
# The one record of an order on a topic has the event type as a header and the full state as its JSON value.
record_is() { # record_is <topic> <orderId> <eventType> <status> <version>
    local line headers value
    line="$(records_for "$1" "$2" | head -1)"
    [[ -n "$line" ]] || return 1
    headers="${line%%$'\t'*}"; value="${line##*$'\t'}"
    [[ "$headers" == *"eventType:$3"* ]] || return 1
    python3 -I -c "
import json, sys
d = json.loads(sys.argv[1])
assert d['orderId'] == sys.argv[2] and d['customerId'] == 'CU-77' and d['itemSku'] == 'SKU-42' and d['quantity'] == 3
assert d['status'] == sys.argv[3] and d['version'] == int(sys.argv[4])" "$value" "$2" "$4" "$5"
}
# The lag of a consumer group on one topic, as a number, or nothing while the group has no committed offset there.
group_lag() { # group_lag <group> <topic>
    docker exec edp-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group "$1" 2>/dev/null \
        | awk -v t="$2" '$2 == t && $6 ~ /^[0-9]+$/ {print $6}'
}
lag_is_zero() { [[ "$(group_lag "$1" "$2")" == 0 ]]; }
service_up() { # service_up: recreate order-service with whatever CACHE_* variables the caller set for this command
    "${COMPOSE[@]}" up -d --no-deps --force-recreate --wait order-service >/dev/null 2>&1 && poll 60 ready
}

tempo_has_trace() { local out; out="$(curl -sf -G "http://localhost:3200/api/search" --data-urlencode "tags=service.name=$1")" || return 1; [[ "$out" == *'"traceID"'* ]]; }
loki_has_log() { local out; out="$(curl -sf -G "http://localhost:3100/loki/api/v1/query_range" --data-urlencode "query={service_name=\"$1\"}")" || return 1; [[ "$out" == *'"values"'* ]]; }

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
check "Redis answers PONG and holds no key at the start" expect "$(redis ping)/$(redis dbsize)" "PONG/0"

echo "The write-forward path"
check "placing O-4201 returns 201" expect "$(place O-4201)" 201
check "the order row has version 1 and the outbox table is empty again" \
    expect "$(sql "SELECT version FROM orders.orders WHERE id='O-4201'")/$(sql "SELECT count(*) FROM orders.outboxevent")" "1/0"
check "one OrderPlaced record for O-4201 carries the full state at version 1" \
    poll 30 record_is "$PLACED" O-4201 OrderPlaced PLACED 1
check "the cache holds O-4201 as PLACED version 1" poll 30 cache_is O-4201 "PLACED 1"
check "cancelling O-4201 returns 200 and its OrderCancelled record carries the full state at version 2" \
    expect "$(cancel O-4201)/$(poll 30 record_is "$CANCELLED" O-4201 OrderCancelled CANCELLED 2 && echo event)" "200/event"
check "the cache holds O-4201 as CANCELLED version 2" poll 30 cache_is O-4201 "CANCELLED 2"

echo "The cache is fed only from events"
"${COMPOSE[@]}" stop connect >/dev/null
poll 30 slot_is f || fail_loudly "the replication slot is still active"
check "with Connect stopped, O-4202 is committed and readable from PostgreSQL, and the cache has no answer for it" \
    expect "$(place O-4202)/$(http_code /orders/O-4202/db)/$(sleep 5; http_code /orders/O-4202)" "201/200/404"
"${COMPOSE[@]}" start connect >/dev/null
poll 90 connector_running || fail_loudly "the connector did not come back"
check "when Connect returns, the cache holds O-4202 as PLACED version 1" poll 60 cache_is O-4202 "PLACED 1"

echo "Staleness"
STALE_RC=0
STALE_OUT="$(python3 -I tools/staleness.py "$ORDERS" 4210 20 10)" || STALE_RC=$?
echo "NOTE staleness over 20 orders: $STALE_OUT"
check "20 orders, O-4210 to O-4229, are each readable from the cache within 10 s of the commit" expect "$STALE_RC" 0
check "the first read after the commit missed the cache for at least one order" \
    bash -c '[[ "$1" =~ first\ read:\ ([0-9]+) ]] && (( BASH_REMATCH[1] > 0 ))' _ "$STALE_OUT"

echo "A failed request"
FAILED_CODE="$(place O-4290)"
check "a request that fails before the commit returns 500" expect "$FAILED_CODE" 500
check "the failed order has no row and no event, after a wait" expect "$(orders_rows O-4290)/$(sleep 4; count_for "$PLACED" O-4290)" "0/0"
check "the cache has no entry for the failed order" expect "$(http_code /orders/O-4290)" 404

echo "Replay"
for i in 4230 4231 4232 4233; do place "O-$i" >/dev/null; done
for i in 4230 4231 4232; do cancel "O-$i" >/dev/null; done
poll 60 cache_is O-4232 "CANCELLED 2" || fail_loudly "O-4232 did not reach the cache"
poll 60 cache_is O-4233 "PLACED 1" || fail_loudly "O-4233 did not reach the cache"
CACHE_GROUP=order-cache-replay service_up || fail_loudly "order-service did not restart for the replay"
poll 90 lag_is_zero order-cache-replay "$PLACED" || fail_loudly "the replay did not finish reading $PLACED"
poll 90 lag_is_zero order-cache-replay "$CANCELLED" || fail_loudly "the replay did not finish reading $CANCELLED"
check "after a fresh group has replayed both topics, O-4230 to O-4232 are still CANCELLED version 2 and O-4233 is PLACED version 1" \
    expect "$(cache_state O-4230)/$(cache_state O-4231)/$(cache_state O-4232)/$(cache_state O-4233)" "CANCELLED 2/CANCELLED 2/CANCELLED 2/PLACED 1"

echo "Older events replayed after newer ones"
redis flushall >/dev/null
CACHE_GUARD="$GUARD_SWITCH" CACHE_GROUP=order-cache-phases CACHE_TOPICS="$CANCELLED" service_up || fail_loudly "order-service did not restart on the cancelled topic"
poll 60 cache_is O-4232 "CANCELLED 2" || fail_loudly "the cancelled events did not reach the cache"
CACHE_GUARD="$GUARD_SWITCH" CACHE_GROUP=order-cache-phases CACHE_TOPICS="$PLACED" service_up || fail_loudly "order-service did not restart on the placed topic"
poll 90 lag_is_zero order-cache-phases "$PLACED" || fail_loudly "the placed events were not all read"
check "after the placed events are applied on top of the cancelled ones, O-4230 to O-4232 are still CANCELLED version 2" \
    expect "$(cache_state O-4230)/$(cache_state O-4231)/$(cache_state O-4232)" "CANCELLED 2/CANCELLED 2/CANCELLED 2"

echo "Losing the cache and rebuilding it"
"${COMPOSE[@]}" stop redis >/dev/null
check "with Redis stopped, O-4205 is accepted and its event reaches the topic" \
    expect "$(place O-4205)/$(poll 30 has_count "$PLACED" O-4205 1 && echo event)" "201/event"
"${COMPOSE[@]}" start redis >/dev/null
poll 30 redis_ping || fail_loudly "Redis did not come back"
check "after Redis returns, it holds no key and the cache has no answer for O-4201" \
    expect "$(redis dbsize)/$(poll 30 http_is /orders/O-4201 404 && echo 404)" "0/404"
CACHE_GROUP=order-cache-rebuild service_up || fail_loudly "order-service did not restart for the rebuild"
REBUILD_START=$SECONDS
check "a fresh consumer group rebuilds the cache: every order in PostgreSQL has the same status and version in Redis" \
    poll 90 cache_matches_db
echo "NOTE rebuild of $(order_count) orders, from order-service answering requests to a matching cache: $((SECONDS - REBUILD_START)) s"
check "the rebuilt cache holds exactly as many keys as there are orders" poll 30 cache_size_is_order_count

echo "Observability"
check "Tempo has a trace for order-service" poll 90 tempo_has_trace order-service
check "Loki has logs for order-service" poll 90 loki_has_log order-service

echo "$PASSED/$TOTAL checks passed"
[[ "$PASSED" == "$TOTAL" ]]
