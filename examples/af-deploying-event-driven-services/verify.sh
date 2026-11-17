#!/usr/bin/env bash
# Deploys the chapter 06 notification-service and the chapter 02 order-service unchanged and changes them four ways
# under a producer Job that sends uniquely numbered records at a fixed rate: a stop and restart, a rolling update of the
# consumer group, a rolling update of the order-service under HTTP requests, a breaking and a compatible schema
# change, and a blue-green cut-over with a rollback. Each scenario checks that nothing is lost and states what is
# lost or repeated. Every group-level check filters by the scenario's id range, because a new group replays the
# whole topic.
# GROUP_PROTOCOL=classic and OFFSET_COPY=off (demo code) are the switches described in the README.
# KEEP=1 leaves the namespaces in place afterwards.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
export APP_PORTS=30080   # only the order-service has a NodePort; the notification-service has none
# shellcheck source=../_k8s/lib.sh
source ../_k8s/lib.sh
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"; k8s_teardown' EXIT

GROUP_PROTOCOL="${GROUP_PROTOCOL:-consumer}"   # rolling update only; classic (demo code) runs it on the classic protocol
OFFSET_COPY="${OFFSET_COPY:-on}"               # off (demo code): green and blue start without the copied offsets
[[ "$GROUP_PROTOCOL" == consumer || "$GROUP_PROTOCOL" == classic ]] || { echo "GROUP_PROTOCOL is consumer or classic" >&2; exit 2; }
[[ "$OFFSET_COPY" == on || "$OFFSET_COPY" == off ]] || { echo "OFFSET_COPY is on or off" >&2; exit 2; }
[[ "$GROUP_PROTOCOL" == classic ]] && echo "demo code: the rolling update runs on the classic group protocol"
[[ "$OFFSET_COPY" == off ]] && echo "demo code: the blue-green cut-over and rollback copy no offsets"

COMMIT_S=5             # Camel's auto-commit interval for the Kafka consumer, left at its default
SLACK_S=2              # the client commits inside poll(), after the batch in hand: allow two seconds on top of the interval
TOPIC=edp.orders.placed
RATE=100

k8s_fresh .

# --- helpers
sql() { psql_exec edp "$1"; }
act() { "$@" >/dev/null || echo "WARN: $*" >&2; }   # an action between checks; its effect is judged by the next check
ge() { [[ "$1" =~ ^[0-9.]+$ ]] && awk -v a="$1" -v b="$2" 'BEGIN { exit !(a + 0 >= b + 0) }'; }
le() { [[ "$1" =~ ^[0-9.]+$ ]] && awk -v a="$1" -v b="$2" 'BEGIN { exit !(a + 0 <= b + 0) }'; }
field() { awk -v k="$1=" '{ for (i = 1; i <= NF; i++) if (index($i, k) == 1) print substr($i, length(k) + 1) }' <<<"$2"; }

produce() { # produce <job> <topic> <rate> <secs> <first-id-number> <kind>
    sed -e "s/__NAME__/$1/" -e "s/__TOPIC__/$2/" -e "s/__RATE__/$3/" -e "s/__SECS__/$4/" -e "s/__FIRST__/$5/" -e "s/__KIND__/$6/" \
        tools/producer-job.yaml | kc apply --server-side --field-manager=edp-verify -f - >/dev/null
}
wait_job() { kc -n "$NS_APPS" wait --for=condition=complete "job/$1" --timeout=180s >/dev/null; }
rollout() { kc -n "$NS_APPS" rollout status "deploy/$1" --timeout=240s >/dev/null; }

