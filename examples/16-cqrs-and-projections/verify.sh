#!/usr/bin/env bash
# Starts the stack and checks the observable effects of a read model built from the public topics: each event moves
# the view forward, a paused topic leaves the view stale and visibly so, a status that arrives late does not move the
# view back, an event may arrive before its order, a burst converges, a replay from the earliest offsets changes
# nothing, and one Camel route builds the same view. PROJECTION_GUARD=off and PROJECTOR_DELAY_MS=50 are the negative
# witnesses (see the README). KEEP=1 leaves the stack running afterwards.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-16

COMPOSE=(docker compose -f compose.yaml)
APP=edp-order-service
URL=http://localhost:8080
PSQL=(docker exec -e PGPASSWORD=edp edp-postgres psql -h 127.0.0.1 -U edp -d edp -tA)
PLACED=edp.orders.placed
CANCELLED=edp.orders.cancelled
PAID=edp.payments.processed
SCHEDULED=edp.shipping.scheduled
IN_TRANSIT=edp.shipping.in-transit
DELIVERED=edp.shipping.delivered
TOPICS=("$PLACED" "$CANCELLED" "$PAID" "$SCHEDULED" "$IN_TRANSIT" "$DELIVERED")
GROUPS_AND_TOPICS=("order-view-placed:$PLACED" "order-view-cancelled:$CANCELLED" "order-view-paid:$PAID"
    "order-view-scheduled:$SCHEDULED" "order-view-in-transit:$IN_TRANSIT" "order-view-delivered:$DELIVERED")
