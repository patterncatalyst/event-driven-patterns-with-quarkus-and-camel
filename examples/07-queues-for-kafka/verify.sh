#!/usr/bin/env bash
# Starts the stack, seeds 30 notification jobs onto edp.orders.placed and delivers them twice: through a share
# group (DELIVERY_MODE=share, the default) and through a consumer group (the control). One C-SLOW job sits first
# in its partition; the relation under test is whether later jobs of that partition finish before it does.
# DELIVERY_MODE=group makes the first run a consumer-group run, so the share relation must fail.
# KEEP=1 leaves the stack running afterwards.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-07

export DELIVERY_MODE="${DELIVERY_MODE:-share}"
export GATEWAY_SLOW_MS="${GATEWAY_SLOW_MS:-8000}"
COMPOSE=(docker compose -f compose.yaml)
TOPIC=edp.orders.placed
SHARE_GROUP=notification-share
CONSUMER_GROUP=notification-workers
PSQL=(docker exec -e PGPASSWORD=edp edp-postgres psql -h 127.0.0.1 -U edp -d edp -tA)
TOTAL=0
PASSED=0
JOBS=30
FIRST_MODE="$DELIVERY_MODE"

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
tempo_has_trace() { curl -sf -G "http://localhost:3200/api/search" --data-urlencode "tags=service.name=$1" | grep -q '"traceID"'; }

members_stable() { # every member has a share-partition assignment, and two reads 3 s apart agree
    local a b
    a=$(kafka kafka-share-groups.sh --describe --group "$SHARE_GROUP" --members 2>/dev/null | awk -v g="$SHARE_GROUP" '$1 == g {print $4 "=" $5 "/" $NF}' | sort)
    sleep 3
    b=$(kafka kafka-share-groups.sh --describe --group "$SHARE_GROUP" --members 2>/dev/null | awk -v g="$SHARE_GROUP" '$1 == g {print $4 "=" $5 "/" $NF}' | sort)
    [[ "$a" == "$b" && "$(wc -l <<<"$a")" -eq 4 ]] && ! grep -q '=0/' <<<"$a"
}
share_members() { kafka kafka-share-groups.sh --describe --group "$SHARE_GROUP" --members 2>/dev/null | awk -v g="$SHARE_GROUP" '$1 == g' | wc -l; }
group_members() { kafka kafka-consumer-groups.sh --describe --group "$CONSUMER_GROUP" --members 2>/dev/null | awk -v g="$CONSUMER_GROUP" '$1 == g' | wc -l; }
group_assigned() { # all 3 partitions are assigned across the 4 members
    kafka kafka-consumer-groups.sh --describe --group "$CONSUMER_GROUP" --members 2>/dev/null \
        | awk -v g="$CONSUMER_GROUP" '$1 == g {n += $5} END {exit !(n == 3)}'
}
members_are() { # members_are <share|group> <n>
    local n
    if [[ "$1" == share ]]; then n=$(share_members); else n=$(group_members); fi
    [[ "$n" == "$2" ]]
}
lock_ms() { kafka kafka-configs.sh --describe --entity-type groups --entity-name "$SHARE_GROUP" 2>/dev/null | grep -o 'share.record.lock.duration.ms=[0-9]*' | head -1 | cut -d= -f2; }
group_config() { kafka kafka-configs.sh --describe --entity-type groups --entity-name "$SHARE_GROUP" 2>/dev/null | grep -c "$1"; }

# The 30 jobs (published once for the share group, once more for the consumer group). Job 1 is the slow customer and, on a fresh topic, the first record of its partition. Job 5 fails
# once and then succeeds; job 6 bounces for good. Keys are order ids, so a job's partition follows from its key.
seed() {
    local i customer
    for i in $(seq 1 "$JOBS"); do
        case "$i" in
            1) customer=C-SLOW-1 ;;
            5) customer=C-RETRY-1 ;;
            6) customer=C-BOUNCE-1 ;;
            *) customer="C-$((8000 + i))" ;;
        esac
        printf 'O-%d|{"orderId":"O-%d","order":{"id":"O-%d","customerId":"%s","itemSku":"SKU-1","quantity":1,"amount":10.00}}\n' \
            $((7000 + i)) $((7000 + i)) $((7000 + i)) "$customer"
        # SEED_LINGER_MS=0 sends each job as its own batch; a larger value writes a partition's jobs as one batch.
        if [[ "${SEED_LINGER_MS:-0}" == 0 ]]; then sleep 0.05; fi
    done | docker exec -i edp-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 \
        --topic "$TOPIC" --property parse.key=true --property 'key.separator=|' --producer-property "linger.ms=${SEED_LINGER_MS:-0}" >/dev/null 2>&1
}

