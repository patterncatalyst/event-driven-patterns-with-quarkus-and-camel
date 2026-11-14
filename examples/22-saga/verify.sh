#!/usr/bin/env bash
# Starts the stack, registers the one outbox connector and checks the observable effects of an order saga choreographed
# over Kafka: the happy path, a refusal for stock, a declined payment that is compensated once, a payment-service that
# does not answer (the sweeper cancels, the stock is released, the late reservation takes no money), a saga whose state
# survives a restart of order-service, and a burst of 200 orders. PAYMENT_CANCEL_GUARD=off and SAGA_PAYMENT_TIMEOUT=60s
# are the negative witnesses (see the README). KEEP=1 leaves the stack running afterwards.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-22

COMPOSE=(docker compose -f compose.yaml)
ORDERS=http://localhost:8080
CONNECT=http://localhost:8083
CONNECTOR=saga-outbox
CONNECTOR_JSON="${CONNECTOR_JSON:-connect/saga-outbox.json}"
PSQL=(docker exec -e PGPASSWORD=edp edp-postgres psql -h 127.0.0.1 -U edp -d edp -tA)
TOPICS=(edp.orders.placed edp.orders.cancelled edp.inventory.reserved edp.inventory.insufficient edp.payments.processed edp.payments.failed)
GROUPS_WANTED=(order-saga-reserved order-saga-insufficient order-saga-processed order-saga-failed
    inventory-placed inventory-failed inventory-cancelled payment-reserved payment-cancelled)
TIMEOUT_SLACK_MS=3500
RESTART_DEADLINE_S="${RESTART_DEADLINE_S:-40}"
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
now_ms() { date +%s%3N; }
uuid() { cat /proc/sys/kernel/random/uuid; }
ready() { curl -sf "http://localhost:$1/q/health/ready" >/dev/null; } # ready <port>
# grep without -q: under pipefail an early-exiting grep would fail the pipeline with SIGPIPE.
log_has() { docker logs "edp-$1" 2>&1 | grep -F -- "$2" >/dev/null; } # log_has <service> <text>

# --- topics, groups and the connector
topics_init_done() { [[ "$(docker inspect -f '{{.State.Status}}/{{.State.ExitCode}}' edp-topics-init)" == "exited/0" ]]; }
partitions_of() { kafka kafka-topics.sh --describe --topic "$1" 2>/dev/null | grep -c 'Partition: [0-9]' || true; }
topics_have_one_partition() { [[ "$(for t in "${TOPICS[@]}"; do partitions_of "$t"; done | tr '\n' ' ')" == "1 1 1 1 1 1 " ]]; }
connector_running() {
    curl -sf "$CONNECT/connectors/$CONNECTOR/status" | python3 -I -c "
import json, sys
d = json.load(sys.stdin)
sys.exit(0 if d['connector']['state'] == 'RUNNING' and d['tasks'] and all(t['state'] == 'RUNNING' for t in d['tasks']) else 1)"
}
plugin_listed() { local out; out="$(curl -sf "$CONNECT/connector-plugins")" || return 1; [[ "$out" == *io.debezium.connector.postgresql.PostgresConnector* ]]; }
describe_all() { kafka kafka-consumer-groups.sh --describe --all-groups 2>/dev/null; }
group_lag() { # group_lag <group>: log end minus committed (0 when nothing is committed), summed over partitions
    describe_all | awk -v g="$1" '$1 == g && $3 ~ /^[0-9]+$/ {c = ($4 == "-") ? 0 : $4; l += $5 - c; seen = 1} END {if (seen) print l}'
}
lag_is() { [[ "$(group_lag "$1")" == "$2" ]]; } # lag_is <group> <n>
groups_assigned() { # every wanted group has a member holding its partition
    describe_all | awk -v gs="${GROUPS_WANTED[*]}" '
        BEGIN {n = split(gs, a, " "); for (i = 1; i <= n; i++) want[a[i]] = 1}
        $1 in want && $3 ~ /^[0-9]+$/ && $7 != "-" {own[$1] = 1}
        END {k = 0; for (g in own) k++; exit !(k == n)}'
}
no_member() { # no_member <group>: the group has no member holding a partition
    ! describe_all | awk -v g="$1" '$1 == g && $3 ~ /^[0-9]+$/ && $7 != "-" {f = 1} END {exit !f}'
}