# Rows of the notification table (chapter 06): one per group, topic, partition and offset, attempts counted.
RANGE="substring(order_id from 3)::int BETWEEN"
rows() { sql "SELECT count(DISTINCT order_id) FROM notifications.notification WHERE group_id='$1' AND $RANGE $2 AND $3"; }
rows_ge() { [[ "$(rows "$1" "$2" "$3" 2>/dev/null || echo 0)" -ge "$4" ]]; }   # rows_ge <group> <lo> <hi> <n>
rows_eq() { [[ "$(rows "$1" "$2" "$3" 2>/dev/null || echo -1)" == "$4" ]]; }
repeated() { sql "SELECT coalesce(sum(attempts - 1), 0) FROM notifications.notification WHERE group_id='$1' AND $RANGE $2 AND $3"; }
union_ids() { sql "SELECT count(DISTINCT order_id) FROM notifications.notification WHERE group_id IN ('$1','$2') AND $RANGE $3 AND $4"; }
union_eq() { [[ "$(union_ids "$1" "$2" "$3" "$4" 2>/dev/null || echo -1)" == "$5" ]]; }
both_ids() { sql "SELECT count(*) FROM (SELECT order_id FROM notifications.notification WHERE group_id IN ('$1','$2') AND $RANGE $3 AND $4 GROUP BY order_id HAVING count(DISTINCT group_id) = 2) s"; }
longest_gap() { sql "SELECT coalesce(round(max(extract(epoch FROM g))::numeric, 1), 0) FROM (SELECT processed_at - lag(processed_at) OVER (ORDER BY processed_at) AS g FROM notifications.notification WHERE group_id='$1' AND $RANGE $2 AND $3) s"; }
# audit.write_log is a verify.sh instrument: a trigger on the notification table records every insert and update, so
# the replica that wrote a record the first time stays visible after a repeat has overwritten the row.
install_audit() {
    [[ "$(sql "SELECT to_regclass('notifications.notification') IS NOT NULL")" == t ]] || return 1
    sql "CREATE SCHEMA IF NOT EXISTS audit;
         CREATE TABLE IF NOT EXISTS audit.write_log (group_id text, kafka_partition int, kafka_offset bigint, order_id text, replica text, written_at timestamptz DEFAULT clock_timestamp());
         CREATE OR REPLACE FUNCTION audit.log_write() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER AS \$f\$
           BEGIN INSERT INTO audit.write_log (group_id, kafka_partition, kafka_offset, order_id, replica) VALUES (NEW.group_id, NEW.kafka_partition, NEW.kafka_offset, NEW.order_id, NEW.replica); RETURN NEW; END \$f\$;
         DROP TRIGGER IF EXISTS write_log ON notifications.notification;
         CREATE TRIGGER write_log AFTER INSERT OR UPDATE ON notifications.notification FOR EACH ROW EXECUTE FUNCTION audit.log_write();" >/dev/null
}
# budget <group> <lo> <hi> <keep-final-owner: yes|no>: the most records a replica can have processed after its last commit
# on a partition: the writes it made in the last COMMIT_S+SLACK_S seconds before its last write there, summed over the
# replicas that gave the partition up (the replica that wrote last on a partition still holds it when keep-final is no).
budget() {
    sql "WITH w AS (SELECT kafka_partition p, replica r, written_at t FROM audit.write_log WHERE group_id='$1' AND $RANGE $2 AND $3),
              l AS (SELECT p, r, max(t) lt FROM w GROUP BY p, r),
              f AS (SELECT DISTINCT ON (p) p, r FROM w ORDER BY p, t DESC)
         SELECT coalesce(sum((SELECT count(*) FROM w WHERE w.p = l.p AND w.r = l.r AND w.t > l.lt - interval '$((COMMIT_S + SLACK_S)) seconds')), 0)
         FROM l WHERE '$4' = 'yes' OR (l.p, l.r) NOT IN (SELECT p, r FROM f)"
}
total_rows() { sql "SELECT count(*) FROM notifications.notification WHERE group_id='$1'"; }

settled() { # settled <bound seconds> <group>: the group's row count is positive and unchanged for 8 polls
    local end=$((SECONDS + $1)) prev=-1 stable=0 cur
    while (( SECONDS < end )); do
        cur="$(total_rows "$2" 2>/dev/null || echo -1)"
        if [[ "$cur" == "$prev" && "$cur" -gt 0 ]]; then stable=$((stable + 1)); else stable=0; fi
        (( stable >= 8 )) && return 0
        prev="$cur"; sleep 1
    done
    return 1
}

# Consumer groups (kafka-consumer-groups.sh in the broker pod; group_empty and group_offsets_copy are in lib.sh)
group_lag() { # the records the group has not consumed; fails when the group lists fewer than three partitions
    local out; out="$(kafka_exec kafka-consumer-groups.sh --describe --group "$1" 2>/dev/null)" || return 1
    awk -v g="$1" '$1 == g && $3 ~ /^[0-9]+$/ { n++; if ($6 ~ /^[0-9]+$/) lag += $6 } END { if (n < 3) exit 1; print lag + 0 }' <<<"$out"
}
lag_zero() { [[ "$(group_lag "$1" || true)" == 0 ]]; }
group_type() { # group_type <group> <classic|consumer>: the broker lists the group under that protocol
    local out; out="$(kafka_exec kafka-consumer-groups.sh --list --type "$2" 2>/dev/null)" || return 1
    awk -v g="$1" -v t="$2" '$1 == g && tolower($2) == t { found = 1 } END { exit !found }' <<<"$out"   # a table: GROUP, TYPE
}
export_offsets() { kafka_exec kafka-consumer-groups.sh --group "$1" --reset-offsets --to-current --all-topics --dry-run --export 2>/dev/null | sort; }
same_offsets() { [[ -n "$1" && "$1" == "$2" ]]; }

