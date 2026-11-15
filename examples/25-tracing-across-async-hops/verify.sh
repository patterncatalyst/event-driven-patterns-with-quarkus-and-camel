#!/usr/bin/env bash
# Starts the stack, registers the outbox connector and follows one order through Tempo: the request, the outbox write,
# the two Debezium spans in Connect and the consumer in inventory-service, all in one trace. A burst of 100 orders shows
# the trace is complete every time, and a second phase restarts inventory-service with the span-link form. The negative
# witnesses are CONNECT_TRACING=off and CONSUMER_TRACE=link (see the README). KEEP=1 leaves the stack running afterwards.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-25

# Demo code: CONNECT_TRACING=off starts Connect without the agent; CONSUMER_TRACE=link runs the whole stack with the
# consumer in span-link form.
if [[ "${CONNECT_TRACING:-on}" == "off" ]]; then
    export CONNECT_ENABLE_OTEL=false CONNECT_KAFKA_OPTS=
fi
if [[ "${CONSUMER_TRACE:-parent}" == "link" ]]; then
    export CONSUMER_TRACE=link CONSUMER_TRACE_ENABLED=false
fi

COMPOSE=(docker compose -f compose.yaml)
ORDERS=http://localhost:8080
CONNECT=http://localhost:8083
CONNECTOR=order-outbox
CONNECTOR_JSON="${CONNECTOR_JSON:-connect/order-outbox.json}"
PSQL=(docker exec -e PGPASSWORD=edp edp-postgres psql -h 127.0.0.1 -U edp -d edp -tA)
TOPIC=edp.orders.placed
CHAIN="POST /orders > outbox-write > db-log-write > edp.orders.placed receive > reserve stock"
TOTAL=0
PASSED=0
TRACE=""

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
ready() { curl -sf "http://localhost:$1/q/health/ready" >/dev/null; } # ready <port>
# grep without -q: under pipefail an early-exiting grep would fail the pipeline with SIGPIPE.
log_has() { docker logs "edp-$1" 2>&1 | grep -F -- "$2" >/dev/null; } # log_has <container suffix> <text>
tempo() { python3 -I tools/tempo.py "$@"; }

# --- topics, groups and the connector
topics_init_done() { [[ "$(docker inspect -f '{{.State.Status}}/{{.State.ExitCode}}' edp-topics-init)" == "exited/0" ]]; }
one_partition() { [[ "$(kafka kafka-topics.sh --describe --topic "$TOPIC" 2>/dev/null | grep -c 'Partition: [0-9]' || true)" == 1 ]]; }
connector_running() {
    curl -sf "$CONNECT/connectors/$CONNECTOR/status" | python3 -I -c "
import json, sys
d = json.load(sys.stdin)
sys.exit(0 if d['connector']['state'] == 'RUNNING' and d['tasks'] and all(t['state'] == 'RUNNING' for t in d['tasks']) else 1)"
}
plugin_listed() { local out; out="$(curl -sf "$CONNECT/connector-plugins")" || return 1; [[ "$out" == *io.debezium.connector.postgresql.PostgresConnector* ]]; }
group_owns_partition() {
    kafka kafka-consumer-groups.sh --describe --all-groups 2>/dev/null \
        | awk '$1 == "inventory-placed" && $3 ~ /^[0-9]+$/ && $7 != "-" {f = 1} END {exit !f}'
}
topology_ready() { connector_running && one_partition; }
services_ready() { ready 8080 && ready 8082 && group_owns_partition; }
# The agent logs its banner once per start; without the agent (CONNECT_TRACING=off) the banner must be absent.
agent_state_matches() {
    if [[ "${CONNECT_TRACING:-on}" == "off" ]]; then ! log_has connect "opentelemetry-javaagent - version:"
    else log_has connect "opentelemetry-javaagent - version:"; fi
}
stock_is() { [[ "$(sql "SELECT quantity_on_hand FROM inventory.inventory_item WHERE sku = '$1'")" == "$2" ]]; } # stock_is <sku> <n>
stock_seeded() { stock_is SKU-25 10 && stock_is SKU-25-BULK 1000; }

# --- records on the topic: timestamp, headers, key and value, tab separated
records() { kafka kafka-console-consumer.sh --topic "$1" --from-beginning --timeout-ms 4000 \
    --formatter-property print.timestamp=true --formatter-property print.headers=true --formatter-property print.key=true 2>/dev/null; }