P95_LIMIT_MS=2000
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
ready() { curl -sf http://localhost:8080/q/health/ready >/dev/null; }
logs() { docker logs "$APP" 2>&1; }
# grep without -q: under pipefail an early-exiting grep would fail the pipeline with SIGPIPE.
log_has() { logs | grep -F -- "$1" >/dev/null; }
now_ms() { date +%s%3N; }

# --- the topics
topics_init_done() { [[ "$(docker inspect -f '{{.State.Status}}/{{.State.ExitCode}}' edp-topics-init)" == "exited/0" ]]; }
partitions_of() { kafka kafka-topics.sh --describe --topic "$1" 2>/dev/null | grep -c 'Partition: [0-9]' || true; }
topics_have_one_partition() { [[ "$(for t in "${TOPICS[@]}"; do partitions_of "$t"; done | tr '\n' ' ')" == "1 1 1 1 1 1 " ]]; }
uuid() { cat /proc/sys/kernel/random/uuid; }
produce() { # produce <topic>: lines "id:<id>,eventType:<Type><TAB><key>|<json>" on stdin, one producer call
    docker exec -i edp-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic "$1" \
        --reader-property parse.key=true --reader-property 'key.separator=|' --reader-property parse.headers=true >/dev/null 2>&1
}
send() { printf 'id:%s,eventType:%s\t%s|%s\n' "$(uuid)" "$2" "$3" "$4" | produce "$1"; } # send <topic> <Type> <key> <json>
placed_json() { printf '{"order":{"id":"%s","customerId":"%s","itemSku":"SKU-16","quantity":1,"amount":25.00}}' "$1" "$2"; }
paid_json() { printf '{"payment":{"id":"P-%s","orderId":"%s","amount":25.00,"status":"COMPLETED"}}' "$1" "$1"; }
cancelled_json() { printf '{"orderId":"%s","reason":"changed mind"}' "$1"; }
ship_json() { printf '{"shipment":{"id":"S-%s","orderId":"%s","carrier":"FASTLINE","trackingNumber":"FL-%s","status":"%s"}}' "$1" "$1" "$1" "$2"; }
send_placed() { send $PLACED OrderPlaced "$1" "$(placed_json "$1" "$2")"; }                 # send_placed <id> <customer>
send_cancelled() { send $CANCELLED OrderCancelled "$1" "$(cancelled_json "$1")"; }
send_paid() { send $PAID PaymentProcessed "$1" "$(paid_json "$1")"; }
send_scheduled() { send $SCHEDULED ShipmentScheduled "$1" "$(ship_json "$1" SCHEDULED)"; }
send_in_transit() { send $IN_TRANSIT ShipmentInTransit "$1" "$(ship_json "$1" IN_TRANSIT)"; }
send_delivered() { send $DELIVERED ShipmentDelivered "$1" "$(ship_json "$1" DELIVERED)"; }
burst_records() { # burst_records <kind>: 250 records for O-16100 to O-16349 in the owner's format
    python3 -I -c '
import json, sys
kind = sys.argv[1]
for i in range(100, 350):
    order = "O-16%d" % i
    ship = lambda s: {"shipment": {"id": "S-" + order, "orderId": order, "carrier": "FASTLINE", "trackingNumber": "FL-" + order, "status": s}}
    events = {
        "placed": ("OrderPlaced", {"order": {"id": order, "customerId": "C-17%02d" % (i % 20), "itemSku": "SKU-16", "quantity": 1, "amount": 25.00}}),
        "paid": ("PaymentProcessed", {"payment": {"id": "P-" + order, "orderId": order, "amount": 25.00, "status": "COMPLETED"}}),
        "scheduled": ("ShipmentScheduled", ship("SCHEDULED")),
        "in-transit": ("ShipmentInTransit", ship("IN_TRANSIT")),
        "delivered": ("ShipmentDelivered", ship("DELIVERED")),
    }
    event, body = events[kind]
    print("id:b-%s-%d,eventType:%s\t%s|%s" % (kind, i, event, order, json.dumps(body, separators=(",", ":"))))
' "$1"
}

# --- consumer groups: one describe call, then arithmetic. A group that never committed prints "-" for its offset.
describe_all() { kafka kafka-consumer-groups.sh --describe --all-groups 2>/dev/null; }
group_lag() { # group_lag <group>: log end minus committed (0 when nothing is committed), summed over partitions
    describe_all | awk -v g="$1" '$1 == g && $3 ~ /^[0-9]+$/ {c = ($4 == "-") ? 0 : $4; l += $5 - c; seen = 1} END {if (seen) print l}'
}
lag_is() { [[ "$(group_lag "$1")" == "$2" ]]; } # lag_is <group> <n>
all_lag_zero() { # all six groups, from a single describe
    describe_all | awk -v gs="${GROUPS_AND_TOPICS[*]}" '
        BEGIN {n = split(gs, a, " "); for (i = 1; i <= n; i++) {split(a[i], p, ":"); want[p[1]] = 1}}
        $1 in want && $3 ~ /^[0-9]+$/ {seen[$1] = 1; c = ($4 == "-") ? 0 : $4; if ($5 - c != 0) bad = 1}
        END {k = 0; for (g in seen) k++; exit !(k == n && !bad)}'
}
groups_assigned() { # every group owns its partition
    describe_all | awk -v gs="${GROUPS_AND_TOPICS[*]}" '
        BEGIN {n = split(gs, a, " "); for (i = 1; i <= n; i++) {split(a[i], p, ":"); want[p[1]] = 1}}
        $1 in want && $3 ~ /^[0-9]+$/ && $7 != "-" {own[$1] = 1}
        END {k = 0; for (g in own) k++; exit !(k == n)}'
}
groups_inactive() { # no member holds a partition of any of the six groups
    ! describe_all | awk '$1 ~ /^order-view-/ && $3 ~ /^[0-9]+$/ && $7 != "-" {f = 1} END {exit !f}'
}
camel_group_owns_six() { [[ "$(describe_all | awk '$1 == "order-view-camel" && $3 ~ /^[0-9]+$/ && $7 != "-"' | wc -l)" == 6 ]]; }
camel_lag_zero() { lag_is order-view-camel 0; }

# --- the view
field() { # field <json> <name>
    python3 -I -c 'import json, sys; v = json.loads(sys.argv[1]).get(sys.argv[2]); print("" if v is None else v)' "$1" "$2"
}
view() { curl -sf "$URL/orders/$1"; }
view_tuple() { # view_tuple <id> <field/field/...>: the fields of the answer joined by "/"
    local j; j="$(view "$1")" || return 1
    python3 -I -c 'import json, sys; d = json.loads(sys.argv[1]); print("/".join("" if d.get(f) is None else str(d[f]) for f in sys.argv[2].split("/")))' "$j" "$2"
}
view_is() { [[ "$(view_tuple "$1" "$2")" == "$3" ]]; }       # view_is <id> <field/field/...> <value/value/...>
status_is() { view_is "$1" status "$2"; }                      # status_is <id> <status>
shipment_is() { view_is "$1" shipmentStatus "$2"; }            # shipment_is <id> <shipment status>
customer_orders() { # customer_orders <customer>: "<id>:<status>" joined by commas
    local j; j="$(curl -sf "$URL/customers/$1/orders")" || return 1
    python3 -I -c 'import json, sys; print(",".join(o["orderId"] + ":" + o["status"] for o in json.loads(sys.argv[1])))' "$j"
}
customer_orders_are() { [[ "$(customer_orders "$1")" == "$2" ]]; }     # customer_orders_are <customer> <list>
# The whole view as one hash, timestamps of the write excluded. O-1604 is left out: its final state is the subject of
# checks 18 and 27, and without the guard it depends on the order the topics are read.
view_hash() {
    sql "SELECT md5(string_agg(concat_ws('|', order_id, coalesce(customer_id,'~'), coalesce(item_sku,'~'), coalesce(amount::text,'~'),
        coalesce(order_status,'~'), coalesce(payment_status,'~'), coalesce(shipment_status,'~'), coalesce(carrier,'~'),
        coalesce(tracking_number,'~'), coalesce(last_event_at::text,'~')), ',' ORDER BY order_id)) FROM orders.order_view WHERE order_id <> 'O-1604'"
}
tables() { sql "SELECT string_agg(table_name, ',' ORDER BY table_name) FROM information_schema.tables WHERE table_schema = 'orders'"; }
delivered_and_caught_up() { status_is "$1" DELIVERED && lag_is order-view-delivered 0; }
partial_row() { view_is "$1" shipmentStatus/customerId "SCHEDULED/" && [[ -z "$(customer_orders "$2")" ]]; } # partial_row <id> <customer>
channel_paused() { [[ "$(curl -sf "$URL/demo/channels/$1" | python3 -I -c 'import json, sys; print(json.load(sys.stdin)["paused"])')" == "True" ]]; }
burst_converged() {
    [[ "$(sql "SELECT count(*) FROM orders.order_view WHERE order_id BETWEEN 'O-16100' AND 'O-16349' AND order_status = 'PLACED' AND payment_status = 'COMPLETED' AND shipment_status = 'DELIVERED'")" == 250 ]] && all_lag_zero
}
lag_field() { curl -sf "$URL/demo/lag" | python3 -I -c 'import json, sys; print(json.load(sys.stdin)[sys.argv[1]])' "$1"; }
p95_below_limit() { (( $(lag_field p95Ms) < P95_LIMIT_MS )); }
stale_reads() { # five reads over five seconds: SHIPPED each time, with asOf before the instant $2 (epoch ms) the delivered record existed
    local i j asof
    for i in 1 2 3 4 5; do
        j="$(view "$1")" || return 1
        [[ "$(field "$j" status)" == SHIPPED ]] || return 1
        asof="$(date -d "$(field "$j" asOf)" +%s%3N)"
        (( asof < $2 )) || return 1
        sleep 1
    done
}

