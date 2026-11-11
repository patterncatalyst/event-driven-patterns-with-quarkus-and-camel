#!/usr/bin/env bash
# Seeds a backlog of 3 x 200 records on three topics of three partitions, then drains it with the
# notification service under different consumer settings, one run per consumer group. Every check
# asserts a structural relation (a count, a membership, a ratio), never a wall-clock time, and each
# relation has a control run that shows the check can fail. KEEP=1 leaves the stack running.
#
# Settings an experiment can change:
#   FIXED_MAX_POLL_RECORDS (default 20)  the max.poll.records of the run that must not be evicted
#   CLASSIC_ASSIGNOR (default RangeAssignor)  the partition assignor of the classic-protocol run
#   TRICKLE_MIN_BYTES (default 65536)  the fetch.min.bytes of the trickle run that must send fewer fetches
#   SECOND_PROTOCOL (default consumer)  the group.protocol of the run that must move fewer partitions
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-06

COMPOSE=(docker compose -f compose.yaml --profile '*')
PER_TOPIC=200
BACKLOG=$((PER_TOPIC * 3))
TOPICS=(edp.orders.placed edp.payments.processed edp.shipping.scheduled)
WORK_MS=50
POLL_INTERVAL_MS=5000
FIXED_POLL_RECORDS="${FIXED_MAX_POLL_RECORDS:-20}"
CLASSIC_ASSIGNOR="${CLASSIC_ASSIGNOR:-org.apache.kafka.clients.consumer.RangeAssignor}"
TRICKLE_MIN_BYTES="${TRICKLE_MIN_BYTES:-65536}"
SECOND_PROTOCOL="${SECOND_PROTOCOL:-consumer}"
TOTAL=0
PASSED=0
APP_ENV=()