finished() { [[ "$(sql "SELECT count(DISTINCT order_id) FROM notifications.delivery_log WHERE mode='$1' AND outcome IN ('sent','rejected')")" == "$JOBS" ]]; }
count() { sql "SELECT count(*) FROM notifications.delivery_log WHERE mode='$1' ${2:+AND $2}"; }

# The slow job: partition, offset, finish time. Later jobs are the jobs of the same partition with a higher offset.
SLOW="SELECT record_partition AS p, record_offset AS o, started_at AS st, finished_at AS f FROM notifications.delivery_log WHERE mode='\$M' AND customer_id LIKE 'C-SLOW%' AND outcome='sent'"
slow_sql() { echo "${SLOW//\$M/$1}"; }
later_count() { sql "WITH s AS ($(slow_sql "$1")) SELECT count(*) FROM notifications.delivery_log d, s WHERE d.mode='$1' AND d.outcome='sent' AND d.record_partition=s.p AND d.record_offset>s.o"; }
overtaking_count() { sql "WITH s AS ($(slow_sql "$1")) SELECT count(*) FROM notifications.delivery_log d, s WHERE d.mode='$1' AND d.outcome='sent' AND d.record_partition=s.p AND d.record_offset>s.o AND d.finished_at<s.f"; }
slow_is_first() { sql "WITH s AS ($(slow_sql "$1")) SELECT count(*) FROM s WHERE s.o=(SELECT min(record_offset) FROM notifications.delivery_log WHERE mode='$1' AND record_partition=s.p)"; }
slow_ms() { sql "WITH s AS ($(slow_sql "$1")) SELECT floor(extract(epoch FROM (f-st))*1000) FROM s"; }
workers_used() { sql "SELECT count(DISTINCT worker) FROM notifications.delivery_log WHERE mode='$1'"; }
max_workers_per_partition() { sql "SELECT coalesce(max(n),0) FROM (SELECT count(DISTINCT worker) AS n FROM notifications.delivery_log WHERE mode='$1' GROUP BY record_partition) t"; }
outcomes_of() { sql "SELECT coalesce(string_agg(outcome, ',' ORDER BY id), '') FROM notifications.delivery_log WHERE mode='$1' AND order_id='$2'"; }
counts_of() { sql "SELECT coalesce(string_agg(delivery_count::text, ',' ORDER BY id), '') FROM notifications.delivery_log WHERE mode='$1' AND order_id='$2'"; }
true_if() { [[ "$1" == "$2" ]]; }
at_least() { (( $1 >= $2 )); }
positive() { (( $1 > 0 )); }
zero() { (( $1 == 0 )); }

echo "Starting the stack in $FIRST_MODE mode"
"${COMPOSE[@]}" up -d --build --wait

check "job topic has 3 partitions" \
    expect "$(kafka kafka-topics.sh --describe --topic "$TOPIC" | grep -o 'PartitionCount: [0-9]*' | cut -d' ' -f2)" 3
check "the share group starts at the beginning of the topic (group setting)" \
    positive "$(group_config 'share.auto.offset.reset=earliest')"
LOCK=$(lock_ms)
[[ -n "$LOCK" ]] || fail_loudly "cannot read share.record.lock.duration.ms from the group"

if [[ "$FIRST_MODE" == share ]]; then
    check "4 workers have joined the share group and hold a stable assignment before any job is published" poll 120 members_stable
else
    check "4 workers have joined the consumer group before any job is published" poll 90 members_are group 4
fi

