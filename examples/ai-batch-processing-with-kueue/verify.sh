#!/usr/bin/env bash
# Installs Kueue v0.20.6 on the Part 6 minikube profile, runs end-of-day delivery reconciliations through the
# batch-runner (one Kubernetes Job per step, each admitted by Kueue) and checks the observable effect: the steps in
# order, the fan-out over the three partitions, the totals against the producer's own data, a retry of one index, a
# replay of the failed step only, a second run held back by the quota, a high priority run that preempts a low priority
# one, and the traces, logs, metrics and dashboard of all of it. The input is dataset.txt, read by the producer Job
# and by this script; every expected number is computed from it.
# Demo switches (demo code, passed to the runner as environment): QUEUE=off (no queue label), REPLAY_GUARD=off,
# PRIORITY=off (no priority label), FAIL_POLICY=off (no FailIndex rule). See the README for the failing set of each.
# KEEP=1 leaves Kueue and the namespaces in place afterwards.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
export APP_PORTS=30080   # only the batch-runner has a NodePort
# shellcheck source=../_k8s/lib.sh
source ../_k8s/lib.sh
TMP="$(mktemp -d)"

teardown() {
    rm -rf "$TMP"
    if [[ "${KEEP:-0}" != "1" ]]; then
        # No runner may create a Job while Kueue is being removed
        kc -n "$NS_APPS" scale deploy/batch-runner --replicas=0 >/dev/null 2>&1 || true
        kc -n "$NS_APPS" wait --for=delete pod -l app=batch-runner --timeout=90s >/dev/null 2>&1 || true
        kueue_teardown || echo "teardown: Kueue was not removed cleanly" >&2
    fi
    k8s_teardown
}
trap teardown EXIT

QUEUE="${QUEUE:-on}"; REPLAY_GUARD="${REPLAY_GUARD:-on}"; PRIORITY="${PRIORITY:-on}"; FAIL_POLICY="${FAIL_POLICY:-on}"
for v in QUEUE REPLAY_GUARD PRIORITY FAIL_POLICY; do
    [[ "${!v}" == on || "${!v}" == off ]] || { echo "$v is on or off" >&2; exit 2; }
    [[ "${!v}" == off ]] && echo "demo code: $v=off"
done

DATA=tools/dataset.txt
RUNNER="http://127.0.0.1:30080"
LOKI="http://127.0.0.1:30100"
BC() { python3 -I tools/batchcheck.py "$@"; }
sql() { psql_exec edp "$1"; }
R=reconciliation   # the schema of the runner's tables

# --- install: platform, Kueue, queues, switches
k8s_fresh .
kueue_fresh
for n in 1 2 3 4 5; do
    kc apply --server-side --field-manager=edp-verify -k kueue >/dev/null && break
    echo "kueue objects: apply attempt $n failed; retrying" >&2; sleep 3
done
kc get clusterqueue reconciliation >/dev/null || { echo "the Kueue objects were not applied" >&2; exit 1; }

env_json=""
for v in QUEUE REPLAY_GUARD PRIORITY FAIL_POLICY; do
    [[ "${!v}" == off ]] && env_json+="{\"name\":\"$v\",\"value\":\"off\"},"
done
if [[ -n "$env_json" ]]; then
    kc -n "$NS_APPS" patch deploy/batch-runner --type=strategic \
        -p "{\"spec\":{\"template\":{\"spec\":{\"containers\":[{\"name\":\"app\",\"env\":[${env_json%,}]}]}}}}" >/dev/null
    kc -n "$NS_APPS" rollout status deploy/batch-runner --timeout=240s >/dev/null || { echo "the runner did not roll out" >&2; exit 1; }
fi
poll 120 ready 30080 || { echo "the batch-runner does not answer on 30080" >&2; exit 1; }
kueue_scraped() { [[ "$(prom_query 'up{job="kueue"}')" == 1 ]]; }
poll 240 kueue_scraped || { echo "the collector does not scrape Kueue" >&2; exit 1; }

