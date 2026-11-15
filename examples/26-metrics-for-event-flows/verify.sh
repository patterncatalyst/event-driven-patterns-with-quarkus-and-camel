#!/usr/bin/env bash
# Starts the stack, registers the outbox connector and checks four signals of an event flow where each can be seen:
# the outbox backlog (replication-slot lag), consumer group lag at the broker, the dead letter topic and the age of
# events, with a provisioned dashboard and one provisioned alert. The relay is paused, a consumer is stopped and
# dead letters are redriven. BACKLOG_SOURCE=table and ALERT_ON=age (demo code) are the negative witnesses (see the
# README). KEEP=1 leaves the stack running afterwards.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-26

COMPOSE=(docker compose -f compose.yaml)
ORDERS=http://localhost:8080
CONNECT=http://localhost:8083
PROM=http://localhost:19090
GRAFANA=http://localhost:3000
CONNECTOR=order-flow-outbox
CONNECTOR_JSON="${CONNECTOR_JSON:-connect/order-flow-outbox.json}"
PSQL=(docker exec -e PGPASSWORD=edp edp-postgres psql -h 127.0.0.1 -U edp -d edp -tA)
GROUP=inventory-placed
THRESHOLD_BYTES=16384                  # the alert threshold; the example's grafana/alerting/backlog.yaml holds the same number
STALL_AGE_MS=35000                     # the paused orders wait at least this long, so they are older than the 30 s bucket
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
ready() { curl -sf "http://localhost:$1/q/health/ready" >/dev/null; } # ready <port>

# --- topics, groups and the connector
topics_init_done() { [[ "$(docker inspect -f '{{.State.Status}}/{{.State.ExitCode}}' edp-topics-init)" == "exited/0" ]]; }
partitions_of() { kafka kafka-topics.sh --describe --topic "$1" 2>/dev/null | grep -c 'Partition: [0-9]' || true; }
topics_have_one_partition() { [[ "$(partitions_of edp.orders.placed)/$(partitions_of edp.orders.placed-dlq)" == "1/1" ]]; }
plugin_listed() { local out; out="$(curl -sf "$CONNECT/connector-plugins")" || return 1; [[ "$out" == *io.debezium.connector.postgresql.PostgresConnector* ]]; }
connector_states() { # connector_states: "<connector state> <task states>" on one line
    curl -sf "$CONNECT/connectors/$CONNECTOR/status" | python3 -I -c "
import json, sys
d = json.load(sys.stdin)
print(d['connector']['state'], *[t['state'] for t in d['tasks']])"
}
connector_is() { # connector_is <state>: the connector and every one of its tasks are in the state, and there is a task
    local s; s="$(connector_states)" || return 1
    [[ "$s" == "$1 $1" ]]
}
describe_all() { kafka kafka-consumer-groups.sh --describe --all-groups 2>/dev/null; }
group_owns_partition() { describe_all | awk -v g="$1" '$1 == g && $3 ~ /^[0-9]+$/ && $7 != "-" {f = 1} END {exit !f}'; }
no_member() { ! group_owns_partition "$1"; }
end_offset() { kafka kafka-get-offsets.sh --topic "$1" 2>/dev/null | awk -F: '{print $3}'; }

