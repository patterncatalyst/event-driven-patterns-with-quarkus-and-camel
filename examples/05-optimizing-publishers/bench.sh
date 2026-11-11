#!/usr/bin/env bash
# Measures the three publish routes, then the tuned route with other linger and codec settings.
# Repeated, interleaved rounds; writes results/bench-<date>.csv (every round) and
# results/bench-<date>-summary.csv (median, min and max), scrubbed of host identifiers.
# Run it alone: nothing else on the machine, no other stack. REPEATS=5 N=20000 CONCURRENCY=200 by default.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=../../scripts/stack-guard.sh
source ../../scripts/stack-guard.sh edp-05

COMPOSE=(docker compose -f compose.yaml)
APP=http://localhost:8080
TOPIC=edp.orders.placed
N=${N:-20000}
CONCURRENCY=${CONCURRENCY:-200}
REPEATS=${REPEATS:-5}
DATE=$(date +%F)
OUT=results/bench-$DATE.csv
SUMMARY=results/bench-$DATE-summary.csv
source ./lib.sh

trap '"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true' EXIT
mkdir -p results

"${COMPOSE[@]}" up -d --build --wait
LOAD_BEFORE=$(cut -d' ' -f1-3 /proc/loadavg)
OTHERS=$(docker ps --format '{{.Names}}' | grep -vc '^edp-' || true)

{
    echo "# orders per round: $N; callers in flight: $CONCURRENCY; rounds: $REPEATS; partitions: 3; brokers: 1; replication factor 1"
    echo "# host class: $(nproc)-thread CPU; load average before: $LOAD_BEFORE; containers outside this stack: $OTHERS"
    echo "# warm-up round per variant discarded; profiles interleaved within each round; tuned route: linger_ms, batch_size, codec per variant column"
    echo "variant,round,profile,orders_per_s,p50_ms,p99_ms,records_per_request,bytes_per_record"
} > "$OUT"

round() { # round <variant> <round number> <profile>
    burst "$3" "$N" "$CONCURRENCY"
    [[ "$DELTA_SENT" == "$N" ]] || { echo "round $1 $2 $3 is not valid: counted $DELTA_SENT of $N" >&2; exit 1; }
    echo "$1,$2,$3,$(field ordersPerSecond),$(field p50Ms),$(field p99Ms),$(ratio "$DELTA_SENT" "$DELTA_REQUESTS"),$(ratio "$DELTA_BYTES" "$DELTA_SENT")" >> "$OUT"
}

echo "Variant: profiles"
for p in unbatched default tuned; do burst "$p" 1000 50 >/dev/null; done
for r in $(seq 1 "$REPEATS"); do
    for p in unbatched default tuned; do round profiles "$r" "$p"; done
done

variant() { # variant <name> <linger ms> <codec>
    echo "Variant: $1"
    PUBLISHER_LINGER_MS=$2 PUBLISHER_COMPRESSION=$3 "${COMPOSE[@]}" up -d --no-deps --force-recreate --wait order-service
    burst tuned 1000 50 >/dev/null
    for r in $(seq 1 "$REPEATS"); do round "$1" "$r" tuned; done
}
for l in 0 5 20 100; do variant "linger-$l" "$l" lz4; done
for c in none gzip lz4 zstd; do variant "codec-$c" 20 "$c"; done

LOAD_AFTER=$(cut -d' ' -f1-3 /proc/loadavg)
echo "# load average after: $LOAD_AFTER" >> "$OUT"

python3 - "$OUT" "$SUMMARY" <<'PY'
import csv, statistics, sys
rows = [r for r in csv.reader(l for l in open(sys.argv[1]) if not l.startswith("#"))]
head, data = rows[0], rows[1:]
groups = {}
for r in data:
    groups.setdefault((r[0], r[2]), []).append([float(x) for x in r[3:]])
with open(sys.argv[2], "w", newline="") as f:
    w = csv.writer(f)
    w.writerow(["variant", "profile"] + [f"{h}_{s}" for h in head[3:] for s in ("median", "min", "max")])
    for (v, p), vals in groups.items():
        row = [v, p]
        for col in zip(*vals):
            row += [f"{statistics.median(col):.2f}", f"{min(col):.2f}", f"{max(col):.2f}"]
        w.writerow(row)
PY
python3 ../../scripts/host_ids.py scrub results
column -s, -t "$SUMMARY"