# --- helpers
ge() { [[ "$1" =~ ^[0-9.]+$ ]] && awk -v a="$1" -v b="$2" 'BEGIN { exit !(a + 0 >= b + 0) }'; }
metric_ge() { local v; v="$(prom_query "$1" 2>/dev/null)" || return 1; ge "$v" "$2"; }   # metric_ge <promql> <n>
metric_eq() { local v; v="$(prom_query "$1" 2>/dev/null)" || return 1; awk -v a="$v" -v b="$2" 'BEGIN { exit !(a + 0 == b + 0) }'; }
act() { "$@" >/dev/null || echo "WARN: $*" >&2; }

snap() { kc -n "$NS_APPS" get job,workload,pod -o json; }   # one JSON list of Jobs, Workloads and pods
job() { echo "rec-$1-$2-a${3:-1}"; }                         # job <run> <step> [attempt]
jobf() { kc -n "$NS_APPS" get job "$1" -o jsonpath="$2"; }
job_cond() { jobf "$1" "{.status.conditions[?(@.type==\"$2\")].status}"; }
job_complete() { [[ "$(job_cond "$1" Complete 2>/dev/null)" == True ]]; }
job_failed() { [[ "$(job_cond "$1" Failed 2>/dev/null)" == True ]]; }
idx_pods() { # idx_pods <job>: pods per completion index, "0:1 1:2 2:1"
    kc -n "$NS_APPS" get pods -l "job-name=$1" -o jsonpath='{range .items[*]}{.metadata.labels.batch\.kubernetes\.io/job-completion-index}{"\n"}{end}' \
        | sort | uniq -c | awk '{ printf "%s%s:%s", (NR > 1 ? " " : ""), $2, $1 } END { print "" }'
}
run_status() { sql "SELECT status FROM $R.run WHERE id='$1'"; }
run_in() { [[ "$(run_status "$1" 2>/dev/null || true)" == "$2" ]]; }             # run_in <run> <status>
run_over() { local s; s="$(run_status "$1" 2>/dev/null || true)"; [[ "$s" == DONE || "$s" == FAILED ]]; }
runs_over() { local r; for r in "$@"; do run_over "$r" || return 1; done; }
step_status() { sql "SELECT status FROM $R.step WHERE run_id='$1' AND name='$2'"; }
totals_db() { sql "SELECT carrier||'|'||scheduled||'|'||delivered||'|'||open FROM $R.carrier_total WHERE run_id='$1' ORDER BY carrier"; }
totals_ok() { [[ "$(totals_db "$1")" == "$(BC expect "$DATA" "$2")" ]]; }       # totals_ok <run> <window>
attempts() { sql "SELECT string_agg(\"partition\"||':'||attempts, ' ' ORDER BY \"partition\") FROM $R.run_partition WHERE run_id='$1'"; }
tid() { sql "SELECT traceparent FROM $R.run WHERE id='$1'" | awk -F- '{ print $2 }'; }
iso() { date -u -d "@$(( $1 / 1000 )).$(printf '%03d' $(( $1 % 1000 )))" +%Y-%m-%dT%H:%M:%S.%3NZ; }

declare -A FROM TO
post_run() { # post_run <W> [priority]: sets LAST_CODE and LAST_ID
    local body
    body="{\"from\":\"$(iso "${FROM[$1]}")\",\"to\":\"$(iso "${TO[$1]}")\"${2:+,\"priority\":\"$2\"}}"
    LAST_CODE="$(curl -s -o "$TMP/post.json" -w '%{http_code}' --max-time 20 -X POST -H 'Content-Type: application/json' --data "$body" "$RUNNER/runs")"
    LAST_ID="$(python3 -I -c 'import json, sys; print(json.load(open(sys.argv[1])).get("id", ""))' "$TMP/post.json" 2>/dev/null || true)"
}
faults() { kc -n "$NS_APPS" create configmap ai-demo-faults "$@" --dry-run=client -o yaml | kc apply --server-side --field-manager=edp-verify -f - >/dev/null; }
faults_clear() { kc -n "$NS_APPS" delete configmap ai-demo-faults --ignore-not-found >/dev/null; }
read_output() { kafka_exec kafka-console-consumer.sh --topic edp.shipping.reconciliations --from-beginning --timeout-ms 8000 --property print.key=true > "$TMP/out.txt" 2>/dev/null; }
output_ok() { read_output && [[ "$(BC output "$TMP/out.txt" "$1")" == "$(BC expect "$DATA" "$2")" ]]; }   # output_ok <run> <window>
output_count() { read_output; BC output "$TMP/out.txt" "$1" | wc -l; }