cleanup() {
    if [[ "${KEEP:-0}" == "1" ]]; then
        echo "KEEP=1: stack left running; run \`docker compose --profile '*' down -v\` in this directory to remove it"
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

poll() { # poll <seconds> <command...>: retry every second until the command succeeds
    local end=$((SECONDS + $1)); shift
    until "$@"; do
        (( SECONDS < end )) || return 1
        sleep 1
    done
}

expect() { [[ "$1" == "$2" ]]; } # expect <actual> <expected>
at_least() { (( $1 >= $2 )); }
at_most() { (( $1 <= $2 )); }

kafka() { docker exec edp-kafka "/opt/kafka/bin/$1" --bootstrap-server localhost:9092 "${@:2}"; }
sql() { docker exec -e PGPASSWORD=edp edp-postgres psql -h 127.0.0.1 -U edp -d edp -tA -c "$1"; }

# --- the consumer service -------------------------------------------------------------------

# start_app NAME=value ...: recreate replica 1 with these settings (a new NOTIFY_GROUP is a new run).
start_app() {
    APP_ENV=("$@")
    env "$@" "${COMPOSE[@]}" up -d --no-deps --force-recreate --wait notification-service >/dev/null
}
start_second() {
    env "${APP_ENV[@]}" "${COMPOSE[@]}" up -d --no-deps --force-recreate --wait notification-2 >/dev/null
}
stop_second() { "${COMPOSE[@]}" rm -sf notification-2 >/dev/null 2>&1 || true; }

seed() { # seed <records per topic> <records per second, -1 for unthrottled> <topics...>
    local n="$1" rate="$2"; shift 2
    SEED_RECORDS="$n" SEED_THROUGHPUT="$rate" SEED_TOPICS="$*" "${COMPOSE[@]}" run --rm seeder >/dev/null 2>&1
}

# --- what the database and the broker say -------------------------------------------------

rows_in() { sql "SELECT count(*) FROM notifications.notification WHERE group_id='$1'"; }
rows_are() { [[ "$(rows_in "$1")" == "$2" ]]; }
max_attempts() { sql "SELECT coalesce(max(attempts), 0) FROM notifications.notification WHERE group_id='$1'"; }
rows_over_one_attempt() { sql "SELECT count(*) FROM notifications.notification WHERE group_id='$1' AND attempts > 1"; }
duplicated() { (( $(rows_over_one_attempt "$1") > 0 )); }
consumers_are() { [[ "$(distinct_consumers "$1")" == "$2" ]]; }
distinct_consumers() { sql "SELECT count(DISTINCT consumer) FROM notifications.notification WHERE group_id='$1'"; }
distinct_partitions() { sql "SELECT count(DISTINCT (topic, kafka_partition)) FROM notifications.notification WHERE group_id='$1'"; }

end_offset_sum() { kafka kafka-get-offsets.sh --topic "$1" | awk -F: '{s += $3} END {print s + 0}'; }
group_listed() { [[ "$(kafka kafka-consumer-groups.sh --list 2>/dev/null | grep -cx "$1")" -gt 0 ]]; }
group_type() { kafka kafka-groups.sh --list 2>/dev/null | awk -v g="$1" '$1 == g {print $2}'; }

# Total lag over the nine partitions, or NA until all nine are listed.
lag_of() {
    kafka kafka-consumer-groups.sh --describe --group "$1" 2>/dev/null \
        | awk '$2 ~ /^edp\./ && $6 ~ /^[0-9]+$/ {s += $6; n++} END {if (n == 9) print s; else print "NA"}'
}
lag_is_zero() { [[ "$(lag_of "$1")" == 0 ]]; }
lag_under() { local l; l=$(lag_of "$1"); [[ "$l" != NA ]] && (( l < $2 )); }

members() { kafka kafka-consumer-groups.sh --describe --group "$1" --members 2>/dev/null; }
members_of() { members "$1" | awk -v g="$1" '$1 == g' | wc -l; }
owning_members_of() { members "$1" | awk -v g="$1" '$1 == g && $5 > 0' | wc -l; }
idle_members_of() { members "$1" | awk -v g="$1" '$1 == g && $5 == 0' | wc -l; }
assigned_total() { members "$1" | awk -v g="$1" '$1 == g {s += $5} END {print s + 0}'; }
partitions_of_client() { members "$1" | awk -v g="$1" -v c="$2" '$1 == g && $4 == c {s += $5} END {print s + 0}'; }
snapshot() { kafka kafka-consumer-groups.sh --describe --group "$1" 2>/dev/null | awk '$2 ~ /^edp\./ {print $2, $3, $7}' | sort; }

# The group has the wanted members, owns all nine partitions, and the assignment holds for 10 seconds.
stable() { # stable <group> <members>
    local a b
    [[ "$(members_of "$1")" == "$2" && "$(assigned_total "$1")" == 9 ]] || return 1
    a=$(snapshot "$1"); sleep 10; b=$(snapshot "$1")
    [[ -n "$a" && "$a" == "$b" && "$(members_of "$1")" == "$2" ]]
}

# Distinct partitions that replica 1 gave up, from its client log after line $MARK.
revoked_partitions() {
    docker logs edp-notification-1 2>&1 | tail -n +"$((MARK + 1))" | grep 'Revoke previously assigned partitions' \
        | sed 's/.*assigned partitions \[\(.*\)\].*/\1/' | tr ',' '\n' | tr -d ' ' | grep . | sort -u | wc -l
}
poll_timeout_logged() { [[ "$(docker logs edp-notification-1 2>&1 | grep -c 'poll timeout has expired')" -gt 0 ]]; }

# --- LGTM -------------------------------------------------------------------------------------

# The value of a Prometheus query, or nothing. The raw series carry the host name label and are never printed.
prom() {
    curl -sfG "http://localhost:19090/api/v1/query" --data-urlencode "query=$1" \
        | python3 -c 'import json,sys; r=json.load(sys.stdin)["data"]["result"]; print(int(float(r[0]["value"][1])) if r else "")'
}
fetches() { prom "sum(kafka_consumer_fetch_manager_fetch_total{client_id=\"$1\"})"; }
fetch_series_present() { [[ -n "$(fetches "$1")" ]]; }
tempo_has_trace() { curl -sf -G "http://localhost:3200/api/search" --data-urlencode "tags=service.name=$1" | grep -q '"traceID"'; }
loki_has_logs() {
    curl -sf -G "http://localhost:3100/loki/api/v1/query_range" --data-urlencode "query={service_name=\"$1\"}" | grep -q '"values"'
}

# --- start ------------------------------------------------------------------------------------

"${COMPOSE[@]}" up -d --build --wait kafka postgres lgtm
"${COMPOSE[@]}" build notification-service notification-2 >/dev/null

echo "Backlog"
seed "$PER_TOPIC" -1 "${TOPICS[@]}"
for t in "${TOPICS[@]}"; do
    check "precondition: $t holds $PER_TOPIC seeded records across 3 partitions" \
        expect "$(end_offset_sum "$t") $(kafka kafka-topics.sh --describe --topic "$t" | grep -c 'Partition: ')" "$PER_TOPIC 3"
done
check "precondition: the first group does not exist yet, so its lag is the whole backlog of $BACKLOG" \
    expect "$(group_listed g-default && echo yes || echo no)" no

echo "Default run"
start_app NOTIFY_GROUP=g-default
check "the default run stores one row per record ($BACKLOG)" poll 120 rows_are g-default "$BACKLOG"
check "the group's lag over all nine partitions ends at 0" poll 30 lag_is_zero g-default
check "every record was processed once (no row has more than 1 attempt)" expect "$(max_attempts g-default)" 1
check "every one of the nine partitions contributed rows" expect "$(distinct_partitions g-default)" 9
check "one consumer thread did all the work" expect "$(distinct_consumers g-default)" 1

echo "Concurrency"
start_app NOTIFY_GROUP=g-three CONSUMERS_COUNT=3
check "consumersCount=3: three members own all nine partitions" poll 150 stable g-three 3
check "consumersCount=3: no member is idle" expect "$(idle_members_of g-three)" 0
check "consumersCount=3: the rows were written by three distinct consumer threads" \
    poll 120 consumers_are g-three 3
start_app NOTIFY_GROUP=g-twelve CONSUMERS_COUNT=12
check "precondition: consumersCount=12 gives 12 members that own the nine partitions" poll 180 stable g-twelve 12
check "consumersCount=12 with the default RangeAssignor: only three members own partitions (all three topics go to the same first three), nine are idle" \
    expect "$(owning_members_of g-twelve) $(idle_members_of g-twelve)" "3 9"
check "consumersCount=12 with the default RangeAssignor: the idle members cost nothing, all $BACKLOG records are stored" \
    poll 120 rows_are g-twelve "$BACKLOG"
start_app NOTIFY_GROUP=g-twelve-rr CONSUMERS_COUNT=12 PARTITION_ASSIGNOR=org.apache.kafka.clients.consumer.RoundRobinAssignor
check "precondition: consumersCount=12 with RoundRobinAssignor gives 12 members that own the nine partitions" poll 180 stable g-twelve-rr 12
check "consumersCount=12 with RoundRobinAssignor: nine members own one partition each and exactly three own none" \
    expect "$(owning_members_of g-twelve-rr) $(idle_members_of g-twelve-rr)" "9 3"

echo "Poll interval"
check "precondition: a full poll of 500 records at ${WORK_MS} ms each takes at least 5 times max.poll.interval.ms (${POLL_INTERVAL_MS} ms)" \
    at_least $((500 * WORK_MS)) $((5 * POLL_INTERVAL_MS))
start_app NOTIFY_GROUP=g-slow NOTIFY_WORK_MS=$WORK_MS MAX_POLL_INTERVAL_MS=$POLL_INTERVAL_MS MAX_POLL_RECORDS=500
poll 180 duplicated g-slow || true
check "max.poll.records=500: some records were delivered again (attempts above 1)" duplicated g-slow
check "max.poll.records=500: the client left the group because poll was not called in time" poll_timeout_logged
start_app NOTIFY_GROUP=g-fixed NOTIFY_WORK_MS=$WORK_MS MAX_POLL_INTERVAL_MS=$POLL_INTERVAL_MS MAX_POLL_RECORDS="$FIXED_POLL_RECORDS"
check "precondition: the run with max.poll.records=$FIXED_POLL_RECORDS stores all $BACKLOG records" \
    poll 240 rows_are g-fixed "$BACKLOG"
check "max.poll.records=$FIXED_POLL_RECORDS: no record was delivered again" expect "$(max_attempts g-fixed)" 1
check "max.poll.records=$FIXED_POLL_RECORDS: the client never left the group for a missed poll" \
    expect "$(poll_timeout_logged && echo yes || echo no)" no

echo "Fetch sizing under a trickle"
# Runs until the group has caught up, then counts the fetch requests the client sends while 6000
# records arrive at 300 per second on one topic.
trickle_fetches() { # trickle_fetches <group> <client id> <settings...>
    local f0 f1
    start_app NOTIFY_GROUP="$1" NOTIFY_CLIENT="$2" "${@:3}"
    poll 120 lag_under "$1" 300 || return 1
    poll 60 fetch_series_present "$2" || return 1
    sleep 8
    f0=$(fetches "$2")
    seed 6000 300 edp.orders.placed
    sleep 8
    f1=$(fetches "$2")
    echo $((f1 - f0))
}
A=$(trickle_fetches g-trickle-a trickle-a || echo 0)
B=$(trickle_fetches g-trickle-b trickle-b FETCH_MIN_BYTES="$TRICKLE_MIN_BYTES" FETCH_WAIT_MAX_MS=500 || echo 0)
echo "  fetch requests during the trickle: defaults $A, fetch.min.bytes=65536 $B"
check "precondition: with the defaults the consumer sent at least 100 fetch requests during the trickle" at_least "$A" 100
check "precondition: with fetch.min.bytes=65536 the consumer was alive and fetching" at_least "$B" 1
check "fetch.min.bytes=65536 with fetch.max.wait.ms=500 sends at most a third of the fetch requests" at_most $((B * 3)) "$A"

echo "Rebalance protocol"
scale_out() { # scale_out <group> <protocol> [settings...]
    stop_second
    start_app NOTIFY_GROUP="$1" GROUP_PROTOCOL="$2" CONSUMERS_COUNT=2 "${@:3}"
    poll 150 stable "$1" 2 || true
    MARK=$(docker logs edp-notification-1 2>&1 | wc -l)
    start_second
    poll 180 stable "$1" 4 || true
}

scale_out g-classic classic PARTITION_ASSIGNOR="$CLASSIC_ASSIGNOR"
check "classic protocol: the group type is Classic" expect "$(group_type g-classic)" Classic
check "precondition: after replica 2 joined, it owns at least one partition" at_least "$(partitions_of_client g-classic notification-2)" 1
echo "  partitions replica 1 gave up under classic ($CLASSIC_ASSIGNOR): $(revoked_partitions)"
check "classic protocol: replica 1 gave up all nine partitions when replica 2 joined" expect "$(revoked_partitions)" 9

scale_out g-consumer "$SECOND_PROTOCOL"
check "consumer protocol: the group type is Consumer" expect "$(group_type g-consumer)" Consumer
check "precondition: after replica 2 joined, it owns at least one partition" at_least "$(partitions_of_client g-consumer notification-2)" 1
REVOKED=$(revoked_partitions)
echo "  partitions replica 1 gave up under $SECOND_PROTOCOL: $REVOKED"
check "consumer protocol: replica 1 gave up at least one partition (one actually moved)" at_least "$REVOKED" 1
check "consumer protocol: replica 1 gave up fewer than nine partitions" at_most "$REVOKED" 8

echo "Telemetry"
check "Tempo has a trace for notification-service" poll 90 tempo_has_trace notification-service
check "Prometheus has the consumer's fetch counter, bound by the client metrics factory" poll 60 fetch_series_present notification-1
check "Loki has logs for notification-service" poll 60 loki_has_logs notification-service

echo "$PASSED/$TOTAL checks passed"
[[ "$PASSED" == "$TOTAL" ]]
