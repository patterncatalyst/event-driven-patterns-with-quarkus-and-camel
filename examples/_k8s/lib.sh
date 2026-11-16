# shellcheck shell=bash
# Helpers for the Part 6 verify.sh scripts. Source it from an example directory:
#   set -euo pipefail; cd "$(dirname "${BASH_SOURCE[0]}")"; source ../_k8s/lib.sh
#   trap k8s_teardown EXIT
#   k8s_fresh .
# No forwarding of any kind: services are reached through the NodePorts that cluster.sh publishes on
# 127.0.0.1, or through kubectl exec.

_LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$_LIB_DIR/../.." && pwd)"
# shellcheck source=versions.env
source "$_LIB_DIR/versions.env"

NS_APPS=edp
NS_KAFKA=edp-kafka
NS_LGTM=edp-lgtm
ORDERS=http://127.0.0.1:30080
PORT2=30082
PORT3=30084
TEMPO=http://127.0.0.1:30320
PROM=http://127.0.0.1:30909
GRAFANA=http://127.0.0.1:30300
APP_PORTS="${APP_PORTS:-30080 30082}" # the NodePorts _wait_apps waits on; a set that serves one port narrows it
TOTAL=0
PASSED=0

kc() { kubectl --context "$MK_PROFILE" "$@"; }
mk() { minikube -p "$MK_PROFILE" "$@"; }

# ---- checks -------------------------------------------------------------------------------------

check() { # check "<description>" <command...>
    local desc="$1"; shift
    TOTAL=$((TOTAL + 1))
    if "$@" >/dev/null 2>&1; then
        PASSED=$((PASSED + 1)); printf 'PASS %d: %s\n' "$TOTAL" "$desc"
    else
        printf 'FAIL %d: %s\n' "$TOTAL" "$desc"
    fi
}

_diagnostics() {
    echo "--- pods that are not Running or Completed" >&2
    kc get pods -A --no-headers 2>&1 | awk '$4 != "Running" && $4 != "Completed"' | head -20 >&2 || true
}

