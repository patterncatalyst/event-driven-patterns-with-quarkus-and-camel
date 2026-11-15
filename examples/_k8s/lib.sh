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

kafka_exec() { # kafka_exec <tool> <args...>: a Kafka CLI tool in the broker pod, bootstrap added
    local tool="$1"; shift
    kc -n "$NS_KAFKA" exec edp-broker-0 -- "/opt/kafka/bin/$tool" --bootstrap-server localhost:9092 "$@"
}
kafka_raw() { # kafka_raw <tool> <args...>: a tool that takes no --bootstrap-server
    local tool="$1"; shift
    kc -n "$NS_KAFKA" exec edp-broker-0 -- "/opt/kafka/bin/$tool" "$@"
}
psql_exec() { # psql_exec <db> <sql>
    kc -n "$NS_APPS" exec edp-pg-1 -c postgres -- psql -tA -d "$1" -c "$2"
}

tempo_trace_with() { # tempo_trace_with <seconds> <name>...: one trace holds every span name or service name
    local secs="$1"; shift
    python3 -I "$_LIB_DIR/tools/tempo.py" "$TEMPO" "$secs" "$@"
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

load_image() { # load_image <NN-slug> <service>: build from the compose context, load only on an ID mismatch
    local slug="$1" svc="$2" nn ref host node
    nn="${slug%%-*}"
    ref="localhost/edp/${nn}-${svc}:local"
    ( cd "$REPO_ROOT" && docker build -q -f "examples/$slug/$svc/Containerfile" -t "$ref" examples >/dev/null ) \
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
    local dir="$1" slug svc
    [[ -f "$dir/images.txt" ]] || return 0
    while read -r slug svc <&3; do
        [[ -z "$slug" || "$slug" == \#* ]] && continue
        load_image "$slug" "$svc" || return 1
    done 3< "$dir/images.txt"
}

# ---- state --------------------------------------------------------------------------------------

_have() { kc get "$@" >/dev/null 2>&1; }

_delete_all_state() { # KafkaTopics first, then Kafka and node pools, then the namespaces. Never strips finalizers.
    if _have ns "$NS_KAFKA" && _have crd kafkatopics.kafka.strimzi.io; then
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
        kc -n "$NS_APPS" wait --for=delete pod -l cnpg.io/cluster --timeout=240s >/dev/null 2>&1 \
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

_kafka_ready() {
    local v
    v="$(kc -n "$NS_KAFKA" get kafka edp -o jsonpath='{.metadata.generation} {.status.observedGeneration} {.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null)" || return 1
    [[ "$v" =~ ^([0-9]+)\ \1\ True$ ]]
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
        for port in 30080 30082; do
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
    if _have -n "$NS_KAFKA" kafka/edp; then
        poll 420 _kafka_ready || { echo "k8s: Kafka edp not Ready" >&2; kc -n "$NS_KAFKA" get kafka edp -o yaml | tail -40 >&2; exit 1; }
        kc -n "$NS_KAFKA" wait --for=condition=Ready pod/edp-broker-0 --timeout=120s >/dev/null \
            || { echo "k8s: broker pod not Ready" >&2; exit 1; }
    fi
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