traceparent_of() { records "$TOPIC" | awk -F'\t' -v k="$1" '$3 == k' | sed -n 's/.*traceparent:\([0-9a-f-]*\).*/\1/p' | head -1; }
has_traceparent() { [[ -n "$(traceparent_of "$1")" ]]; }
# The header holds the trace id of the order's trace and the span id of db-log-write, the span Connect starts for the log entry.
header_matches_trace() { # header_matches_trace <order id>
    [[ -n "$TRACE" ]] || return 1   # without the trace of check 7 two empty values would agree
    local tp parts; tp="$(traceparent_of "$1")"
    IFS=- read -r -a parts <<<"$tp"
    [[ "${parts[1]:-}" == "$TRACE" && "${parts[2]:-}" == "$(tempo span-id "$TRACE" db-log-write)" ]]
}

# --- the orders
place() { # place <id> <sku> <quantity> <amount>
    curl -sf -o /dev/null -X POST -H 'Content-Type: application/json' "$ORDERS/orders" \
        --data "{\"id\":\"$1\",\"customerId\":\"CU-25\",\"itemSku\":\"$2\",\"quantity\":$3,\"amount\":$4}"
}
burst() { # burst: 100 orders of the bulk SKU; prints how many were accepted
    python3 -I - "$ORDERS" <<'PY'
import json, sys, urllib.request
from concurrent.futures import ThreadPoolExecutor
base = sys.argv[1]
def post(i):
    body = json.dumps({"id": "O-25%d" % i, "customerId": "CU-25", "itemSku": "SKU-25-BULK", "quantity": 1,
                       "amount": 25.00}).encode()
    req = urllib.request.Request(base + "/orders", body, {"Content-Type": "application/json"})
    with urllib.request.urlopen(req) as r:
        return r.status == 201
with ThreadPoolExecutor(8) as pool:
    print(sum(pool.map(post, range(100, 200))))
PY
}