precondition() { # precondition "<description>" <command...>: counted as a check; failing it stops the run
    local desc="$1"; shift
    TOTAL=$((TOTAL + 1))
    if "$@" >/dev/null 2>&1; then
        PASSED=$((PASSED + 1)); printf 'PASS %d: %s\n' "$TOTAL" "$desc"
    else
        printf 'PRECONDITION FAILED %d: %s\n' "$TOTAL" "$desc" >&2
        _diagnostics
        exit 1
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

summary() {
    echo "$PASSED/$TOTAL checks passed"
    [[ "$PASSED" == "$TOTAL" ]]
}

# ---- access -------------------------------------------------------------------------------------

http_get() { curl -sf "$1"; }
ready() { curl -sf "http://127.0.0.1:$1/q/health/ready"; } # ready <nodeport>

kafka_exec_on() { # kafka_exec_on <pod> <tool> <args...>: a Kafka CLI tool in that broker pod (any cluster), bootstrap added
    local pod="$1" tool="$2"; shift 2
    kc -n "$NS_KAFKA" exec "$pod" -- "/opt/kafka/bin/$tool" --bootstrap-server localhost:9092 "$@"
}
kafka_raw_on() { # kafka_raw_on <pod> <tool> <args...>: a tool that takes no --bootstrap-server
    local pod="$1" tool="$2"; shift 2
    kc -n "$NS_KAFKA" exec "$pod" -- "/opt/kafka/bin/$tool" "$@"
}
kafka_exec() { kafka_exec_on edp-broker-0 "$@"; } # the substrate's broker
kafka_raw() { kafka_raw_on edp-broker-0 "$@"; }
group_empty() { # group_empty <group>: the consumer group has no active member (state Empty or Dead)
    local out; out="$(kafka_exec kafka-consumer-groups.sh --describe --group "$1" --state 2>/dev/null)" || return 1
    [[ "$out" =~ [[:space:]](Empty|Dead)([[:space:]]|$) ]]
}
group_offsets_copy() { # group_offsets_copy <from> <to>: copy committed offsets between two inactive groups
    # kafka-consumer-groups.sh exports `topic,partition,offset` (no header, no group column) and imports it
    # with --from-file into a group that has no active member; the target may never have existed.
    local from="$1" to="$2" csv
    group_empty "$from" || { echo "group_offsets_copy: $from still has members" >&2; return 1; }
    csv=$(kafka_exec kafka-consumer-groups.sh --group "$from" --reset-offsets --to-current --all-topics \
            --dry-run --export 2>/dev/null) || return 1
    [[ -n "$csv" ]] || { echo "group_offsets_copy: $from has no committed offsets" >&2; return 1; }
    kc -n edp-kafka exec -i edp-broker-0 -- sh -c 'cat > /tmp/edp-offsets.csv' <<<"$csv"
    kafka_exec kafka-consumer-groups.sh --group "$to" --reset-offsets --from-file /tmp/edp-offsets.csv --execute >/dev/null
}
connect_get() { # connect_get <path> [connect-pod]: read-only GET on a Connect worker, for status and offsets. Changes go through the CRs.
    local path="$1" pod="${2:-}"
    [[ -n "$pod" ]] || pod="$(kc -n "$NS_KAFKA" get pod -l strimzi.io/kind=KafkaConnect -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)"
    [[ -n "$pod" ]] || return 1
    kc -n "$NS_KAFKA" exec "$pod" -- curl -sf "localhost:8083$path"
}
psql_exec() { # psql_exec <db> <sql>; with sql "-", the SQL is read from stdin (a file or heredoc)
    if [[ "$2" == "-" ]]; then
        kc -n "$NS_APPS" exec -i edp-pg-1 -c postgres -- psql -tA -v ON_ERROR_STOP=1 -d "$1"
    else
        kc -n "$NS_APPS" exec edp-pg-1 -c postgres -- psql -tA -d "$1" -c "$2"
    fi
}

tempo_trace_with() { # tempo_trace_with <seconds> <name>...: one trace holds every span name or service name
    local secs="$1"; shift
    python3 -I "$_LIB_DIR/tools/tempo.py" "$TEMPO" "$secs" "$@"
}
prom_query() { # prom_query <promql>: prints the scalar of a one-series result, else the number of series; fails on no data
    python3 -I "$_LIB_DIR/tools/prom.py" "$PROM" "$1"
}
grafana_dashboard() { # grafana_dashboard <uid>: Grafana serves the provisioned dashboard
    [[ "$(curl -s -o /dev/null -w '%{http_code}' "$GRAFANA/api/dashboards/uid/$1")" == "200" ]]
}
prom_has() { # prom_has <metric> <service>: Prometheus holds a sample of the metric for the service
    curl -sf -G "$PROM/api/v1/query" --data-urlencode "query=$1{service_name=\"$2\"}" | grep -q '"value"'
}

# ---- images -------------------------------------------------------------------------------------

_host_image_id() { docker image inspect -f '{{.Id}}' "$1" 2>/dev/null; }
_node_image_id() {
    docker exec "$MK_PROFILE" crictl inspecti "$1" 2>/dev/null \
        | python3 -I -c 'import json,sys; print(json.load(sys.stdin)["status"]["id"])' 2>/dev/null
}

load_image() { # load_image <NN-slug> <service|->: build from the compose context, load only on an ID mismatch
    # "-" means the example has one image from examples/<slug>/Containerfile; its name is the third column of images.txt
    # and arrives as the optional third argument (load_image <slug> - <name>).
    local slug="$1" svc="$2" name="${3:-}" nn ref host node file
    nn="${slug%%-*}"
    if [[ "$svc" == "-" ]]; then
        [[ -n "$name" ]] || { echo "load_image: $slug - needs an image name (third column of images.txt)" >&2; return 1; }
        file="examples/$slug/Containerfile"; ref="localhost/edp/${nn}-${name}:local"
    else
        file="examples/$slug/$svc/Containerfile"; ref="localhost/edp/${nn}-${svc}:local"
    fi
    ( cd "$REPO_ROOT" && docker build -q -f "$file" -t "$ref" examples >/dev/null ) \
        || { echo "load_image: docker build failed for $slug/$svc" >&2; return 1; }
    host="$(_host_image_id "$ref")"
    node="$(_node_image_id "$ref" || true)"
    if [[ -n "$host" && "$host" == "$node" ]]; then
        echo "load_image: $ref already on the node"
    else
        echo "load_image: loading $ref"
        mk image load "$ref" || return 1
        node="$(_node_image_id "$ref" || true)"
        [[ "$host" == "$node" ]] || { echo "load_image: node image ID differs from the host's for $ref" >&2; return 1; }
    fi
}

_load_images_for() { # _load_images_for <kustomize-dir>: every line of <dir>/images.txt
    local dir="$1" slug svc name
    [[ -f "$dir/images.txt" ]] || return 0
    while read -r slug svc name <&3; do
        [[ -z "$slug" || "$slug" == \#* ]] && continue
        load_image "$slug" "$svc" "$name" || return 1
    done 3< "$dir/images.txt"
}

# ---- state --------------------------------------------------------------------------------------

_have() { kc get "$@" >/dev/null 2>&1; }

_wait_gone() { # _wait_gone <ns> <selector> <seconds>: no pod matches the selector
    kc -n "$1" wait --for=delete pod -l "$2" --timeout="${3}s" >/dev/null 2>&1
}

# Order: connectors and rebalances; Connect and MirrorMaker 2 with their pods and build pod gone (a worker that holds a
# replication connection keeps the CNPG instance pod terminating for minutes); KafkaTopics; Kafka; node pools; then the
# applications, Databases, the CNPG Cluster and its instance pod; then the namespaces. Finalizers are never stripped.
_delete_all_state() {
    if _have ns "$NS_KAFKA" && _have crd kafkatopics.kafka.strimzi.io; then
        kc -n "$NS_KAFKA" delete kafkaconnector,kafkarebalance --all --wait --timeout=120s >/dev/null \
            || { echo "k8s: connectors or rebalances did not delete in time" >&2; return 1; }
        kc -n "$NS_KAFKA" delete kafkaconnect,kafkamirrormaker2 --all --wait --timeout=180s >/dev/null \
            || { echo "k8s: Connect or MirrorMaker 2 did not delete in time" >&2; return 1; }
        _wait_gone "$NS_KAFKA" 'strimzi.io/kind in (KafkaConnect,KafkaMirrorMaker2)' 120 \
            || { echo "k8s: Connect or MirrorMaker 2 pods did not terminate in time" >&2; return 1; }
        kc -n "$NS_KAFKA" delete kafkatopic --all --wait --timeout=120s \
            || { echo "k8s: KafkaTopics did not delete in time (stuck finalizer?)" >&2; return 1; }
        kc -n "$NS_KAFKA" delete kafka --all --wait --timeout=240s \
            || { echo "k8s: Kafka did not delete in time" >&2; return 1; }
        kc -n "$NS_KAFKA" delete kafkanodepool --all --wait --timeout=240s \
            || { echo "k8s: KafkaNodePools did not delete in time" >&2; return 1; }
    fi
    if _have ns "$NS_APPS"; then
        # The applications first, so no client holds a connection while PostgreSQL shuts down
        kc -n "$NS_APPS" delete deploy,job --all --wait --timeout=120s >/dev/null \
            || { echo "k8s: application Deployments did not delete in time" >&2; return 1; }
        kc -n "$NS_APPS" wait --for=delete pod -l '!cnpg.io/cluster' --timeout=120s >/dev/null 2>&1 \
            || { echo "k8s: application pods did not terminate in time" >&2; return 1; }
    fi
    if _have ns "$NS_APPS" && _have crd databases.postgresql.cnpg.io; then
        # CNPG Databases before their Cluster, so no Database finalizer waits on a deleted instance
        kc -n "$NS_APPS" delete database.postgresql.cnpg.io --all --wait --timeout=120s \
            || { echo "k8s: CNPG Databases did not delete in time" >&2; return 1; }
        kc -n "$NS_APPS" delete cluster.postgresql.cnpg.io --all --wait --timeout=120s \
            || { echo "k8s: CNPG Cluster did not delete in time" >&2; return 1; }
        # The instance pod is garbage collected after its Cluster and carries CloudNativePG's long termination
        # grace period (stopDelay, 1800 s). A namespace deleted while such a pod is still terminating is not
        # rechecked by the namespace controller until much later, so wait until the pod is gone.
        _wait_gone "$NS_APPS" cnpg.io/cluster 240 \
            || { echo "k8s: CNPG instance pods did not terminate in time" >&2; return 1; }
    fi
    kc delete ns "$NS_APPS" "$NS_KAFKA" "$NS_LGTM" --ignore-not-found --wait --timeout=240s \
        || { echo "k8s: namespaces did not delete in time" >&2; _why_terminating; return 1; }
}

_why_terminating() { # print why each namespace is still Terminating: its conditions and what is left in it
    local ns
    for ns in "$NS_APPS" "$NS_KAFKA" "$NS_LGTM"; do
        _have ns "$ns" || continue
        kc get ns "$ns" -o jsonpath='{range .status.conditions[*]}{.type}: {.message}{"\n"}{end}' >&2 || true
        kc api-resources --verbs=list --namespaced -o name 2>/dev/null \
            | xargs -r -I{} kubectl --context "$MK_PROFILE" -n "$ns" get {} --ignore-not-found -o name >&2 2>/dev/null || true
    done
}

_assert_clean() {
    local terminating pvs
    terminating="$(kc get ns --no-headers 2>/dev/null | awk '$2 == "Terminating" {print $1}')"
    [[ -z "$terminating" ]] || { echo "k8s: namespaces still Terminating: $terminating" >&2; return 1; }
    _no_bound_pv() {
        pvs="$(kc get pv -o jsonpath='{range .items[*]}{.spec.claimRef.namespace}{"\n"}{end}' 2>/dev/null \
            | grep -E "^($NS_APPS|$NS_KAFKA|$NS_LGTM)\$" || true)"
        [[ -z "$pvs" ]]
    }
    poll 90 _no_bound_pv || { echo "k8s: PersistentVolumes still bound to a deleted namespace" >&2; return 1; }
}

k8s_teardown() { # delete the three namespaces (KafkaTopics first) unless KEEP=1; never deletes the profile
    if [[ "${KEEP:-0}" == "1" ]]; then
        echo "KEEP=1: namespaces left in place; run \`kubectl --context $MK_PROFILE delete ns $NS_APPS $NS_KAFKA $NS_LGTM\` to remove them"
        return 0
    fi
    local t0=$SECONDS
    _delete_all_state || { echo "k8s_teardown: FAILED, state left for inspection" >&2; return 1; }
    echo "measured: KafkaTopics, Kafka and the three namespaces deleted in $((SECONDS - t0)) s"
}

_apply() { # _apply <dir> <manager>: server-side apply; retried while new CRDs are still being established
    local n
    for n in 1 2 3; do
        kc apply --server-side --field-manager="$2" -k "$1" >/dev/null && return 0
        echo "k8s: apply attempt $n failed; waiting for CRDs to be established" >&2
        kc wait --for=condition=Established crd --all --timeout=60s >/dev/null 2>&1 || true
    done
    return 1
}

kafka_ready() { # kafka_ready <name>: the Kafka CR's generation is observed and it is Ready. (The r11 test used a regex
    # backreference, which bash 5.3 never matches, and `read` returns 1 at the end of jsonpath output, which has no newline.)
    local g o s
    read -r g o s < <(kc -n "$NS_KAFKA" get kafka "$1" -o jsonpath='{.metadata.generation} {.status.observedGeneration} {.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null) || true
    [[ -n "$g" && "$g" == "$o" && "$s" == "True" ]]
}
_kafka_ready() { kafka_ready edp; }

_all_kafka_ready() { # every Kafka CR in edp-kafka is Ready
    local k names
    names="$(kc -n "$NS_KAFKA" get kafka -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}' 2>/dev/null)" || return 1
    [[ -n "$names" ]] || return 1
    for k in $names; do kafka_ready "$k" || return 1; done
}

_cr_ready() { # _cr_ready <kind>: every resource of the kind in edp-kafka reports Ready True
    local out
    out="$(kc -n "$NS_KAFKA" get "$1" -o jsonpath='{range .items[*]}{.status.conditions[?(@.type=="Ready")].status}{"\n"}{end}' 2>/dev/null)" || return 1
    [[ -n "$out" ]] && ! grep -qv '^True$' <<<"$out"
}

_database_applied() { # every CNPG Database in the namespace reports status.applied
    local out
    out="$(kc -n "$NS_APPS" get database.postgresql.cnpg.io -o jsonpath='{range .items[*]}{.status.applied}{"\n"}{end}' 2>/dev/null)" || return 1
    [[ -n "$out" ]] && ! grep -qv '^true$' <<<"$out"
}

_wait_apps() { # every Deployment in edp rolled out, every Job complete
    local d
    for d in $(kc -n "$NS_APPS" get deploy -o name 2>/dev/null); do
        kc -n "$NS_APPS" rollout status "$d" --timeout=240s >/dev/null || { echo "k8s: $d did not roll out" >&2; return 1; }
    done
    if [[ -n "$(kc -n "$NS_APPS" get job -o name 2>/dev/null)" ]]; then
        kc -n "$NS_APPS" wait --for=condition=complete job --all --timeout=240s >/dev/null \
            || { echo "k8s: a Job did not complete" >&2; return 1; }
    fi
    # A Ready pod is not yet reachable through the published NodePort: wait until the published ports answer.
    if [[ -n "$(kc -n "$NS_APPS" get deploy -o name 2>/dev/null)" ]]; then
        local port i
        for port in $APP_PORTS; do
            for i in $(seq 1 60); do ready "$port" >/dev/null 2>&1 && continue 2; sleep 1; done
            echo "k8s: NodePort $port does not answer" >&2; return 1
        done
    fi
}

k8s_fresh() { # k8s_fresh <kustomize-dir> [manager=edp-verify]
    local dir="${1:?k8s_fresh <kustomize-dir> [manager]}" mgr="${2:-edp-verify}"
    # shellcheck source=../../scripts/stack-guard.sh
    source "$REPO_ROOT/scripts/stack-guard.sh" --minikube "$MK_PROFILE" || exit 1
    "$_LIB_DIR/cluster.sh" up
    _load_images_for "$dir" || { echo "k8s: image build or load failed" >&2; exit 1; }
    _delete_all_state || exit 1
    _assert_clean || exit 1
    _apply "$dir" "$mgr" || { echo "k8s: apply of $dir failed" >&2; exit 1; }
    if _have -n "$NS_KAFKA" deploy/strimzi-cluster-operator; then
        kc -n "$NS_KAFKA" wait --for=condition=Available deploy/strimzi-cluster-operator --timeout=180s >/dev/null \
            || { echo "k8s: Strimzi operator not Available" >&2; _diagnostics; exit 1; }
    fi
    if [[ -n "$(kc -n "$NS_KAFKA" get kafka -o name 2>/dev/null)" ]]; then
        poll 420 _all_kafka_ready || { echo "k8s: a Kafka cluster is not Ready" >&2; kc -n "$NS_KAFKA" get kafka -o yaml | tail -60 >&2; exit 1; }
    fi
    if _have -n "$NS_KAFKA" pod/edp-broker-0; then
        kc -n "$NS_KAFKA" wait --for=condition=Ready pod/edp-broker-0 --timeout=120s >/dev/null \
            || { echo "k8s: broker pod not Ready" >&2; exit 1; }
    fi
    local kind
    for kind in kafkaconnect kafkamirrormaker2; do
        # 420 s: a KafkaConnect with a build section waits for the build pod, the push and the pull of the new image
        if _have crd "$kind.kafka.strimzi.io" || _have crd "${kind}s.kafka.strimzi.io"; then
            [[ -n "$(kc -n "$NS_KAFKA" get "$kind" -o name 2>/dev/null)" ]] || continue
            poll 420 _cr_ready "$kind" || { echo "k8s: $kind not Ready" >&2; _diagnostics; exit 1; }
        fi
    done
    if _have -n "$NS_APPS" cluster.postgresql.cnpg.io/edp-pg; then
        kc -n "$NS_APPS" wait --for=condition=Ready cluster.postgresql.cnpg.io/edp-pg --timeout=300s >/dev/null \
            || { echo "k8s: CNPG Cluster edp-pg not Ready" >&2; _diagnostics; exit 1; }
        if [[ -n "$(kc -n "$NS_APPS" get database.postgresql.cnpg.io -o name 2>/dev/null)" ]]; then
            poll 90 _database_applied || { echo "k8s: CNPG Database resources not applied" >&2; exit 1; }
        fi
    fi
    if _have -n "$NS_LGTM" deploy/lgtm; then
        kc -n "$NS_LGTM" rollout status deploy/lgtm --timeout=240s >/dev/null || { echo "k8s: lgtm not rolled out" >&2; exit 1; }
    fi
    if _have -n "$NS_LGTM" deploy/kube-state-metrics; then
        kc -n "$NS_LGTM" rollout status deploy/kube-state-metrics --timeout=180s >/dev/null || { echo "k8s: kube-state-metrics not rolled out" >&2; exit 1; }
    fi
    if _have -n "$NS_KAFKA" deploy/registry; then
        kc -n "$NS_KAFKA" rollout status deploy/registry --timeout=180s >/dev/null || { echo "k8s: registry not rolled out" >&2; exit 1; }
    fi
    if [[ "${WAIT_APPS:-1}" != "0" ]]; then
        _wait_apps || { _diagnostics; exit 1; }
    fi
}

deploy_set() { # deploy_set <kustomize-dir>: apply an app set and wait for it
    local dir="${1:?deploy_set <kustomize-dir>}"
    _load_images_for "$dir" || { echo "deploy_set: image build or load failed" >&2; return 1; }
    _apply "$dir" edp-verify || return 1
    _wait_apps
}

remove_set() { # remove_set <kustomize-dir>: delete an app set and wait until it is gone
    local dir="${1:?remove_set <kustomize-dir>}"
    kc delete -k "$dir" --ignore-not-found --wait --timeout=120s \
        || { echo "remove_set: $dir did not delete in time" >&2; return 1; }
    # Deleting the Deployments returns before their pods are gone; wait so the next set starts clean.
    kc -n "$NS_APPS" wait --for=delete pod -l '!cnpg.io/cluster' --timeout=120s >/dev/null 2>&1 || true
}
