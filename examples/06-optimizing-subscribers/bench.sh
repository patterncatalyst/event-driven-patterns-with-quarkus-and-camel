#!/usr/bin/env bash
# Measures how long the notification service takes to drain a seeded backlog under different
# consumer settings. Every profile drains the same backlog, once per repeat, each time in a fresh
# consumer group, interleaved so a slow minute on the host affects every profile alike. Writes
# results/bench-<date>.csv (scrubbed) and prints the median and range per profile. verify.sh
# asserts relations; this script reports numbers.
#
#   REPEATS=5 BENCH_N=1000 NOTIFY_WORK_MS=5 ./bench.sh
#   PROFILES="consumers-12-roundrobin" ./bench.sh     a subset of the profiles
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-06

COMPOSE=(docker compose -f compose.yaml --profile '*')
REPEATS="${REPEATS:-5}"
PER_TOPIC="${BENCH_N:-1000}"
WORK_MS="${NOTIFY_WORK_MS:-5}"
PROFILES="${PROFILES:-consumers-1 consumers-3 consumers-9 consumers-12 consumers-12-roundrobin poll-50}"
BACKLOG=$((PER_TOPIC * 3))
DATE="$(date +%Y-%m-%d)"
OUT="results/bench-$DATE.csv"

cleanup() { "${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true; }
trap cleanup EXIT

poll() { local end=$((SECONDS + $1)); shift; until "$@"; do (( SECONDS < end )) || return 1; sleep 1; done; }
sql() { docker exec -e PGPASSWORD=edp edp-postgres psql -h 127.0.0.1 -U edp -d edp -tA -c "$1"; }
rows_at_least() { (( $(sql "SELECT count(*) FROM notifications.notification WHERE group_id='$1'") >= $2 )); }
start_app() { env "$@" "${COMPOSE[@]}" up -d --no-deps --force-recreate --wait notification-service >/dev/null; }

drain_seconds() { sql "SELECT round(extract(epoch FROM max(processed_at) - min(processed_at))::numeric, 2) FROM notifications.notification WHERE group_id='$1'"; }

run_profile() { # run_profile <name> <repeat> <settings...>
    local group="b-$1-$2-$(date +%s)"
    start_app NOTIFY_GROUP="$group" NOTIFY_WORK_MS="$WORK_MS" "${@:3}"
    poll 900 rows_at_least "$group" "$BACKLOG"
    printf '%s,%s,%s,%s,%s\n' "$DATE" "$1" "$2" "$(sql "SELECT count(*) FROM notifications.notification WHERE group_id='$group'")" \
        "$(drain_seconds "$group")" >> "$OUT"
    echo "  $1 repeat $2 done"
}

settings_of() {
    case "$1" in
        consumers-1) echo CONSUMERS_COUNT=1 ;;
        consumers-3) echo CONSUMERS_COUNT=3 ;;
        consumers-9) echo CONSUMERS_COUNT=9 ;;
        consumers-12) echo CONSUMERS_COUNT=12 ;;
        consumers-12-roundrobin) echo CONSUMERS_COUNT=12 PARTITION_ASSIGNOR=org.apache.kafka.clients.consumer.RoundRobinAssignor ;;
        poll-50) echo CONSUMERS_COUNT=3 MAX_POLL_RECORDS=50 ;;
    esac
}

"${COMPOSE[@]}" up -d --build --wait kafka postgres lgtm
"${COMPOSE[@]}" build notification-service >/dev/null
SEED_RECORDS="$PER_TOPIC" "${COMPOSE[@]}" run --rm seeder >/dev/null 2>&1

mkdir -p results
{
    echo "# bench.sh $DATE: drain of a seeded backlog of $BACKLOG records (3 topics x 3 partitions x $PER_TOPIC per topic) by the notification service"
    echo "# settings: NOTIFY_WORK_MS=$WORK_MS per record, one broker, one PostgreSQL, one fresh consumer group per run, $REPEATS repeats interleaved"
    echo "# host class: $(nproc)-thread laptop CPU, Docker Engine, Kafka and PostgreSQL in containers on the same host"
    echo "# drain_s: last minus first row time"
    echo "date,profile,repeat,rows,drain_s"
} > "$OUT"

for rep in $(seq 1 "$REPEATS"); do
    echo "Repeat $rep"
    for profile in $PROFILES; do
        # shellcheck disable=SC2046
        run_profile "$profile" "$rep" $(settings_of "$profile")
    done
done

python3 ../../scripts/host_ids.py scrub results >/dev/null
python3 - "$OUT" <<'PY'
import statistics, sys
rows = [l.strip().split(',') for l in open(sys.argv[1]) if not l.startswith('#')][1:]
by = {}
for date, profile, rep, n, drain in rows:
    by.setdefault(profile, []).append(float(drain))
print(f"{'profile':26} drain_s median (min-max)")
for p, d in by.items():
    print(f"{p:26} {statistics.median(d):7.2f} ({min(d):.2f}-{max(d):.2f})")
PY
echo "wrote $OUT"