# --- preconditions: Kueue, the webhook, the queues
kueue_pinned() {
    local d=kueue-controller-manager
    [[ "$(kc -n "$KUEUE_NS" get deploy "$d" -o jsonpath='{.status.conditions[?(@.type=="Available")].status}')" == True ]] \
        && [[ "$(kc -n "$KUEUE_NS" get deploy "$d" -o jsonpath='{.spec.template.spec.containers[0].image}')" == *"${KUEUE_IMAGE##*@}" ]]
}
queues_active() {
    [[ "$(kc get clusterqueue reconciliation -o jsonpath='{.status.conditions[?(@.type=="Active")].status}')" == True ]] \
        && [[ "$(kc -n "$NS_APPS" get localqueue reconciliation -o jsonpath='{.status.conditions[?(@.type=="Active")].status}')" == True ]]
}
precondition "Kueue $KUEUE_VERSION is Available and the controller image is the pinned digest" kueue_pinned
precondition "the Kueue webhook suspends a Job that carries the queue label" _kueue_webhook_suspends
precondition "the ClusterQueue and the LocalQueue are Active" poll 60 queues_active

# --- the input: topics, then the producer Job, whose log names the boundary of every window
topics_ready() { kc -n "$NS_KAFKA" wait --for=condition=Ready kafkatopic --all --timeout=120s; }
topics_ready >/dev/null || { echo "the KafkaTopics are not Ready" >&2; exit 1; }
kc -n "$NS_APPS" create configmap ai-dataset --from-file=dataset.txt="$DATA" --dry-run=client -o yaml | kc apply --server-side --field-manager=edp-verify -f - >/dev/null
kc apply --server-side --field-manager=edp-verify -f tools/producer-job.yaml >/dev/null
kc -n "$NS_APPS" wait --for=condition=complete job/ai-producer --timeout=300s >/dev/null || { echo "the producer Job did not complete" >&2; kc -n "$NS_APPS" logs job/ai-producer >&2 || true; exit 1; }
while read -r _ w edge ms; do
    if [[ "$edge" == FROM ]]; then FROM[$w]="$ms"; else TO[$w]="$ms"; fi
done < <(kc -n "$NS_APPS" logs job/ai-producer | grep '^WINDOW ')
consume() { kafka_exec kafka-console-consumer.sh --topic "$1" --from-beginning --timeout-ms 10000 --property print.timestamp=true --property print.partition=true --property print.key=true > "$2" 2>/dev/null; }
consume edp.shipping.scheduled "$TMP/sched.txt"
consume edp.shipping.delivered "$TMP/deliv.txt"
parts_ok() { # three partitions, both topics place every key alike, and the records inside each window are the dataset's
    local topic w
    for topic in edp.shipping.scheduled edp.shipping.delivered; do
        [[ "$(kafka_exec kafka-topics.sh --describe --topic "$topic" 2>/dev/null)" == *"PartitionCount: 3"* ]] || return 1
    done
    BC copartition "$TMP/sched.txt" "$TMP/deliv.txt" || return 1
    for w in W1 W2 W3 W4 W5 W6 W7; do
        BC inwindow "$DATA" "$w" "${FROM[$w]}" "${TO[$w]}" "$TMP/sched.txt" "$TMP/deliv.txt" || return 1
    done
}
precondition "both input topics have 3 partitions, are keyed alike, and each window holds exactly the dataset's records" parts_ok
echo "measured: $(BC markers "$DATA") marker orders sit outside every window"

# --- run 1: the steps, the fan-out, the totals
echo "--- run 1"
post_run W1; R1="$LAST_ID"; c1="$LAST_CODE"
post_run W1; c2="$LAST_CODE"
check "POST /runs for window 1 answers 202 with an id, and the same window again answers 409" expect "$c1 $c2 ${R1:+id}" "202 409 id"
poll 420 run_over "$R1" || echo "WARN: run 1 did not finish" >&2
echo "measured: run 1 is $(run_status "$R1")"
J1="$(job "$R1" reconcile)"

steps_in_order() { kc -n "$NS_APPS" get job -l "edp.batch/run=$R1" -o json | BC joborder "$R1"; }
check "reconcile was created after plan completed, and aggregate after reconcile completed" steps_in_order