# --- records on the topics: timestamp, headers, key and value, tab separated
records() { kafka kafka-console-consumer.sh --topic "$1" --from-beginning --timeout-ms 4000 \
    --formatter-property print.timestamp=true --formatter-property print.headers=true --formatter-property print.key=true 2>/dev/null; }
record_count() { records "$1" | awk -F'\t' -v k="$2" '$3 == k' | wc -l; }                        # record_count <topic> <key>
record_ts() { records "$1" | awk -F'\t' -v k="$2" '$3 == k {sub("CreateTime:", "", $1); print $1; exit}'; }
event_id() { records "$1" | awk -F'\t' -v k="$2" '$3 == k {match($2, /id:[0-9a-f-]+/); print substr($2, RSTART + 3, RLENGTH - 3); exit}'; }
produce() { # produce <topic>: lines "id:<id>,eventType:<Type><TAB><key>|<json>" on stdin
    docker exec -i edp-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic "$1" \
        --reader-property parse.key=true --reader-property 'key.separator=|' --reader-property parse.headers=true >/dev/null 2>&1
}

# --- the orders
place() { # place <id> <sku> <quantity> <amount>
    curl -sf -o /dev/null -X POST -H 'Content-Type: application/json' "$ORDERS/orders" \
        --data "{\"id\":\"$1\",\"customerId\":\"CU-22\",\"itemSku\":\"$2\",\"quantity\":$3,\"amount\":$4}"
}
view_tuple() { # view_tuple <id> <field/field/...>: fields of GET /orders/<id> joined by "/"
    local j; j="$(curl -sf "$ORDERS/orders/$1")" || return 1
    python3 -I -c 'import json, sys; d = json.loads(sys.argv[1]); print("/".join("" if d.get(f) is None else str(d[f]) for f in sys.argv[2].split("/")))' "$j" "$2"
}
view_is() { [[ "$(view_tuple "$1" "$2")" == "$3" ]]; }     # view_is <id> <field/field/...> <value/value/...>
saga_is() { view_is "$1" saga "$2"; }                     # saga_is <id> <state>
deadline_ms() { date -d "$(view_tuple "$1" deadline)" +%s%3N; }
stock() { sql "SELECT quantity_on_hand FROM inventory.inventory_item WHERE sku = '$1'"; }
stock_is() { [[ "$(stock "$1")" == "$2" ]]; }              # stock_is <sku> <n>
reservation_is() { [[ "$(sql "SELECT state FROM inventory.reservation WHERE order_id = '$1'")" == "$2" ]]; }
payment_status() { local s; s="$(sql "SELECT status FROM payments.payment WHERE order_id = '$1'")"; echo "${s:-none}"; }
payment_is() { [[ "$(payment_status "$1")" == "$2" ]]; }   # payment_is <id> <status|none>
count_sagas() { sql "SELECT count(*) FROM orders.order_saga WHERE order_id BETWEEN 'O-22100' AND 'O-22299' AND state = '$1'"; }
bench_terminal() { [[ "$(count_sagas COMPLETED)" == 100 && "$(count_sagas CANCELLED)" == 100 ]]; }
bench_stock_and_reservations() {
    stock_is SKU-22-BULK 900 && [[ "$(sql "SELECT state || ':' || count(*) FROM inventory.reservation WHERE order_id BETWEEN 'O-22100' AND 'O-22299' GROUP BY state ORDER BY state" | tr '\n' ' ')" == "RELEASED:100 RESERVED:100 " ]]
}
burst() { # burst: 200 orders of the bulk SKU, every second one above the decline limit; prints how many were accepted
    python3 -I - "$ORDERS" <<'PY'
import json, sys, urllib.request
from concurrent.futures import ThreadPoolExecutor
base = sys.argv[1]
def post(i):
    body = json.dumps({"id": "O-22%d" % i, "customerId": "CU-22", "itemSku": "SKU-22-BULK", "quantity": 1,
                       "amount": 900.00 if i % 2 else 25.00}).encode()
    req = urllib.request.Request(base + "/orders", body, {"Content-Type": "application/json"})
    with urllib.request.urlopen(req) as r:
        return r.status == 201
with ThreadPoolExecutor(8) as pool:
    print(sum(pool.map(post, range(100, 300))))
PY
}