# --- Tempo, Loki and Prometheus, through their HTTP APIs
one_trace() { # one_trace <order id>: sets TRACE when exactly one trace carries the order id
    local order="$1" ids; ids="$(tempo search "{ span.order.id = \"$order\" }")" || return 1
    ids="${ids//$'\n'/ }"
    # shellcheck disable=SC2086
    set -- $ids
    (( $# == 1 )) || return 1
    # The consumer's span must be searchable too: before it is, the order's trace alone would pass as the only trace.
    [[ -n "$(consumer_trace_of "$order")" ]] || return 1
    TRACE="$1"
}
trace_count_is() { # trace_count_is <order id> <n>
    local ids; ids="$(tempo search "{ span.order.id = \"$1\" }")" || return 1
    [[ "$(wc -w <<<"$ids")" == "$2" ]]
}
services_are() { [[ "$(tempo services "$1")" == "$2" ]]; } # services_are <trace id> "<service> <service> ..."
structural_match() { # the request span has an inventory-service descendant that carries the order id
    [[ -n "$TRACE" ]] || return 1   # an empty match would equal an empty trace id
    local ids; ids="$(tempo search "{ resource.service.name = \"order-service\" && name = \"POST /orders\" && span.order.id = \"$1\" } >> { resource.service.name = \"inventory-service\" && span.order.id = \"$1\" }")" || return 1
    [[ "$ids" == "$TRACE" ]]
}
ancestry_is() { [[ "$(tempo ancestry "$TRACE" inventory-service "reserve stock")" == "$CHAIN" ]]; }
debezium_read_beside_consumer() { [[ "$(tempo parent "$TRACE" debezium-read)" == db-log-write ]]; }
BURST_NOTE="$(mktemp)"
burst_complete() { tempo complete '{ span.order.id =~ "O-251[0-9][0-9]" }' 100 2>"$BURST_NOTE" >/dev/null; }
order_trace_of() { tempo search "{ resource.service.name = \"order-service\" && name = \"POST /orders\" && span.order.id = \"$1\" }"; }
consumer_trace_of() { tempo search "{ resource.service.name = \"inventory-service\" && name = \"reserve stock\" && span.order.id = \"$1\" }"; }
linked_not_parented() { # linked_not_parented <order id>: separate traces, and the consumer's root links to the db-log-write span
    local order consumer
    order="$(order_trace_of "$1")"; consumer="$(consumer_trace_of "$1")"
    [[ -n "$order" && -n "$consumer" && "$order" != "$consumer" ]] || return 1
    [[ "$(tempo services "$order")" == "connect order-service" ]] || return 1
    [[ "$(tempo links "$consumer")" == "process edp.orders.placed"$'\t'"$order"$'\t'"$(tempo span-id "$order" db-log-write)" ]]
}
prom_holds() { curl -sf -G "http://localhost:19090/api/v1/query" --data-urlencode "query=$1" | grep '"value"' >/dev/null; }
loki_has_line() { # loki_has_line <service> <text> <trace id>
    [[ -n "$3" ]] || return 1   # an empty trace id would match every line that has none
    local out; out="$(curl -sf -G "http://localhost:3100/loki/api/v1/query_range" \
        --data-urlencode "query={service_name=\"$1\"} |= \"$2\" | trace_id=\"$3\"")" || return 1
    [[ "$out" == *'"values"'* ]]
}

echo "Starting the stack"
# Fresh start: remove any containers and volumes left by an earlier run (KEEP=1, an aborted run),
# then create every container and anonymous volume anew.
"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
"${COMPOSE[@]}" up -d --build --force-recreate --renew-anon-volumes --wait
poll 60 topics_init_done || fail_loudly "topics-init did not finish"
poll 60 ready 8080 || fail_loudly "order-service is not ready"
poll 30 plugin_listed || fail_loudly "Connect does not list the PostgreSQL connector"
curl -sf -X PUT -H 'Content-Type: application/json' --data @"$CONNECTOR_JSON" "$CONNECT/connectors/$CONNECTOR/config" >/dev/null \
    || fail_loudly "the connector was not accepted by Connect"

echo "Infrastructure"
precondition "the connector $CONNECTOR is RUNNING and $TOPIC has one partition" poll 60 topology_ready
precondition "both services are ready and inventory-service's group owns the partition" poll 90 services_ready
precondition "Connect has the OpenTelemetry agent loaded (CONNECT_TRACING=off: it has none)" agent_state_matches
precondition "SKU-25 holds 10 and SKU-25-BULK holds 1000" stock_seeded

echo "One order through the outbox (O-2501)"
place O-2501 SKU-25 1 100.00 || fail_loudly "O-2501 was not accepted"
PLACED_AT=$SECONDS
check "inventory reserved O-2501: the stock of SKU-25 is 9" poll 20 stock_is SKU-25 9
check "the record for O-2501 on $TOPIC carries a traceparent header" poll 20 has_traceparent O-2501
check "Tempo holds exactly one trace for order id O-2501" poll 240 one_trace O-2501
echo "NOTE: Tempo made the trace of O-2501 searchable $((SECONDS - PLACED_AT)) s after the order was accepted"
check "that trace has spans of order-service, connect and inventory-service" poll 60 services_are "$TRACE" "connect inventory-service order-service"
check "a structural query finds the inventory-service span below the POST /orders span of O-2501" poll 60 structural_match O-2501
check "the reservation descends from the request: $CHAIN" poll 60 ancestry_is
check "debezium-read is a child of db-log-write, beside the consumer span" poll 60 debezium_read_beside_consumer
check "the record's traceparent holds the trace id of that trace and the span id of db-log-write" header_matches_trace O-2501
check "Prometheus holds the Tempo service-graph series of the requests to order-service" \
    poll 120 prom_holds 'traces_service_graph_request_total{server="order-service"} > 0'

echo "A burst of 100 orders"
precondition "all 100 orders of the burst are accepted" expect "$(burst)" 100
check "the stock of SKU-25-BULK is 900" poll 60 stock_is SKU-25-BULK 900
check "Tempo holds 100 traces for the burst and every one crosses order-service, connect and inventory-service" poll 240 burst_complete
grep NOTE "$BURST_NOTE" || true

echo "The span-link form (O-2502)"
# Demo code: inventory-service restarts with the consumer in link form. CONSUMER_TRACE_ENABLED=false turns off the
# channel's own consumer span; CONSUMER_TRACE=link starts the linked one.
CONSUMER_TRACE=link CONSUMER_TRACE_ENABLED=false "${COMPOSE[@]}" up -d --no-deps --force-recreate --wait inventory-service
precondition "inventory-service is ready again and its group owns the partition" poll 90 services_ready
place O-2502 SKU-25 1 100.00 || fail_loudly "O-2502 was not accepted"
check "inventory reserved O-2502: the stock of SKU-25 is 8" poll 20 stock_is SKU-25 8
check "Tempo holds exactly two traces for order id O-2502" poll 240 trace_count_is O-2502 2
check "the order's trace stops at Connect, and the consumer's root span links to its db-log-write span" poll 120 linked_not_parented O-2502

echo "Logs"
check "Loki has an inventory-service line for O-2501 that carries the trace id of its trace" \
    poll 90 loki_has_line inventory-service O-2501 "$TRACE"

check "Prometheus holds an HTTP server metric of order-service with its service_name label" \
    poll 90 prom_holds 'count({service_name="order-service", __name__=~"http_server_.*"}) > 0'

if [[ "$PASSED" != "$TOTAL" ]]; then
    for s in connect order-service inventory-service; do
        echo "$s log, last lines:" >&2
        docker logs "edp-$s" 2>&1 | tail -25 | cut -c1-260 >&2 || true
    done
fi
rm -f "$BURST_NOTE"
echo "$PASSED/$TOTAL checks passed"
[[ "$PASSED" == "$TOTAL" ]]