topics_ok() {
    local t out
    for t in edp.orders.placed edp.payments.processed edp.shipping.scheduled; do
        out="$(kafka_exec kafka-topics.sh --describe --topic "$t" 2>/dev/null)" || return 1
        [[ "$out" == *"PartitionCount: 3"* ]] || return 1
    done
}
images_unchanged() { # the node runs the images built from the chapter 06 and chapter 02 Containerfiles
    local ref
    for ref in localhost/edp/06-notification-service:local localhost/edp/02-order-service:local; do
        [[ -n "$(_host_image_id "$ref")" && "$(_host_image_id "$ref")" == "$(_node_image_id "$ref")" ]] || return 1
    done
}

# --- preconditions
for t in edp.orders.placed edp.payments.processed edp.shipping.scheduled; do
    kafka_exec kafka-topics.sh --create --if-not-exists --topic "$t" --partitions 3 --replication-factor 1 >/dev/null
done
precondition "Kafka edp is Ready" kafka_ready edp
precondition "the three input topics have three partitions" topics_ok
precondition "the node runs the notification-service and order-service images, unchanged" images_unchanged
poll 180 install_audit || { echo "the notification table did not appear" >&2; exit 1; }

# --- stop and restart: Recreate
echo "--- stop and restart"
LO=41000; HI=$((LO + RATE * 30 - 1))
produce prod-stop "$TOPIC" "$RATE" 30 "$LO" v1
poll 120 rows_ge notify-stop "$LO" "$HI" 800 || echo "WARN: notify-stop did not start reading" >&2
act kc -n "$NS_APPS" set env deploy/notify-blue REV=stop
rollout notify-blue
wait_job prod-stop
poll 120 rows_eq notify-stop "$LO" "$HI" 3000 || true
gap="$(longest_gap notify-stop "$LO" "$HI" || true)"; echo "measured: longest period with no row written $gap s"
check "the Recreate rollout leaves a period of at least 3 s with no row written" ge "$gap" 3
check "the group has no lag once it has caught up" poll 90 lag_zero notify-stop
miss="$(rows notify-stop "$LO" "$HI" || true)"; echo "measured: $miss of 3000 ids have a row"
check "every record sent through the stop has a row" expect "$miss" 3000
rep="$(repeated notify-stop "$LO" "$HI" || true)"; echo "measured: $rep records delivered again"
bound="$(budget notify-stop "$LO" "$HI" no || true)"; echo "measured: bound from the old replicas' last commit interval $bound"
check "the records delivered again stay within what the old replicas wrote in their last commit interval" le "$rep" "$bound"

# --- rolling update of the consumer group
echo "--- rolling update of the consumer group ($GROUP_PROTOCOL protocol)"
LO=44000; HI=$((LO + RATE * 30 - 1))
act kc -n "$NS_APPS" patch deploy notify-blue --type=merge \
    -p '{"spec":{"strategy":{"type":"RollingUpdate","rollingUpdate":{"maxSurge":1,"maxUnavailable":0}}}}'
act kc -n "$NS_APPS" set env deploy/notify-blue GROUP_PROTOCOL="$GROUP_PROTOCOL" NOTIFY_GROUP=notify-roll REV=roll0
rollout notify-blue
settled 120 notify-roll || echo "WARN: notify-roll did not finish replaying the topic" >&2   # a new group replays it
OLD_PODS="$(kc -n "$NS_APPS" get pods -l color=blue -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}')"
OLD_LIST="$(sed "s/.*/'&'/" <<<"$OLD_PODS" | paste -sd, -)"
produce prod-roll "$TOPIC" "$RATE" 30 "$LO" v1
poll 120 rows_ge notify-roll "$LO" "$HI" 800 || echo "WARN: notify-roll did not start reading" >&2
act kc -n "$NS_APPS" set env deploy/notify-blue REV=roll1
rollout notify-blue
wait_job prod-roll
poll 120 rows_eq notify-roll "$LO" "$HI" 3000 || true
old_gone() { local p; for p in $OLD_PODS; do ! kc -n "$NS_APPS" get pod "$p" >/dev/null 2>&1 || return 1; done; }
both_revisions() {
    [[ "$(sql "SELECT (count(*) FILTER (WHERE replica IN ($OLD_LIST)) > 0 AND count(*) FILTER (WHERE replica NOT IN ($OLD_LIST)) > 0) FROM notifications.notification WHERE group_id='notify-roll' AND $RANGE $LO AND $HI")" == t ]]
}
check "the group runs the consumer protocol" group_type notify-roll consumer
check "the rolling update replaced every pod" poll 60 old_gone
check "rows of the range were written by the old pods and by the new ones" both_revisions
miss="$(rows notify-roll "$LO" "$HI" || true)"; echo "measured: $miss of 3000 ids have a row"
check "every record sent through the rolling update has a row" expect "$miss" 3000
rep="$(repeated notify-roll "$LO" "$HI" || true)"; echo "measured: $rep records delivered again"
bound="$(budget notify-roll "$LO" "$HI" no || true)"; echo "measured: bound from the old replicas' last commit interval $bound"
check "the records delivered again stay within what the old replicas wrote in their last commit interval" le "$rep" "$bound"