# --- observability
prom_holds() { curl -sf -G "http://localhost:19090/api/v1/query" --data-urlencode "query=$1" | grep '"value"' >/dev/null; }
tempo_has_trace() { local out; out="$(curl -sf -G "http://localhost:3200/api/search" --data-urlencode "tags=service.name=$1")" || return 1; [[ "$out" == *'"traceID"'* ]]; }
loki_has_log() { local out; out="$(curl -sf -G "http://localhost:3100/loki/api/v1/query_range" --data-urlencode "query={service_name=\"$1\"}")" || return 1; [[ "$out" == *'"values"'* ]]; }

echo "Starting the stack"
# Fresh start: remove any containers and volumes left by an earlier run (KEEP=1, an aborted run),
# then create every container and anonymous volume anew.
"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
"${COMPOSE[@]}" up -d --build --force-recreate --renew-anon-volumes --wait
poll 60 topics_init_done || fail_loudly "topics-init did not finish"
poll 60 ready || fail_loudly "order-service is not ready"

echo "Infrastructure"
precondition "order-service is ready and the six groups each own their partition" poll 60 groups_assigned
precondition "the six topics exist with one partition each" topics_have_one_partition
check "the schema holds the view and the migration history, and no events table or write model" \
    expect "$(tables)" "flyway_schema_history,order_view"

echo "A lifecycle read through the view (O-1601)"
send_placed O-1601 C-1601
check "OrderPlaced: O-1601 is PLACED within 10 s, with its customer and amount" \
    poll 10 view_is O-1601 status/customerId/amount "PLACED/C-1601/25.0"
send_paid O-1601
check "PaymentProcessed: PAID" poll 10 status_is O-1601 PAID
send_scheduled O-1601
check "ShipmentScheduled: SHIPPED, with carrier FASTLINE and tracking number FL-O-1601" \
    poll 10 view_is O-1601 status/carrier/trackingNumber "SHIPPED/FASTLINE/FL-O-1601"
send_in_transit O-1601
check "ShipmentInTransit: still SHIPPED, shipment IN_TRANSIT" poll 10 shipment_is O-1601 IN_TRANSIT
send_delivered O-1601
check "ShipmentDelivered: DELIVERED" poll 10 status_is O-1601 DELIVERED
send_placed O-1602 C-1602
poll 10 status_is O-1602 PLACED || fail_loudly "O-1602 was not projected"
check "the orders of C-1601 are O-1601 as DELIVERED and nothing of another customer" customer_orders_are C-1601 "O-1601:DELIVERED"

echo "A cancellation"
send_cancelled O-1602
check "OrderCancelled: O-1602 is CANCELLED" poll 10 status_is O-1602 CANCELLED

echo "Read lag: the delivered topic held back (O-1603)"
send_placed O-1603 C-1603; send_paid O-1603; send_scheduled O-1603
poll 10 status_is O-1603 SHIPPED || fail_loudly "O-1603 was not projected"
curl -sf -X POST "$URL/demo/channels/delivered/pause" >/dev/null || fail_loudly "the delivered channel could not be paused"
precondition "the delivered channel reports paused" channel_paused delivered
send_delivered O-1603
SENT_MS="$(now_ms)"
check "after ShipmentDelivered is produced, the group order-view-delivered has lag 1" poll 20 lag_is order-view-delivered 1
check "five reads over five seconds all return SHIPPED, each with asOf before the delivered record was produced" \
    stale_reads O-1603 "$SENT_MS"
RESUME_MS="$(now_ms)"
curl -sf -X POST "$URL/demo/channels/delivered/resume" >/dev/null || fail_loudly "the delivered channel could not be resumed"
check "after resume O-1603 is DELIVERED within 10 s and the group has lag 0" \
    poll 10 delivered_and_caught_up O-1603
check "Prometheus holds order_view_lag_max_milliseconds for order-service, at least as long as the pause" \
    poll 90 prom_holds "max_over_time(order_view_lag_max_milliseconds{service_name=\"order-service\"}[10m]) >= $((RESUME_MS - SENT_MS))"

echo "Order across topics: IN_TRANSIT arrives after DELIVERED (O-1604)"
send_placed O-1604 C-1604
poll 10 status_is O-1604 PLACED || fail_loudly "O-1604 was not projected"
curl -sf -X POST "$URL/demo/channels/in-transit/pause" >/dev/null || fail_loudly "the in-transit channel could not be paused"
precondition "the in-transit channel reports paused" channel_paused in-transit
send_scheduled O-1604; send_delivered O-1604; send_in_transit O-1604
check "ShipmentScheduled and ShipmentDelivered are applied while ShipmentInTransit waits: DELIVERED" \
    poll 10 status_is O-1604 DELIVERED
curl -sf -X POST "$URL/demo/channels/in-transit/resume" >/dev/null || fail_loudly "the in-transit channel could not be resumed"
poll 20 lag_is order-view-in-transit 0 || fail_loudly "the in-transit group did not catch up"
check "after ShipmentInTransit is applied, O-1604 is still DELIVERED, with shipment DELIVERED" \
    view_is O-1604 status/shipmentStatus "DELIVERED/DELIVERED"

echo "An event before its order (O-1605)"
curl -sf -X POST "$URL/demo/channels/placed/pause" >/dev/null || fail_loudly "the placed channel could not be paused"
precondition "the placed channel reports paused" channel_paused placed
send_placed O-1605 C-1605
send_scheduled O-1605
check "ShipmentScheduled before OrderPlaced: O-1605 has shipment SCHEDULED, no customer, and is not listed for C-1605" \
    poll 10 partial_row O-1605 C-1605
curl -sf -X POST "$URL/demo/channels/placed/resume" >/dev/null || fail_loudly "the placed channel could not be resumed"
check "after OrderPlaced is applied, O-1605 is listed under C-1605 as SHIPPED" poll 10 customer_orders_are C-1605 "O-1605:SHIPPED"

echo "A burst: 250 orders, 1,250 records"
for kind in placed paid scheduled in-transit delivered; do
    case "$kind" in
        placed) topic=$PLACED ;; paid) topic=$PAID ;; scheduled) topic=$SCHEDULED ;;
        in-transit) topic=$IN_TRANSIT ;; delivered) topic=$DELIVERED ;;
    esac
    burst_records "$kind" | produce "$topic"