# --- Prometheus, Grafana
prom_num() { # prom_num <query>: the first sample's value, or "none"
    curl -sf -G "$PROM/api/v1/query" --data-urlencode "query=$1" | python3 -I -c '
import json, sys
r = json.load(sys.stdin)["data"]["result"]
print(r[0]["value"][1] if r else "none")'
}
prom_has() { [[ "$(prom_num "$1")" != "none" ]]; }
num_is() { local v; v="$(prom_num "$1")" || return 1; python3 -I -c 'import sys; sys.exit(0 if float(sys.argv[1]) == float(sys.argv[2]) else 1)' "$v" "$2"; }
num_lt() { local v; v="$(prom_num "$1")" || return 1; python3 -I -c 'import sys; sys.exit(0 if float(sys.argv[1]) < float(sys.argv[2]) else 1)' "$v" "$2"; }
num_gt() { local v; v="$(prom_num "$1")" || return 1; python3 -I -c 'import sys; sys.exit(0 if float(sys.argv[1]) > float(sys.argv[2]) else 1)' "$v" "$2"; }
val0() { # val0 <query>: the value as an integer, 0 when there is no series yet
    local v; v="$(prom_num "$1")" || return 1
    if [[ "$v" == none ]]; then echo 0; else python3 -I -c 'import sys; print(int(float(sys.argv[1])))' "$v"; fi
}
# The age timers export a count and explicit buckets (le in milliseconds); each function sums the series of the service.
age_count() { val0 "sum(order_flow_$1_milliseconds_count)"; }                          # age_count event_age|record_age
age_le() { val0 "sum(order_flow_$1_milliseconds_bucket{le=~\"$2|$2.0\"})"; }          # age_le event_age|record_age <ms>
broker_lag() { prom_num "sum(kafka_consumer_group_lag_ratio{group=\"$GROUP\"})"; }
lag_is() { num_is "sum(kafka_consumer_group_lag_ratio{group=\"$GROUP\"})" "$1"; }
backlog_over() { num_gt 'max(outbox_backlog_bytes)' "$THRESHOLD_BYTES"; }
backlog_under() { num_lt 'max(outbox_backlog_bytes)' "$THRESHOLD_BYTES"; }
dlq_end_is() { num_is 'max(kafka_partition_current_offset_ratio{topic="edp.orders.placed-dlq"})' "$1"; }
client_lag_absent() { prom_has 'absent_over_time(kafka_consumer_fetch_manager_records_lag_max{service_name="inventory-service"}[15s])'; }
client_lag_present() { prom_has 'count_over_time(kafka_consumer_fetch_manager_records_lag_max{service_name="inventory-service"}[30s])'; }
dashboard_loaded() { curl -sf "$GRAFANA/api/dashboards/uid/edp-order-flow" | python3 -I -c '
import json, sys
d = json.load(sys.stdin)
sys.exit(0 if d["meta"]["provisioned"] and len(d["dashboard"]["panels"]) == 6 else 1)'; }
alert_state() { # alert_state: missing, Normal, Pending or Alerting, from Grafana's Prometheus-compatible rules API
    curl -sf "$GRAFANA/api/prometheus/grafana/api/v1/rules" | python3 -I -c '
import json, sys
state = "missing"
for g in json.load(sys.stdin)["data"]["groups"]:
    for r in g["rules"]:
        if r.get("name") == "order flow stalled":
            s = {a["state"] for a in r.get("alerts", [])}
            state = "Alerting" if "Alerting" in s else "Pending" if "Pending" in s else "Normal"
print(state)'
}
alert_is() { [[ "$(alert_state)" == "$1" ]]; }
panel_queries_return_data() { # every expression of every provisioned panel returns a series
    curl -sf "$GRAFANA/api/dashboards/uid/edp-order-flow" | python3 -I -c '
import json, sys
for p in json.load(sys.stdin)["dashboard"]["panels"]:
    for t in p["targets"]:
        print(t["expr"])' | while IFS= read -r q; do
        prom_has "$q" || { echo "no series: $q" >&2; return 1; }
    done
}
tempo_has_trace() { local out; out="$(curl -sf -G "http://localhost:3200/api/search" --data-urlencode "tags=service.name=$1")" || return 1; [[ "$out" == *'"traceID"'* ]]; }
loki_has_line() { local out; out="$(curl -sf -G "http://localhost:3100/loki/api/v1/query_range" --data-urlencode "query={service_name=\"$1\"} |= \"$2\"")" || return 1; [[ "$out" == *'"values"'* ]]; }

# --- the orders
place() { # place <id> <sku>
    curl -sf -o /dev/null -X POST -H 'Content-Type: application/json' "$ORDERS/orders" \
        --data "{\"id\":\"$1\",\"customerId\":\"CU-26\",\"itemSku\":\"$2\",\"quantity\":1,\"amount\":10.00}"
}
place_range() { # place_range <first> <last> <sku>: orders O-<n> for each n
    local n
    for ((n = $1; n <= $2; n++)); do place "O-$n" "$3" || return 1; done
}
burst() { # burst <first> <count>: <count> orders of SKU-26 from 8 threads; prints how many were accepted
    python3 -I - "$ORDERS" "$1" "$2" <<'PY'
import json, sys, urllib.request
from concurrent.futures import ThreadPoolExecutor
base, first, count = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
def post(i):
    body = json.dumps({"id": "O-%d" % i, "customerId": "CU-26", "itemSku": "SKU-26", "quantity": 1, "amount": 10.00}).encode()
    req = urllib.request.Request(base + "/orders", body, {"Content-Type": "application/json"})
    with urllib.request.urlopen(req) as r:
        return r.status == 201
with ThreadPoolExecutor(8) as pool:
    print(sum(pool.map(post, range(first, first + count))))
PY
}
stock() { sql "SELECT quantity_on_hand FROM inventory.inventory_item WHERE sku = '$1'"; }
stock_is() { [[ "$(stock "$1")" == "$2" ]]; } # stock_is <sku> <n>

# --- dead letters: read them with their headers, and write them back as the operator's redrive
dlq_lines() { kafka kafka-console-consumer.sh --topic edp.orders.placed-dlq --from-beginning --timeout-ms 4000 \
    --formatter-property print.headers=true --formatter-property print.key=true 2>/dev/null; }
dlq_with_event_time() { [[ "$(dlq_lines | awk -F'\t' '$1 ~ /occurredAt:/ {n++} END {print n + 0}')" == "$1" ]]; }
produce() { # produce <topic>: lines "id:<id>,eventType:<Type>,occurredAt:<time><TAB><key>|<json>" on stdin
    docker exec -i edp-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic "$1" \
        --reader-property parse.key=true --reader-property 'key.separator=|' --reader-property parse.headers=true >/dev/null 2>&1
}
redrive() { # redrive: writes every dead letter back to edp.orders.placed with its original id, type and event time
    dlq_lines | python3 -I -c '
import re, sys
for line in sys.stdin:
    headers, key, value = line.rstrip("\n").split("\t", 2)
    kept = [m.group(1) for m in (re.search(r"(?:^|,)(" + n + r":[^,]+)", headers) for n in ("id", "eventType", "occurredAt")) if m]
    print(",".join(kept) + "\t" + key + "|" + value)' | produce edp.orders.placed
}
topology_ready() { connector_is RUNNING && topics_have_one_partition; }
metrics_present() {
    prom_has "kafka_consumer_group_lag_ratio{group=\"$GROUP\"}" && prom_has 'kafka_partition_current_offset_ratio{topic="edp.orders.placed"}' && client_lag_present
}
restarted_and_caught_up() { lag_is 0 && stock_is SKU-26 939; }
counted_is() { [[ "$(age_count event_age)" == "$1" && "$(age_count record_age)" == "$1" ]]; } # counted_is <n>: n arrivals counted since the last restart
age_counted_since_baseline() { (( $(age_count event_age) - BURST_BASE >= $1 )); } # age_counted_since_baseline <n>
waited_ms() { (( $(now_ms) - $1 >= $2 )); } # waited_ms <since ms> <at least ms>

# --- age checks: counts and buckets of the two timers, as differences from a baseline taken before the step
snapshot_ages() { B_EC="$(age_count event_age)"; B_E5="$(age_le event_age 5000)"; B_E30="$(age_le event_age 30000)"
    B_RC="$(age_count record_age)"; B_R5="$(age_le record_age 5000)"; B_R30="$(age_le record_age 30000)"; }
arrivals_are() { # arrivals_are <n>: both timers counted n more records than the baseline
    [[ "$(age_count event_age)" == "$((B_EC + $1))" && "$(age_count record_age)" == "$((B_RC + $1))" ]]
}
redriven_ages() { # the n redriven records: under 5 s by record age, over 5 s by event age
    arrivals_are "$1" || return 1
    local r5 e5; r5="$(age_le record_age 5000)"; e5="$(age_le event_age 5000)"
    [[ "$((r5 - B_R5))" == "$1" && "$((e5 - B_E5))" == 0 ]]
}
over_30s() { # over_30s <event_age|record_age> <n>: n records arrived whose age is above the 30 s bucket
    local c b; c="$(age_count "$1")"; b="$(age_le "$1" 30000)"
    if [[ "$1" == event_age ]]; then (( (c - b) - (B_EC - B_E30) == $2 )); else (( (c - b) - (B_RC - B_R30) == $2 )); fi
}

echo "Starting the stack"
# Fresh start: remove any containers and volumes left by an earlier run (KEEP=1, an aborted run),
# then create every container and anonymous volume anew.
"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
"${COMPOSE[@]}" up -d --build --force-recreate --renew-anon-volumes --wait
poll 60 topics_init_done || fail_loudly "topics-init did not finish"
poll 60 ready 8080 || fail_loudly "order-service is not ready"
poll 60 ready 8082 || fail_loudly "inventory-service is not ready"
poll 30 plugin_listed || fail_loudly "Connect does not list the PostgreSQL connector"
curl -sf -X PUT -H 'Content-Type: application/json' --data @"$CONNECTOR_JSON" "$CONNECT/connectors/$CONNECTOR/config" >/dev/null \
    || fail_loudly "the connector was not accepted by Connect"

echo "Infrastructure"
precondition "the connector $CONNECTOR is RUNNING and the order and dead letter topics have one partition each" poll 60 topology_ready
precondition "inventory-service owns the partition of edp.orders.placed in group $GROUP" poll 60 group_owns_partition "$GROUP"
precondition "Grafana loaded the provisioned dashboard edp-order-flow with six panels" poll 60 dashboard_loaded
precondition "the provisioned alert rule is loaded and Normal" poll 60 alert_is Normal
precondition "SKU-26 holds 1000" stock_is SKU-26 1000

echo "A warm-up order, then the flow at rest (O-2600)"
# Heartbeats alone do not move the slot right after a start; the first captured event does.
place O-2600 SKU-26 || fail_loudly "O-2600 was not accepted"
precondition "O-2600 is reserved: stock of SKU-26 is 999" poll 30 stock_is SKU-26 999
precondition "at rest the backlog gauge is exported and under the ${THRESHOLD_BYTES} byte threshold" poll 60 backlog_under
precondition "Prometheus holds the broker's group lag, the broker's end offsets and the client's own lag metric" poll 60 metrics_present

echo "The flow while everything runs (O-2601 to O-2640)"
place_range 2601 2640 SKU-26 || fail_loudly "the orders O-2601 to O-2640 were not accepted"
check "all 40 orders are reserved: stock of SKU-26 is 959" poll 60 stock_is SKU-26 959
check "the broker's group lag is 0" poll 60 lag_is 0

echo "A consumer that stops (O-2641 to O-2660)"
"${COMPOSE[@]}" stop inventory-service >/dev/null
precondition "inventory-service is down and the group has no member" poll 60 no_member "$GROUP"
place_range 2641 2660 SKU-26 || fail_loudly "the orders O-2641 to O-2660 were not accepted"
check "the broker's group lag reaches 20, the orders waiting on the topic" poll 45 lag_is 20
check "the client's own lag metric has no sample in the last 15 s: the stopped service reports nothing" poll 60 client_lag_absent
"${COMPOSE[@]}" up -d --no-deps --wait inventory-service
poll 60 ready 8082 || fail_loudly "inventory-service is not ready after the restart"
check "after the restart the group lag is back at 0 and the stock of SKU-26 is 939" poll 60 restarted_and_caught_up

echo "Dead letters and a redrive (O-2661 to O-2663)"
LATE_PLACED_MS="$(now_ms)"
place_range 2661 2663 SKU-26-LATE || fail_loudly "the orders O-2661 to O-2663 were not accepted"
check "the dead letter topic holds exactly 3 records, as the broker reports" poll 60 dlq_end_is 3
check "the 3 dead letters keep the event time header of the original records" poll 30 dlq_with_event_time 3
check "the dead letter share of orders is above zero" poll 60 num_gt 'sum(kafka_partition_current_offset_ratio{topic="edp.orders.placed-dlq"}) / sum(kafka_partition_current_offset_ratio{topic="edp.orders.placed"})' 0
# The operator fixes the cause (the SKU had no stock row), waits until the orders are old, and redrives them.
sql "INSERT INTO inventory.inventory_item VALUES ('SKU-26-LATE', 100)" >/dev/null || fail_loudly "stock for SKU-26-LATE was not added"
poll 60 waited_ms "$LATE_PLACED_MS" 10000 || fail_loudly "the clock did not advance"
precondition "the 23 arrivals since the restart (20 waiting orders and the 3 that failed) are counted by both age timers" poll 60 counted_is 23
snapshot_ages
redrive || fail_loudly "the dead letters were not written back"
check "the 3 redriven orders are reserved: stock of SKU-26-LATE is 97" poll 60 stock_is SKU-26-LATE 97
check "the 3 redriven records are young by record age and old by event age" poll 60 redriven_ages 3

echo "A relay that stops (O-2671 to O-2690)"
precondition "the 26 arrivals since the restart, redriven ones included, are counted by both age timers" poll 60 counted_is 26
snapshot_ages
OFFSET_BEFORE_PAUSE="$(end_offset edp.orders.placed)"
curl -sf -X PUT "$CONNECT/connectors/$CONNECTOR/pause" >/dev/null || fail_loudly "the connector was not paused"
precondition "the connector and its task are PAUSED" poll 30 connector_is PAUSED
PAUSE_PLACED_MS="$(now_ms)"
place_range 2671 2690 SKU-26 || fail_loudly "the orders O-2671 to O-2690 were not accepted"
check "the outbox table holds 0 rows although 20 orders wait for the relay" expect "$(sql "SELECT count(*) FROM orders.outboxevent")" 0
check "the backlog gauge is over ${THRESHOLD_BYTES} bytes" poll 60 backlog_over
check "the alert is Alerting within 60 s of the pause" poll 60 alert_is Alerting
echo "NOTE: the alert was Alerting $(( $(now_ms) - PAUSE_PLACED_MS )) ms after the orders were placed"
check "no record reached edp.orders.placed while the relay was paused" expect "$(end_offset edp.orders.placed)" "$OFFSET_BEFORE_PAUSE"
poll 90 waited_ms "$PAUSE_PLACED_MS" "$STALL_AGE_MS" || fail_loudly "the clock did not advance"
RESUMED_MS="$(now_ms)"
curl -sf -X PUT "$CONNECT/connectors/$CONNECTOR/resume" >/dev/null || fail_loudly "the connector was not resumed"
check "after the resume the backlog is under the threshold again" poll 60 backlog_under
check "the alert is Normal within 90 s of the resume" poll 90 alert_is Normal
echo "NOTE: the alert was Normal $(( $(now_ms) - RESUMED_MS )) ms after the resume"
check "the 20 orders are reserved: stock of SKU-26 is 919" poll 60 stock_is SKU-26 919
check "the 20 drained events are older than 30 s by event age" poll 60 over_30s event_age 20
check "the 20 drained records are older than 30 s by record age too: the record timestamp is the commit time" poll 60 over_30s record_age 20

echo "A burst of 500 orders"
BURST_BASE="$(age_count event_age)"
BURST_START="$(now_ms)"
precondition "all 500 orders of the burst are accepted" expect "$(burst 260000 500)" 500
check "all 500 are reserved: stock of SKU-26 is 419" poll 120 stock_is SKU-26 419
check "the broker's group lag returns to 0" poll 60 lag_is 0
LAG0_MS=$(( $(now_ms) - BURST_START ))
# The numbers below are a note, not a check: wait until the timers have counted the burst and the exporters have run once more.
poll 60 age_counted_since_baseline 500 || true
sleep 12
BURST_S=$(( ($(now_ms) - BURST_START) / 1000 + 5 )) # the window of the burst, so the stall earlier in the run is not in the numbers
echo "NOTE: lag 0 again ${LAG0_MS} ms after the burst started; over the burst: peak group lag $(prom_num "max_over_time(sum(kafka_consumer_group_lag_ratio{group=\"$GROUP\"})[${BURST_S}s:5s])"), peak backlog $(prom_num "max_over_time(max(outbox_backlog_bytes)[${BURST_S}s:5s])") bytes, event age p95 $(prom_num "histogram_quantile(0.95, sum by (le) (increase(order_flow_event_age_milliseconds_bucket[${BURST_S}s])))") ms"

echo "Dashboard and observability"
check "every query of the provisioned dashboard returns a series" poll 90 panel_queries_return_data
check "Tempo has a trace for order-service" poll 90 tempo_has_trace order-service
check "Loki has a log line of inventory-service that names O-2601" poll 90 loki_has_line inventory-service O-2601

if [[ "$PASSED" != "$TOTAL" ]]; then
    for s in order-service inventory-service; do
        echo "$s log, last lines:" >&2
        docker logs "edp-$s" 2>&1 | tail -25 | cut -c1-260 >&2 || true
    done
fi
echo "$PASSED/$TOTAL checks passed"
[[ "$PASSED" == "$TOTAL" ]]
