#!/usr/bin/env bash
# Starts the stack, registers the one outbox connector and follows orders through Tempo and Loki: one order is one trace
# across three services and the Connect relay, every log line of it carries that trace id and the order id, a log line
# leads to its trace and the trace to its log lines, and an order that the sweeper cancels is two traces, the second
# linked to the first. SWEEPER_LINK=off, ORDER_ID_IN_LOGS=off and CONNECT_ENABLE_OTEL=false CONNECT_KAFKA_OPTS= (Connect
# without the agent) are the negative witnesses (see the README).
# KEEP=1 leaves the stack running afterwards.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-27

COMPOSE=(docker compose -f compose.yaml)
ORDERS=http://localhost:8080
CONNECT=http://localhost:8083
CONNECTOR=correlation-outbox
CONNECTOR_JSON="${CONNECTOR_JSON:-connect/correlation-outbox.json}"
PSQL=(docker exec -e PGPASSWORD=edp edp-postgres psql -h 127.0.0.1 -U edp -d edp -tA)
TOPICS=(edp.orders.placed edp.orders.cancelled edp.inventory.reserved edp.inventory.insufficient edp.payments.processed edp.payments.failed)
GROUPS_WANTED=(order-saga-reserved order-saga-insufficient order-saga-processed order-saga-failed
    inventory-placed inventory-failed inventory-cancelled payment-reserved payment-cancelled)
TIMEOUT_SLACK_MS=3500
OBSERVE=(python3 -I tools/observe.py)
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