done
check "every one of the 250 orders reaches PLACED, paid, shipment DELIVERED within 60 s, and every group has lag 0" \
    poll 60 burst_converged
echo "NOTE: lag over the run: $(curl -sf "$URL/demo/lag")"
check "the 95th percentile of order.view.lag is below ${P95_LIMIT_MS} ms" p95_below_limit

echo "Replay from the earliest offsets"
HASH="$(view_hash)"
echo "NOTE: view hash before the replay: $HASH"
"${COMPOSE[@]}" stop order-service >/dev/null
poll 60 groups_inactive || fail_loudly "the groups still have members"
for pair in "${GROUPS_AND_TOPICS[@]}"; do
    kafka kafka-consumer-groups.sh --group "${pair%%:*}" --topic "${pair##*:}" --reset-offsets --to-earliest --execute >/dev/null 2>&1 \
        || fail_loudly "could not reset ${pair%%:*}"
done
all_lag_zero && fail_loudly "the reset left nothing to read"
"${COMPOSE[@]}" up -d --no-deps --wait order-service
poll 60 ready || fail_loudly "order-service is not ready after the reset"
poll 90 all_lag_zero || fail_loudly "the groups did not read the topics again"
check "after every group read its topic again from offset 0, the view hash is unchanged" expect "$(view_hash)" "$HASH"

echo "Camel: one route, one group, six topics"
sql "DELETE FROM orders.order_view" >/dev/null
PROJECTOR_PROFILE=camel-projector "${COMPOSE[@]}" up -d --no-deps --force-recreate --wait order-service
poll 60 ready || fail_loudly "order-service is not ready with the camel-projector profile"
precondition "the route order-view-camel has started and its group owns the six partitions" \
    poll 60 camel_group_owns_six
poll 90 camel_lag_zero || fail_loudly "order-view-camel did not read the topics"
check "the view built by the route has the same hash as the view built by the channels" expect "$(view_hash)" "$HASH"
check "O-1604 is DELIVERED on the Camel path too, with shipment DELIVERED" \
    view_is O-1604 status/shipmentStatus "DELIVERED/DELIVERED"

echo "Observability"
check "Tempo has a trace for order-service" poll 90 tempo_has_trace order-service
check "Loki has logs for order-service" poll 90 loki_has_log order-service

if [[ "$PASSED" != "$TOTAL" ]]; then
    echo "order-service log, last lines:" >&2
    logs | tail -40 | cut -c1-260 >&2 || true
fi
echo "$PASSED/$TOTAL checks passed"
[[ "$PASSED" == "$TOTAL" ]]