# --- a breaking schema change, then a compatible one
echo "--- schema change"
act kc -n "$NS_APPS" set env deploy/notify-blue GROUP_PROTOCOL=classic NOTIFY_GROUP=notify-schema REV=schema
rollout notify-blue
settled 120 notify-schema || echo "WARN: notify-schema did not finish replaying the topic" >&2
SINCE="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
batch() { produce "$1" "$TOPIC" 10 2 "$2" "$3"; wait_job "$1"; poll 60 lag_zero notify-schema || true; }
failure_logged() { local out; out="$(kc -n "$NS_APPS" logs -l app=notify --tail=-1 --since-time="$SINCE" 2>/dev/null || true)"; [[ "$out" == *"Failed delivery"* ]]; }
batch prod-v2 50000 v2
check "no record with the renamed field produced a row" rows_eq notify-schema 50000 50019 0
check "the group committed past the records it could not store" lag_zero notify-schema
check "the failure is in the consumer log and nowhere else" failure_logged
batch prod-v1 50020 v1
check "the next records in the old shape are all stored" poll 30 rows_eq notify-schema 50020 50039 20
batch prod-expand 50040 expand
check "records carrying the old and the new field are all stored" poll 30 rows_eq notify-schema 50040 50059 20

# --- blue-green: cut-over and rollback
echo "--- blue-green"
LO=51000; HI=$((LO + RATE * 40 - 1))
act kc -n "$NS_APPS" scale deploy/notify-blue --replicas=0
kc -n "$NS_APPS" wait --for=delete pod -l color=blue --timeout=120s >/dev/null 2>&1 || true
poll 60 group_empty notify-schema || echo "WARN: notify-schema is not empty" >&2
act group_offsets_copy notify-schema notify-blue    # blue starts where the previous group ended, not at the start
act kc -n "$NS_APPS" set env deploy/notify-blue GROUP_PROTOCOL=classic NOTIFY_GROUP=notify-blue REV=bg
act kc -n "$NS_APPS" scale deploy/notify-blue --replicas=3
rollout notify-blue
produce prod-bg "$TOPIC" "$RATE" 40 "$LO" v1
poll 120 rows_ge notify-blue "$LO" "$HI" 1500 || echo "WARN: blue did not start reading" >&2
T0=$SECONDS
act kc -n "$NS_APPS" scale deploy/notify-blue --replicas=0
check "the blue group is empty within 60 s of the scale-down" poll 60 group_empty notify-blue
blue_off="$(export_offsets notify-blue || true)"
[[ "$OFFSET_COPY" == on ]] && act group_offsets_copy notify-blue notify-green
green_off="$(export_offsets notify-green || true)"
act kc -n "$NS_APPS" scale deploy/notify-green --replicas=3
rollout notify-green
echo "measured: green is live $((SECONDS - T0)) s after the blue scale-down"
check "the green group holds blue's committed offsets, partition by partition" same_offsets "$green_off" "$blue_off"
wait_job prod-bg
poll 240 union_eq notify-blue notify-green "$LO" "$HI" 4000 || true
settled 300 notify-green || echo "WARN: green did not settle" >&2
u="$(union_ids notify-blue notify-green "$LO" "$HI" || true)"; echo "measured: $u of 4000 ids have a row in blue or green"
check "every record sent through the cut-over has a row in blue or green" expect "$u" 4000
b="$(both_ids notify-blue notify-green "$LO" "$HI" || true)"; echo "measured: $b ids were processed by both groups"
bound="$(budget notify-blue "$LO" "$HI" yes || true)"; echo "measured: bound from blue's last commit interval $bound"
check "the ids processed by both groups stay within what blue wrote in its last commit interval" le "$b" "$bound"