# --- the sequence of events, the timeout and the guard
causal_order() { # causal_order <id>: OrderPlaced, InventoryReserved, PaymentProcessed in timestamp order
    local a b c
    a="$(record_ts edp.orders.placed "$1")"; b="$(record_ts edp.inventory.reserved "$1")"; c="$(record_ts edp.payments.processed "$1")"
    [[ -n "$a" && -n "$b" && -n "$c" ]] && (( a <= b && b <= c ))
}
cancelled_on_time() { # cancelled_on_time <id> <deadline ms>: CANCELLED by the sweeper, not before the deadline and soon after
    poll 45 view_is "$1" status/saga/reason CANCELLED/CANCELLED/TIMED_OUT || return 1
    DELTA_MS=$(( $(now_ms) - $2 ))
    (( DELTA_MS >= 0 && DELTA_MS <= TIMEOUT_SLACK_MS ))
}
survives_restart() { # survives_restart <id> <deadline>: still waiting after the recreate, before the deadline, then cancelled at that deadline
    local dl_ms; dl_ms="$(date -d "$2" +%s%3N)"
    saga_is "$1" AWAITING_PAYMENT || return 1
    REMAINING_MS=$(( dl_ms - $(now_ms) ))
    (( REMAINING_MS > 0 )) || return 1
    cancelled_on_time "$1" "$dl_ms" && view_is "$1" deadline "$2"
}
# The release is idempotent, so the stock and the reservation are the same with or without the inbox: only the log line
# shows that the inbox saw the repeated id. The stock is checked as the harmless outcome, not as the witness.
duplicate_skipped() { # duplicate_skipped <event id>
    log_has inventory-service "duplicate event $1 skipped" && stock_is SKU-22 9 && reservation_is O-2203 RELEASED
}
refused_and_no_money() { log_has payment-service "refused the payment for $1: the order was cancelled" && payment_is "$1" none; }
no_payment_event() { [[ "$(record_count edp.payments.processed "$1")" == 0 && "$(record_count edp.payments.failed "$1")" == 0 ]]; }