seed
echo "Seeded $JOBS jobs; waiting for deliveries"
poll 120 finished "$FIRST_MODE" || true

echo "Share group relations (jobs delivered in $FIRST_MODE mode)"
M=share
check "every job reached a final outcome" finished "$M"
check "29 jobs were sent, once each" expect "$(count $M "outcome='sent'") $(sql "SELECT count(DISTINCT order_id) FROM notifications.delivery_log WHERE mode='$M' AND outcome='sent'")" "29 29"
check "30 jobs produced 31 attempts: only the retry job has a second one" expect "$(count $M)" 31
check "precondition: the slow job is the first record of its partition and at least 5 later jobs share the partition" \
    expect "$(slow_is_first $M)/$([[ "$(later_count $M)" -ge 5 ]] && echo yes)" "1/yes"
check "precondition: the slow job took at least GATEWAY_SLOW_MS" at_least "$(slow_ms $M || echo 0)" "$GATEWAY_SLOW_MS"
check "a later job of the slow job's partition finished before the slow job" positive "$(overtaking_count $M)"
check "at least 3 of the 4 workers delivered jobs" at_least "$(workers_used $M)" 3
check "at least one partition was delivered by 2 or more different workers" at_least "$(max_workers_per_partition $M)" 2
check "the retry job failed once, then was sent on delivery 2" \
    expect "$(outcomes_of $M O-7005)/$(counts_of $M O-7005)" "failed,sent/1,2"
check "the bounce job was rejected on delivery 1" expect "$(outcomes_of $M O-7006)/$(counts_of $M O-7006)" "rejected/1"
BOUNCE_AGE=$(sql "SELECT floor(extract(epoch FROM (now() - max(finished_at)))) FROM notifications.delivery_log WHERE mode='$M' AND order_id='O-7006'" || echo 0)
WAIT=$(( LOCK / 1000 + 5 - ${BOUNCE_AGE:-0} ))
(( WAIT > 0 )) && sleep "$WAIT"
check "the rejected job is not delivered again after the lock duration" expect "$(count $M "order_id='O-7006'")" 1
check "kafka-share-groups shows 4 members" expect "$(share_members)" 4
check "Tempo has a trace for notification-service" poll 90 tempo_has_trace notification-service

echo "Consumer group run (control)"
export DELIVERY_MODE=group
# The delivery log lives in PostgreSQL; only the workers are replaced, so the share rows stay for comparison.
"${COMPOSE[@]}" up -d --no-deps --force-recreate --wait worker-1 worker-2 worker-3 worker-4
M=group
check "4 workers have joined the consumer group" poll 90 members_are group 4
poll 90 group_assigned || true
sleep 5
echo "Seeding the same $JOBS jobs for the consumer group"
seed
poll 180 finished "$M" || true
check "every job reached a final outcome" finished "$M"
check "29 jobs were sent, once each" expect "$(count $M "outcome='sent'") $(sql "SELECT count(DISTINCT order_id) FROM notifications.delivery_log WHERE mode='$M' AND outcome='sent'")" "29 29"
check "the retry job failed once, then was sent on attempt 2" \
    expect "$(outcomes_of $M O-7005)/$(counts_of $M O-7005)" "failed,sent/1,2"
check "the bounce job was rejected once and not retried" expect "$(outcomes_of $M O-7006)" rejected
check "precondition: the slow job is the first record of its partition and at least 5 later jobs share the partition" \
    expect "$(slow_is_first $M)/$([[ "$(later_count $M)" -ge 5 ]] && echo yes)" "1/yes"
check "precondition: the slow job took at least GATEWAY_SLOW_MS" at_least "$(slow_ms $M || echo 0)" "$GATEWAY_SLOW_MS"
check "control: no later job of the slow job's partition finished before the slow job" zero "$(overtaking_count $M)"
check "control: each partition was delivered by one worker" expect "$(max_workers_per_partition $M)" 1
check "control: the fourth worker owns no partition, so exactly 3 workers delivered" expect "$(workers_used $M)" 3

echo "$PASSED/$TOTAL checks passed"
[[ "$PASSED" == "$TOTAL" ]]