partition_offsets_ok() { # the run_partition offsets equal kafka-get-offsets.sh at the window boundaries (an omitted partition: the end offset)
    offs() { # offs <topic> <ms>: "p offset" per partition
        local out end
        out="$(kafka_exec kafka-get-offsets.sh --topic "$1" --time "$2" 2>/dev/null | grep -v WARN || true)"
        end="$(kafka_exec kafka-get-offsets.sh --topic "$1" --time -1 2>/dev/null | grep -v WARN)"
        awk -F: 'NR == FNR { e[$2] = $3; next } { o[$2] = $3 } END { for (p = 0; p < 3; p++) print p, (p in o ? o[p] : e[p]) }' <(echo "$end") <(echo "$out")
    }
    paste -d' ' <(offs edp.shipping.scheduled "${FROM[W1]}") <(offs edp.shipping.scheduled "${TO[W1]}") \
                <(offs edp.shipping.delivered "${FROM[W1]}") <(offs edp.shipping.delivered "${TO[W1]}") \
        | awk '{ print $1 "|" $2 "|" $4 "|" $6 "|" $8 }' > "$TMP/offsets.expected"
    sql "SELECT \"partition\"||'|'||scheduled_from||'|'||scheduled_to||'|'||delivered_from||'|'||delivered_to FROM $R.run_partition WHERE run_id='$R1' ORDER BY \"partition\"" > "$TMP/offsets.actual"
    [[ -s "$TMP/offsets.actual" ]] && cmp -s "$TMP/offsets.expected" "$TMP/offsets.actual"
}
check "3 run_partition rows hold the offsets kafka-get-offsets.sh returns for the window's start and end" partition_offsets_ok
workloads_ok() { kc -n "$NS_APPS" get job,workload -o json | BC jobworkloads "$R1" 3; }
check "every step Job carried the queue label and owns a Workload that was Admitted, then Finished" workloads_ok

partition_rows() { # partition|DONE|records_read per partition, against the dataset and the live topics
    BC partition-records "$DATA" W1 "$TMP/sched.txt" "$TMP/deliv.txt" | awk -F'|' '{ print $1 "|DONE|" $2 }' > "$TMP/rows.expected"
    sql "SELECT \"partition\"||'|'||status||'|'||records_read FROM $R.run_partition WHERE run_id='$1' ORDER BY \"partition\"" > "$TMP/rows.actual"
    cmp -s "$TMP/rows.expected" "$TMP/rows.actual"
}
fanout_ok() {
    [[ "$(jobf "$J1" '{.spec.completionMode} {.status.completedIndexes}')" == "Indexed 0-2" ]] \
        && [[ "$(idx_pods "$J1")" == "0:1 1:1 2:1" ]] && partition_rows "$R1"
}
check "reconcile ran as an Indexed Job with completedIndexes 0-2, one pod per index, and partition i read what the dataset puts in partition i" fanout_ok
markers_excluded() {
    local seen read_sum sched_sum want_sched
    seen="$(grep -cP '\tO-449\d\t' "$TMP/sched.txt" || true)"
    read_sum="$(sql "SELECT coalesce(sum(records_read), 0) FROM $R.run_partition WHERE run_id='$R1'")"
    sched_sum="$(sql "SELECT coalesce(sum(scheduled), 0) FROM $R.carrier_total WHERE run_id='$R1'")"
    want_sched="$(BC expect "$DATA" W1 | awk -F'|' '{ s += $2 } END { print s }')"
    (( seen >= 1 )) && [[ "$read_sum" == "$(BC records "$DATA" W1)" && "$sched_sum" == "$want_sched" ]]
}
check "the markers outside the window are on the topic and in no count" markers_excluded
check "carrier_total holds the dataset's scheduled, delivered and open counts for FASTLINE and AIRCO" totals_ok "$R1" W1
check "the output topic holds exactly 2 records for the run, equal to the totals" output_ok "$R1" W1

# --- run 2: one index fails once and is retried
echo "--- run 2: a transient failure of partition 1"
faults --from-literal=FAULT_PARTITION=1 --from-literal=FAULT_MODE=once
post_run W2; R2="$LAST_ID"
poll 420 run_over "$R2" || echo "WARN: run 2 did not finish" >&2
faults_clear
J2="$(job "$R2" reconcile)"
retry_ok() { job_complete "$J2" && [[ "$(idx_pods "$J2")" == "0:1 1:2 2:1" ]]; }
check "the reconcile Job completed, index 1 ran in 2 pods and indexes 0 and 2 in 1" retry_ok
attempts_ok() { [[ "$(attempts "$R2")" == "0:1 1:2 2:1" ]] && totals_ok "$R2" W2; }
check "partition 1 has 2 attempts and the others 1, and the totals are right" attempts_ok