# --- conditions that combine several reads
topology_ready() { connector_running && topics_have_one_partition; }
stock_seeded() { stock_is SKU-22 10 && stock_is SKU-22-BULK 1000; }
happy_effects() { stock_is SKU-22 9 && payment_is O-2201 COMPLETED; }
only_stock_unchanged() { payment_is O-2202 none && stock_is SKU-22 9; }
declined_compensated() { reservation_is O-2203 RELEASED && stock_is SKU-22 9; }
payment_down() { ! curl -sf http://localhost:8084/q/health/ready >/dev/null && no_member payment-reserved && no_member payment-cancelled; }
reserved_and_waiting() { saga_is O-2204 AWAITING_PAYMENT && stock_is SKU-22 8; }
cancellation_published_and_released() { [[ "$(record_count edp.orders.cancelled O-2204)" == 1 ]] && reservation_is O-2204 RELEASED && stock_is SKU-22 9; }
payment_groups_caught_up() { lag_is payment-reserved 0 && lag_is payment-cancelled 0; }

# --- observability
prom_holds() { curl -sf -G "http://localhost:19090/api/v1/query" --data-urlencode "query=$1" | grep '"value"' >/dev/null; }
tempo_has_trace() { local out; out="$(curl -sf -G "http://localhost:3200/api/search" --data-urlencode "tags=service.name=$1")" || return 1; [[ "$out" == *'"traceID"'* ]]; }
loki_has_line() { local out; out="$(curl -sf -G "http://localhost:3100/loki/api/v1/query_range" --data-urlencode "query={service_name=\"$1\"} |= \"$2\"")" || return 1; [[ "$out" == *'"values"'* ]]; }

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
precondition "the connector $CONNECTOR is RUNNING over the three outbox tables and the six topics have one partition each" poll 60 topology_ready
precondition "the three services are ready and their nine consumer groups each own a partition" poll 90 groups_assigned
precondition "SKU-22 holds 10 and SKU-22-BULK holds 1000" stock_seeded
precondition "the saga table is empty" expect "$(sql "SELECT count(*) FROM orders.order_saga")" 0

echo "The happy path (O-2201)"
place O-2201 SKU-22 1 100.00 || fail_loudly "O-2201 was not accepted"
check "O-2201 is PAID within 15 s and its saga is COMPLETED" poll 15 view_is O-2201 status/saga PAID/COMPLETED
check "stock of SKU-22 is 9 and the payment is COMPLETED" poll 10 happy_effects
check "the events of O-2201 are on the topics in causal order: OrderPlaced, InventoryReserved, PaymentProcessed" \
    poll 30 causal_order O-2201

echo "A burst of 200 orders, half of them declined"
BURST_START="$(now_ms)"
precondition "all 200 orders of the burst are accepted" expect "$(burst)" 200
check "100 sagas are COMPLETED and 100 are CANCELLED within 120 s" poll 120 bench_terminal
BURST_MS=$(( $(now_ms) - BURST_START ))
echo "NOTE: 200 orders reached a final state after $BURST_MS ms ($(( 200000 / (BURST_MS > 0 ? BURST_MS : 1) )) orders per second)"
check "the bulk stock is 900 and 100 reservations are RESERVED and 100 RELEASED" poll 30 bench_stock_and_reservations

echo "Out of stock (O-2202)"
place O-2202 SKU-22 50 1000.00 || fail_loudly "O-2202 was not accepted"
check "O-2202 is CANCELLED with reason OUT_OF_STOCK" poll 15 view_is O-2202 status/saga/reason CANCELLED/CANCELLED/OUT_OF_STOCK
check "O-2202 has no payment and the stock of SKU-22 is still 9" only_stock_unchanged

echo "A declined payment (O-2203)"
place O-2203 SKU-22 1 900.00 || fail_loudly "O-2203 was not accepted"
check "O-2203 is CANCELLED with reason PAYMENT_DECLINED" poll 15 view_is O-2203 status/saga/reason CANCELLED/CANCELLED/PAYMENT_DECLINED
check "the reservation of O-2203 is RELEASED and the stock of SKU-22 is back at 9" poll 15 declined_compensated
FAILED_ID="$(event_id edp.payments.failed O-2203)"
[[ -n "$FAILED_ID" ]] || fail_loudly "no id header on the PaymentFailed record of O-2203"
printf 'id:%s,eventType:PaymentFailed\tO-2203|{"orderId":"O-2203","reason":"DECLINED"}\n' "$FAILED_ID" | produce edp.payments.failed
check "inventory logs that its inbox skipped the repeated PaymentFailed id (a log witness: the release is idempotent), and the stock stays at 9" poll 30 duplicate_skipped "$FAILED_ID"

echo "A payment-service that does not answer (O-2204)"
"${COMPOSE[@]}" stop payment-service >/dev/null
precondition "payment-service is down and both of its groups have no member" poll 60 payment_down
place O-2204 SKU-22 1 100.00 || fail_loudly "O-2204 was not accepted"
precondition "O-2204 is reserved: its saga waits for the payment and the stock of SKU-22 is 8" poll 20 reserved_and_waiting
DEADLINE_2204="$(deadline_ms O-2204)"
check "the sweeper cancels O-2204 with reason TIMED_OUT, within ${TIMEOUT_SLACK_MS} ms after its deadline" cancelled_on_time O-2204 "$DEADLINE_2204"
echo "NOTE: O-2204 was observed cancelled ${DELTA_MS:-unknown} ms after its deadline"
check "OrderCancelled is on the topic and the stock of SKU-22 is back at 9" poll 30 cancellation_published_and_released
"${COMPOSE[@]}" up -d --no-deps --wait payment-service
poll 60 ready 8084 || fail_loudly "payment-service is not ready after the restart"
check "payment-service catches up: both of its groups have lag 0" poll 60 payment_groups_caught_up
check "payment-service refused the late reservation of O-2204: its log says so and there is no payment row" poll 30 refused_and_no_money O-2204
check "no PaymentProcessed and no PaymentFailed exists for O-2204" no_payment_event O-2204
check "O-2204 is still CANCELLED" view_is O-2204 status/saga CANCELLED/CANCELLED

echo "The saga state survives a restart of order-service (O-2205)"
"${COMPOSE[@]}" stop payment-service >/dev/null
precondition "payment-service is down again and its groups have no member" poll 60 payment_down
place O-2205 SKU-22 1 100.00 || fail_loudly "O-2205 was not accepted"
precondition "O-2205 waits for the payment, with a deadline" poll 20 saga_is O-2205 AWAITING_PAYMENT
# The deadline is moved well beyond the time a recreate takes, so the saga is still waiting when order-service is back.
sql "UPDATE orders.order_saga SET deadline = now() + interval '${RESTART_DEADLINE_S} seconds' WHERE order_id = 'O-2205'" >/dev/null \
    || fail_loudly "the deadline of O-2205 was not moved"
DEADLINE_2205="$(view_tuple O-2205 deadline)"
"${COMPOSE[@]}" up -d --no-deps --force-recreate --wait order-service
poll 60 ready 8080 || fail_loudly "order-service is not ready after the recreate"
check "after the recreate O-2205 is still AWAITING_PAYMENT before its deadline, and the sweeper cancels it with reason TIMED_OUT at the deadline PostgreSQL held before" \
    survives_restart O-2205 "$DEADLINE_2205"
echo "NOTE: O-2205 was still waiting ${REMAINING_MS:-unknown} ms before its deadline after the recreate, and was observed cancelled ${DELTA_MS:-unknown} ms after it"
"${COMPOSE[@]}" up -d --no-deps --wait payment-service
poll 60 ready 8084 || fail_loudly "payment-service is not ready after the second restart"
check "payment-service refused the reservation of O-2205 and took no money" poll 60 refused_and_no_money O-2205

echo "Observability"
check "Prometheus holds saga_outcomes_total for outcome compensated from order-service, at least 101" \
    poll 90 prom_holds 'max_over_time(saga_outcomes_total{outcome="compensated",service_name="order-service"}[1h]) >= 101'
check "Tempo has a trace for order-service" poll 90 tempo_has_trace order-service
check "Loki has a log line of inventory-service that names O-2203" poll 90 loki_has_line inventory-service O-2203

if [[ "$PASSED" != "$TOTAL" ]]; then
    for s in order-service inventory-service payment-service; do
        echo "$s log, last lines:" >&2
        docker logs "edp-$s" 2>&1 | tail -25 | cut -c1-260 >&2 || true
    done
fi
echo "$PASSED/$TOTAL checks passed"
[[ "$PASSED" == "$TOTAL" ]]