# --- the orders
place() { # place <id> <sku> <quantity> <amount>
    curl -sf -o /dev/null -X POST -H 'Content-Type: application/json' "$ORDERS/orders" \
        --data "{\"id\":\"$1\",\"customerId\":\"CU-27\",\"itemSku\":\"$2\",\"quantity\":$3,\"amount\":$4}"
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

# --- conditions on the sequence of events and the timeout
cancelled_on_time() { # cancelled_on_time <id> <deadline ms>: CANCELLED by the sweeper, not before the deadline and soon after
    poll 45 view_is "$1" status/saga/reason CANCELLED/CANCELLED/TIMED_OUT || return 1
    DELTA_MS=$(( $(now_ms) - $2 ))
    (( DELTA_MS >= 0 && DELTA_MS <= TIMEOUT_SLACK_MS ))
}
refused_and_no_money() { log_has payment-service "refused the payment for $1: the order was cancelled" && payment_is "$1" none; }
no_payment_event() { [[ "$(record_count edp.payments.processed "$1")" == 0 && "$(record_count edp.payments.failed "$1")" == 0 ]]; }
topology_ready() { connector_running && topics_have_one_partition; }
stock_seeded() { stock_is SKU-27 10; }
happy_effects() { stock_is SKU-27 9 && payment_is O-2701 COMPLETED; }
declined_compensated() { reservation_is O-2703 RELEASED && stock_is SKU-27 9; }
payment_down() { ! curl -sf http://localhost:8084/q/health/ready >/dev/null && no_member payment-reserved && no_member payment-cancelled; }
reserved_and_waiting() { saga_is O-2704 AWAITING_PAYMENT && stock_is SKU-27 8; }
cancellation_published_and_released() { [[ "$(record_count edp.orders.cancelled O-2704)" == 1 ]] && reservation_is O-2704 RELEASED && stock_is SKU-27 9; }
payment_groups_caught_up() { lag_is payment-reserved 0 && lag_is payment-cancelled 0; }
# The agent logs its banner once per start. With CONNECT_ENABLE_OTEL=false (the negative witness) the banner must be absent.
agent_state_matches() {
    if [[ "${CONNECT_ENABLE_OTEL:-true}" == "false" ]]; then ! log_has connect "opentelemetry-javaagent - version"
    else log_has connect "opentelemetry-javaagent - version"; fi
}

# --- Tempo and Loki through their HTTP APIs. A trace is searchable 20 to 40 s after the request, so these poll.
trace_count_is() { [[ "$("${OBSERVE[@]}" trace-ids "$1" | wc -l)" == "$2" ]]; }                 # trace_count_is <order> <n>
the_trace() { # the_trace <order>: the id of the order's trace, only when there is exactly one
    local ids; ids="$("${OBSERVE[@]}" trace-ids "$1")"; [[ -n "$ids" && "$ids" != *$'\n'* ]] && echo "$ids"
}
# The trace search lists a trace as soon as one of its spans is indexed, so a count taken then can be too small.
# trace_count_settled counts only after the spans that a correct run holds for the order are searchable somewhere in its traces.
span_searchable() { # span_searchable <order> <service> <name prefix>: some trace of the order has that span
    local t; for t in $("${OBSERVE[@]}" trace-ids "$1"); do "${OBSERVE[@]}" has-span "$t" "$2" "$3" && return 0; done; return 1
}
required_spans_searchable() { # required_spans_searchable <order> <service:name prefix>...
    local o="$1" spec; shift
    for spec in "$@"; do span_searchable "$o" "${spec%%:*}" "${spec#*:}" || return 1; done
}
trace_count_settled() { # trace_count_settled <order> <n> <service:name prefix>...: wait for the required spans, then count once
    local o="$1" n="$2"; shift 2
    poll 90 required_spans_searchable "$o" "$@" && trace_count_is "$o" "$n"
}
trace_has_services() { # trace_has_services <order> <service...>: the one trace has spans of every service
    local t svcs; t="$(the_trace "$1")"; shift; [[ -n "$t" ]] || return 1
    svcs="$("${OBSERVE[@]}" services "$t")"
    local s; for s in "$@"; do [[ $'\n'"$svcs"$'\n' == *$'\n'"$s"$'\n'* ]] || return 1; done
}
order_on_spans() { # order_on_spans <order> <service...>: each service has a span with that order.id
    local o="$1" s; shift
    for s in "$@"; do "${OBSERVE[@]}" order-in-service "$o" "$s" || return 1; done
}
trace_has_span() { local t; t="$(the_trace "$1")"; [[ -n "$t" ]] && "${OBSERVE[@]}" has-span "$t" "$2" "$3"; } # trace_has_span <order> <service> <name prefix>
logs_cover_services() { # logs_cover_services <order> <service...>: Loki holds lines of the order from each service
    local lines; lines="$("${OBSERVE[@]}" log-lines "$1")"; shift
    local s; for s in "$@"; do [[ $'\n'"$lines" == *$'\n'"$s "* ]] || return 1; done
}
logs_carry_trace() { # logs_carry_trace <order>: every line of the order has the trace id of its one Tempo trace
    local t lines; t="$(the_trace "$1")"; lines="$("${OBSERVE[@]}" log-lines "$1")"
    [[ -n "$t" && -n "$lines" ]] || return 1
    ! awk -v t="$t" '$2 != t {bad = 1} END {exit !bad}' <<<"$lines"
}
log_leads_to_trace() { # log_leads_to_trace <order> <service>: a line of that service names a trace that holds POST /orders
    local t; t="$("${OBSERVE[@]}" log-trace "$2" "$1" | head -n 1)"; [[ -n "$t" ]] || return 1
    "${OBSERVE[@]}" has-span "$t" order-service "POST /orders"
}
trace_leads_to_logs() { # trace_leads_to_logs <order> <n>: Loki returns lines of at least n services for the trace id
    local t; t="$(the_trace "$1")"; [[ -n "$t" ]] || return 1
    (( $("${OBSERVE[@]}" log-services "$t" | wc -l) >= $2 ))
}
sweeper_links_back() { # sweeper_links_back <order>: the trace that starts at the sweeper links to the trace of POST /orders
    local first="" sweeper="" t
    for t in $("${OBSERVE[@]}" trace-ids "$1"); do
        if "${OBSERVE[@]}" has-span "$t" order-service "POST /orders"; then first="$t"
        elif [[ "$("${OBSERVE[@]}" root-span "$t")" == "cancel expired saga" ]]; then sweeper="$t"; fi
    done
    [[ -n "$first" && -n "$sweeper" ]] && [[ "$("${OBSERVE[@]}" links "$sweeper")" == *"-> $first"* ]]
}
logs_of_both_traces() { # logs_of_both_traces <order>: Loki lines of the order name two different trace ids
    [[ "$("${OBSERVE[@]}" log-lines "$1" | awk '{print $2}' | sort -u | wc -l)" == 2 ]]
}
route_line_lacks_order_id() { # route_line_lacks_order_id <order>: by trace id payment-service has the route line, by orderId it does not
    local t by_trace by_order; t="$(the_trace "$1")"; [[ -n "$t" ]] || return 1
    by_trace="$("${OBSERVE[@]}" lines-by-trace payment-service "$t")"; by_order="$("${OBSERVE[@]}" lines-by-order payment-service "$1")"
    [[ "$by_trace" == *"taking the payment for $1"* && -n "$by_order" && "$by_order" != *"taking the payment for $1"* ]]
}
prom_holds() { curl -sf -G "http://localhost:19090/api/v1/query" --data-urlencode "query=$1" | grep '"value"' >/dev/null; }

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
precondition "Connect started with the tracing agent (CONNECT_ENABLE_OTEL=false: without it)" poll 30 agent_state_matches
precondition "SKU-27 holds 10" stock_seeded
precondition "no saga exists yet" expect "$(sql "SELECT count(*) FROM orders.order_saga")" 0

echo "One order, one trace (O-2701)"
place O-2701 SKU-27 1 100.00 || fail_loudly "O-2701 was not accepted"
check "O-2701 is PAID within 15 s and its saga is COMPLETED" poll 15 view_is O-2701 status/saga PAID/COMPLETED
check "stock of SKU-27 is 9 and the payment is COMPLETED" poll 10 happy_effects
check "Tempo holds exactly one trace with the order id O-2701" \
    trace_count_settled O-2701 1 "order-service:POST /orders" "order-service:edp.payments.processed" "payment-service:handle event" \
    "inventory-service:handle event"
check "that trace has spans of order-service, connect, inventory-service and payment-service" \
    poll 60 trace_has_services O-2701 order-service connect inventory-service payment-service
check "the same trace has the consumer span of order-service for PaymentProcessed" \
    poll 60 trace_has_span O-2701 order-service "edp.payments.processed"
check "order-service, inventory-service and payment-service each set the order id on a span of their own" \
    poll 60 order_on_spans O-2701 order-service inventory-service payment-service
echo "Logs of the order (O-2701)"
check "Loki holds lines with orderId O-2701 from order-service, inventory-service and payment-service" \
    poll 90 logs_cover_services O-2701 order-service inventory-service payment-service
check "every one of those lines carries the trace id of the Tempo trace" poll 30 logs_carry_trace O-2701
check "a log line of payment-service leads to a trace that holds POST /orders" poll 30 log_leads_to_trace O-2701 payment-service
check "the trace id leads back to log lines of at least three services" poll 30 trace_leads_to_logs O-2701 3

echo "A declined payment (O-2703)"
place O-2703 SKU-27 1 900.00 || fail_loudly "O-2703 was not accepted"
check "O-2703 is CANCELLED with reason PAYMENT_DECLINED and its stock is back at 9" poll 20 declined_compensated
check "the release in inventory-service is a span of the one trace of O-2703" \
    poll 90 trace_has_span O-2703 inventory-service "edp.orders.cancelled"
check "Tempo holds exactly one trace with the order id O-2703" \
    trace_count_settled O-2703 1 "order-service:POST /orders" "inventory-service:edp.orders.cancelled"

echo "A payment-service that does not answer (O-2704)"
"${COMPOSE[@]}" stop payment-service >/dev/null
precondition "payment-service is down and both of its groups have no member" poll 60 payment_down
place O-2704 SKU-27 1 100.00 || fail_loudly "O-2704 was not accepted"
precondition "O-2704 is reserved: its saga waits for the payment and the stock of SKU-27 is 8" poll 20 reserved_and_waiting
DEADLINE_2704="$(deadline_ms O-2704)"
check "the sweeper cancels O-2704 with reason TIMED_OUT, within ${TIMEOUT_SLACK_MS} ms after its deadline" cancelled_on_time O-2704 "$DEADLINE_2704"
echo "NOTE: O-2704 was observed cancelled ${DELTA_MS:-unknown} ms after its deadline"
check "OrderCancelled is on the topic and the stock of SKU-27 is back at 9" poll 30 cancellation_published_and_released
check "Tempo holds exactly two traces with the order id O-2704" \
    trace_count_settled O-2704 2 "order-service:POST /orders" "order-service:cancel expired saga" "inventory-service:edp.orders.cancelled"
check "the trace the sweeper started links to the trace of POST /orders" poll 30 sweeper_links_back O-2704
check "Loki lines of O-2704 name two trace ids: the request and the sweeper" poll 60 logs_of_both_traces O-2704
"${COMPOSE[@]}" up -d --no-deps --wait payment-service
poll 60 ready 8084 || fail_loudly "payment-service is not ready after the restart"
precondition "payment-service catches up: both of its groups have lag 0" poll 60 payment_groups_caught_up
check "payment-service refused the late reservation of O-2704: its log says so, there is no payment row and no payment event" \
    poll 30 refused_and_no_money O-2704
check "no PaymentProcessed and no PaymentFailed exists for O-2704" no_payment_event O-2704

echo "Observability"
check "Grafana has the trace_id link from Loki to Tempo and the link from Tempo to Loki" "${OBSERVE[@]}" grafana-links
check "Prometheus holds saga_outcomes_total from order-service for compensated and timed-out orders, at least 2" \
    poll 90 prom_holds 'sum(max_over_time(saga_outcomes_total{outcome=~"compensated|timed-out",service_name="order-service"}[1h])) >= 2'

echo "Which lines carry the order id"
check "payment-service's line from the route names the trace and lacks orderId; the line from inside the payment has both" \
    poll 30 route_line_lacks_order_id O-2701

if [[ "$PASSED" != "$TOTAL" ]]; then
    for s in order-service inventory-service payment-service; do
        echo "$s log, last lines:" >&2
        docker logs "edp-$s" 2>&1 | tail -25 | cut -c1-260 >&2 || true
    done
fi
echo "$PASSED/$TOTAL checks passed"
[[ "$PASSED" == "$TOTAL" ]]