LO=55000; HI=$((LO + RATE * 30 - 1))
produce prod-rb "$TOPIC" "$RATE" 30 "$LO" v1
poll 90 rows_ge notify-green "$LO" "$HI" 1000 || echo "WARN: green did not start reading" >&2
act kc -n "$NS_APPS" scale deploy/notify-green --replicas=0
check "the green group is empty within 60 s of the scale-down" poll 60 group_empty notify-green
green_off="$(export_offsets notify-green || true)"
[[ "$OFFSET_COPY" == on ]] && act group_offsets_copy notify-green notify-blue
blue_off="$(export_offsets notify-blue || true)"
act kc -n "$NS_APPS" scale deploy/notify-blue --replicas=3
rollout notify-blue
check "the blue group holds green's committed offsets, partition by partition" same_offsets "$blue_off" "$green_off"
wait_job prod-rb
poll 240 union_eq notify-blue notify-green "$LO" "$HI" 3000 || true
settled 300 notify-blue || echo "WARN: blue did not settle" >&2
u="$(union_ids notify-blue notify-green "$LO" "$HI" || true)"; echo "measured: $u of 3000 ids have a row in blue or green"
check "every record sent through the rollback has a row in blue or green" expect "$u" 3000
b="$(both_ids notify-blue notify-green "$LO" "$HI" || true)"; echo "measured: $b ids were processed by both groups"
bound="$(budget notify-green "$LO" "$HI" yes || true)"; echo "measured: bound from green's last commit interval $bound"
check "the ids processed by both groups stay within what green wrote in its last commit interval" le "$b" "$bound"

# --- rolling update of the order-service under HTTP requests
echo "--- rolling update of the order-service"
act kc -n "$NS_APPS" scale deploy/notify-blue deploy/notify-green --replicas=0   # 02's events have no eventType: 06 cannot read them
kc -n "$NS_APPS" wait --for=delete pod -l app=notify --timeout=120s >/dev/null 2>&1 || true
python3 -I tools/http.py load 60000 20 "$TMP/control.txt"
control="$(python3 -I tools/http.py report "$TMP/control.txt")"; echo "measured: control $control"
check "without a rollout every request is answered 201" expect "$(field failed "$control")" 0
python3 -I tools/http.py load 62000 40 "$TMP/roll.txt" "$TMP/flowing" &
LOAD_PID=$!
poll 30 test -e "$TMP/flowing"   # the rollout starts once the first request has been answered; a check below fails when it did not run while requests flowed
R0="$(date +%s)"
act kc -n "$NS_APPS" set env deploy/order-service REV=roll
rollout order-service
R1="$(date +%s)"
wait "$LOAD_PID"
roll="$(python3 -I tools/http.py report "$TMP/roll.txt")"; echo "measured: rolling $roll"
inside() { awk -v a="$R0" -v b="$R1" -v f="$(field first "$roll")" -v l="$(field last "$roll")" 'BEGIN { exit !(f < a && b < l) }'; }
check "the rollout ran while requests were flowing" inside
check "no request was answered with a 5xx status" expect "$(field status5xx "$roll")" 0
failed_below_2_percent() {
    local f r; f="$(field failed "$roll")"; r="$(field requests "$roll")"
    [[ "$r" =~ ^[0-9]+$ && "$f" =~ ^[0-9]+$ ]] && (( r > 0 && f * 100 < r * 2 ))
}
check "failed requests stay below 2 percent" failed_below_2_percent
retry="$(python3 -I tools/http.py post O-60000 || true)"; echo "measured: retry of accepted O-60000 answered $retry"
python3 -I tools/http.py retry "$TMP/retry.txt" "$TMP/control.txt" "$TMP/roll.txt"
echo "measured: retries $(awk '{print $3}' "$TMP/retry.txt" | sort | uniq -c | tr -s ' ' | tr '\n' ' ')"
once="$(kafka_exec kafka-console-consumer.sh --topic "$TOPIC" --from-beginning --timeout-ms 10000 --property print.key=true 2>/dev/null || true)"
once="$(python3 -I tools/http.py once "$TMP/retry.txt" "$TMP/control.txt" "$TMP/roll.txt" <<<"$once")"; echo "measured: $once"
check "a retry of an accepted order is answered 409, and after every failed request is retried each order has exactly one event" \
    expect "$retry ${once% accepted_before_retry=*}" "409 missing=0 duplicate=0 unresolved=0"

summary