# --- run 3: a poison record fails index 1, then a replay runs index 1 only
echo "--- run 3: a poison failure of partition 1, then a replay"
faults --from-literal=FAULT_PARTITION=1 --from-literal=FAULT_MODE=always
post_run W3; R3="$LAST_ID"
J3="$(job "$R3" reconcile 1)"
poll 420 job_failed "$J3" || echo "WARN: the reconcile Job of run 3 did not fail" >&2
poison_ok() {
    [[ "$(jobf "$J3" '{.status.failedIndexes}|{.status.completedIndexes}')" == "1|0,2" ]] && [[ "$(idx_pods "$J3")" == "0:1 1:1 2:1" ]]
}
check "the reconcile Job failed with failedIndexes 1 and completedIndexes 0,2, and index 1 ran in 1 pod" poison_ok
poll 120 run_in "$R3" FAILED || echo "WARN: run 3 is not FAILED" >&2
failed_clean() {
    [[ "$(step_status "$R3" reconcile)" == FAILED && "$(run_status "$R3")" == FAILED ]] \
        && [[ -z "$(kc -n "$NS_APPS" get job -l "edp.batch/run=$R3,edp.batch/step=aggregate" -o name)" ]] \
        && [[ "$(sql "SELECT count(*) FROM $R.carrier_total WHERE run_id='$R3'")" == 0 ]] \
        && [[ "$(output_count "$R3")" == 0 ]]
}
check "the step and the run are FAILED, with no aggregate Job, no carrier_total and no output record" failed_clean
SNAP_OUTER="$(sql "SELECT \"partition\"||'|'||attempts||'|'||completed_at FROM $R.run_partition WHERE run_id='$R3' AND \"partition\" IN (0,2) ORDER BY 1")"
SNAP_ATT1="$(sql "SELECT attempts FROM $R.run_partition WHERE run_id='$R3' AND \"partition\"=1")"
faults_clear
rcode="$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 -X POST "$RUNNER/runs/$R3/replay")"
poll 420 run_in "$R3" DONE || echo "WARN: the replay of run 3 did not finish" >&2
plan_once() { [[ "$rcode" == 202 && "$(kc -n "$NS_APPS" get job -l "edp.batch/run=$R3,edp.batch/step=plan" -o name | wc -l)" == 1 ]]; }
check "the replay is accepted with 202 and run 3 keeps its one plan Job" plan_once
J3b="$(job "$R3" reconcile 2)"
skipped() { kc -n "$NS_APPS" logs -l "job-name=$J3b,batch.kubernetes.io/job-completion-index=$1" --tail=-1 2>/dev/null | grep -qi skip; }
replay_only_failed() {
    [[ "$(sql "SELECT \"partition\"||'|'||attempts||'|'||completed_at FROM $R.run_partition WHERE run_id='$R3' AND \"partition\" IN (0,2) ORDER BY 1")" == "$SNAP_OUTER" ]] \
        && [[ "$(sql "SELECT attempts||'|'||status FROM $R.run_partition WHERE run_id='$R3' AND \"partition\"=1")" == "$((SNAP_ATT1 + 1))|DONE" ]] \
        && skipped 0 && skipped 2 && ! skipped 1
}
check "the replay Job left partitions 0 and 2 unchanged and logged them as skipped, and processed partition 1" replay_only_failed
aggregate_ok() { [[ "$(step_status "$R3" aggregate)" == DONE ]] && totals_ok "$R3" W3 && output_ok "$R3" W3; }
check "the aggregate step ran after the replay, with the window's totals and 2 output records" aggregate_ok

# --- runs 4 and 5: the quota admits one reconcile step at a time
echo "--- runs 4 and 5: two reconcile steps against a quota of one"
faults --from-literal=SLOW_PARTITION=2 --from-literal=SLOW_SECONDS=40
post_run W4; R4="$LAST_ID"
post_run W5; R5="$LAST_ID"
SECOND=""; FIRST=""
find_waiting() { local out; out="$(snap | BC waiting "$R4" "$R5")" || return 1; read -r SECOND FIRST <<<"$out"; }
check "one reconcile Workload waits (QuotaReserved False WaitingForQuota, Admitted False NoReservation) with its Job suspended and no pod, while the other is admitted" poll 120 find_waiting
pending_seen() { # kueue_pending_workloads reaches 1 while the waiting reconcile Job is still suspended
    local end=$((SECONDS + 100))
    [[ -n "$SECOND" ]] || return 1
    while (( SECONDS < end )); do
        metric_ge 'sum(kueue_pending_workloads{cluster_queue="reconciliation"})' 1 && return 0
        [[ "$(jobf "$(job "$SECOND" reconcile)" '{.spec.suspend}' 2>/dev/null)" == true ]] || return 1
        sleep 2
    done
    return 1
}
check "kueue_pending_workloads shows at least 1 pending workload during the wait" pending_seen
poll 600 runs_over "$R4" "$R5" || echo "WARN: runs 4 and 5 did not finish" >&2
faults_clear
overlap_ok() {
    local out max demand
    out="$(snap | BC overlap "$R4" "$R5")" || return 1
    max="${out#max=}"; max="${max%% *}"; demand="${out##*demand=}"
    echo "measured: $out" >&2
    (( max <= 1750 && max >= 1500 && demand == 3000 ))
}
check "the batch pods never requested more than 1750m of CPU at once, while the two reconcile steps asked for 3000m" overlap_ok
sequential_ok() {
    local started finished
    [[ -n "$FIRST" && -n "$SECOND" ]] && run_in "$FIRST" DONE && run_in "$SECOND" DONE || return 1
    started="$(jobf "$(job "$SECOND" reconcile)" '{.status.startTime}')"
    finished="$(jobf "$(job "$FIRST" reconcile)" '{.status.completionTime}')"
    [[ -n "$started" && -n "$finished" && ! "$started" < "$finished" ]] && totals_ok "$R4" W4 && totals_ok "$R5" W5
}
check "the waiting reconcile step started after the other one finished, and both runs are DONE with the right totals" sequential_ok

if [[ "$QUEUE" == off ]]; then
    echo "demo code: QUEUE=off ends here; the preemption section needs the queue"
    summary && exit 0
    exit 1
fi

# --- runs 6 and 7: a high priority run preempts a low priority one
echo "--- runs 6 and 7: preemption"
faults --from-literal=SLOW_PARTITION=2 --from-literal=SLOW_SECONDS=120
post_run W6 batch-low; R6="$LAST_ID"
J6="$(job "$R6" reconcile 1)"
r6_mid() {
    [[ "$(jobf "$J6" '{.status.completedIndexes}' 2>/dev/null)" == "0,1" ]] \
        && [[ "$(kc -n "$NS_APPS" get pods -l "job-name=$J6,batch.kubernetes.io/job-completion-index=2" -o jsonpath='{.items[*].status.phase}' 2>/dev/null)" == Running ]] \
        && snap | BC admitted "$J6"
}
precondition "run 6 (batch-low) holds the quota with partitions 0 and 1 done and index 2 running" poll 300 r6_mid
SNAP6="$(sql "SELECT \"partition\"||'|'||attempts||'|'||completed_at FROM $R.run_partition WHERE run_id='$R6' AND \"partition\" IN (0,1) ORDER BY 1")"
faults_clear
post_run W7 batch-high; R7="$LAST_ID"
J7="$(job "$R7" reconcile 1)"
r6_preempted() {
    snap | BC preempted "$J6" && [[ "$(step_status "$R6" reconcile)" == QUEUED && "$(run_status "$R6")" != FAILED ]]
}
check "run 6's Workload is Evicted (Preempted) and Preempted (InClusterQueue), its Job is suspended with no active pod, and its step is QUEUED, not FAILED" poll 90 r6_preempted
r7_while_suspended() { [[ "$(jobf "$J6" '{.spec.suspend}')" == true ]] && snap | BC admitted "$J7"; }
r7_first() {
    local resumed
    poll 90 r7_while_suspended || return 1
    poll 600 runs_over "$R6" "$R7" || return 1
    resumed="$(kc -n "$NS_APPS" get pods -l "job-name=$J6,batch.kubernetes.io/job-completion-index=2" --sort-by=.metadata.creationTimestamp -o jsonpath='{.items[-1:].status.startTime}')"
    [[ -n "$resumed" && ! "$resumed" < "$(jobf "$J7" '{.status.completionTime}')" ]]
}
check "run 7's reconcile step was admitted while run 6's Job was suspended, and finished before run 6's resumed index 2 pod started" r7_first
preempt_metrics() {
    poll 150 metric_ge 'sum(kueue_preempted_workloads_total{preempting_cluster_queue="reconciliation"})' 1 \
        && poll 90 metric_ge 'sum(kueue_evicted_workloads_total{cluster_queue="reconciliation",reason="Preempted"})' 1 \
        && poll 120 metric_eq 'sum(batch_step_preemptions_total{step="reconcile"})' 1
}
check "kueue_preempted_workloads_total and kueue_evicted_workloads_total count the preemption, and the runner counts 1 reconcile preemption" preempt_metrics
poll 600 runs_over "$R6" "$R7" || echo "WARN: runs 6 and 7 did not finish" >&2
resume_ok() {
    job_complete "$J6" && [[ "$(idx_pods "$J6")" == "0:1 1:1 2:2" ]] \
        && [[ "$(jobf "$J6" '{.status.completedIndexes}')" == "0-2" && "$(jobf "$J6" '{.status.failed}')" =~ ^0?$ ]] \
        && [[ "$(attempts "$R6")" == "0:1 1:1 2:1" ]] \
        && [[ "$(sql "SELECT \"partition\"||'|'||attempts||'|'||completed_at FROM $R.run_partition WHERE run_id='$R6' AND \"partition\" IN (0,1) ORDER BY 1")" == "$SNAP6" ]] \
        && [[ "$(kc -n "$NS_APPS" get pods -l "job-name=$J6,batch.kubernetes.io/job-completion-index=2" -o jsonpath='{range .items[*]}{.metadata.annotations.batch\.kubernetes\.io/job-index-failure-count}{" "}{end}')" == "0 0 " ]]
}
check "after the resume completedIndexes was kept, only index 2 got a new pod, partitions 0 and 1 were not read again, and no pod failure was counted" resume_ok
both_done() { run_in "$R6" DONE && run_in "$R7" DONE && totals_ok "$R6" W6 && totals_ok "$R7" W7 && output_ok "$R6" W6 && output_ok "$R7" W7; }
check "runs 6 and 7 are DONE with the right totals and 2 output records each" both_done

# --- observability
echo "--- observability"
trace_ok() {
    curl -sf --max-time 10 -o "$TMP/trace.json" "$TEMPO/api/traces/$(tid "$R1")" && BC trace "$TMP/trace.json" ai-batch-runner ai-batch-worker 3
}
check "Tempo holds one trace of run 1 with spans of ai-batch-runner and ai-batch-worker, including 3 reconcile spans" poll 120 trace_ok
poison_logged() {
    local t; t="$(tid "$R3")"
    curl -sf --max-time 15 -G "$LOKI/loki/api/v1/query_range" --data-urlencode "query={service_name=\"ai-batch-worker\"} | trace_id=\"$t\" |= \"42\"" \
        --data-urlencode limit=20 -o "$TMP/loki.json" && BC loki "$TMP/loki.json" "$t"
}
check "Loki holds the exit 42 line of run 3 with its trace id" poll 120 poison_logged
runner_metrics() {
    local s
    for s in plan reconcile aggregate; do metric_ge "sum(batch_step_duration_seconds_count{step=\"$s\"})" 1 || return 1; done
    metric_ge 'sum(batch_step_failures_total)' 1 && metric_eq 'sum(batch_step_replays_total)' 1
}
check "the runner reports a duration for plan, reconcile and aggregate, at least 1 failure and exactly 1 replay" poll 120 runner_metrics
admissions() { metric_ge 'sum(kueue_admission_wait_time_seconds_count{cluster_queue="reconciliation"})' "$(kc -n "$NS_APPS" get job -l edp.batch/run -o name | wc -l)"; }
check "kueue_admission_wait_time_seconds_count is at least the number of step Jobs" poll 120 admissions
check "Grafana serves the edp-batch dashboard" grafana_dashboard edp-batch

summary
