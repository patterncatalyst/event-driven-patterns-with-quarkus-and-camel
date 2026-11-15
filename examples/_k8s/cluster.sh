#!/usr/bin/env bash
# cluster.sh up | down | status: the single minikube profile for Part 6.
#   up      guard, tool versions, ports free, profile (create or start), loopback-only ports,
#           vendor manifests (sha256 checked), CloudNativePG operator. Idempotent.
#   down    minikube delete -p edp
#   status  profile, ports, namespaces
set -euo pipefail
K8S_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$K8S_DIR/../.." && pwd)"
# shellcheck source=tools.sh
source "$K8S_DIR/tools.sh"

P="$MK_PROFILE"
kc() { kubectl --context "$P" "$@"; }

profile_exists() {
    minikube profile list -o json 2>/dev/null | python3 -I -c '
import json, sys
try:
    d = json.load(sys.stdin)
except ValueError:
    sys.exit(1)
names = [p["Name"] for k in ("valid", "invalid") for p in d.get(k) or []]
sys.exit(0 if sys.argv[1] in names else 1)' "$P"
}

host_state() { minikube status -p "$P" --format='{{.Host}}' 2>/dev/null || true; }

ports_free() {
    local p busy=0
    for p in $MK_PORTS; do
        if [[ -n "$(ss -ltnH "sport = :$p")" ]]; then
            echo "cluster: host port $p is in use" >&2; busy=1
        fi
    done
    return "$busy"
}

ports_loopback_only() {
    local out p
    out="$(docker port "$P")"
    for p in $MK_PORTS; do
        grep -q "^$p/tcp -> 127.0.0.1:$p\$" <<<"$out" || { echo "cluster: port $p is not published on 127.0.0.1" >&2; return 1; }
    done
    if grep -E -- '-> (0\.0\.0\.0|\[?::\]?):' <<<"$out" >/dev/null; then
        echo "cluster: a published port is bound beyond 127.0.0.1" >&2; return 1
    fi
}

fetch_manifest() { # fetch_manifest <url> <sha256> <relative file>
    local url="$1" want="$2" file="$K8S_DIR/$3" tmp got
    if [[ -f "$file" ]]; then
        got="$(sha256sum "$file" | cut -d' ' -f1)"
        [[ "$got" == "$want" ]] && return 0
        echo "cluster: $3 does not match the pinned sha256; delete it to download again" >&2; return 1
    fi
    tmp="$(mktemp)"
    curl -fsSL -o "$tmp" "$url"
    got="$(sha256sum "$tmp" | cut -d' ' -f1)"
    if [[ "$got" != "$want" ]]; then
        rm -f "$tmp"
        echo "cluster: downloaded $3 has sha256 $got, expected $want; refusing to use it" >&2; return 1
    fi
    mv "$tmp" "$file"
}

# The in-cluster registry (registry/, fixed ClusterIP) speaks plain HTTP, so the node's containerd must be allowed to pull
# from it that way. First choice: the profile flag `minikube start --insecure-registry=<service CIDR>`. If containerd
# has no entry for the registry address after that (a profile created without the flag, or a flag that only reaches
# the Docker daemon), write the hosts.toml for it inside the node. Both are settings of the profile, not of the host.
registry_pull_over_http() {
    local addr="$REGISTRY_IP:$REGISTRY_PORT" f
    f="/etc/containerd/certs.d/$addr/hosts.toml"
    if docker exec "$P" test -f "$f"; then
        echo "cluster: containerd on the node already pulls from $addr over HTTP"; return 0
    fi
    echo "cluster: the --insecure-registry flag left no containerd entry for $addr; writing $f in the node"
    docker exec -i "$P" sh -c "mkdir -p /etc/containerd/certs.d/$addr && cat > $f" <<TOML
server = "http://$addr"

[host."http://$addr"]
  capabilities = ["pull", "resolve"]
TOML
}

cmd_up() {
    check_tools
    # shellcheck source=../../scripts/stack-guard.sh
    source "$REPO_ROOT/scripts/stack-guard.sh" --minikube "$P" || exit 1
    if profile_exists; then
        if [[ "$(host_state)" != "Running" ]]; then
            echo "cluster: starting stopped profile $P"
            minikube start -p "$P" --keep-context
        fi
    else
        ports_free
        local ports=() p
        for p in $MK_PORTS; do ports+=("--ports=127.0.0.1:$p:$p"); done
        echo "cluster: creating profile $P ($MK_CPUS CPUs, $MK_MEMORY, Kubernetes $K8S_VERSION)"
        minikube start -p "$P" --keep-context --driver=docker --container-runtime=containerd \
            --kubernetes-version="$K8S_VERSION" --cpus="$MK_CPUS" --memory="$MK_MEMORY" --disk-size="$MK_DISK" \
            --insecure-registry="$MK_INSECURE_REGISTRY" "${ports[@]}"
    fi
    ports_loopback_only
    registry_pull_over_http
    fetch_manifest "$CNPG_URL" "$CNPG_SHA256" "$CNPG_FILE"
    fetch_manifest "$STRIMZI_URL" "$STRIMZI_SHA256" "$STRIMZI_FILE"
    kc apply --server-side --field-manager=edp-cluster -k "$K8S_DIR/platform/cnpg" >/dev/null
    kc -n cnpg-system wait --for=condition=Available deployment/cnpg-controller-manager --timeout=180s >/dev/null
    echo "cluster: $P is up; CloudNativePG $CNPG_VERSION is running in cnpg-system"
}

cmd_down() {
    minikube delete -p "$P"
}

cmd_status() {
    echo "profile: $P"
    if ! profile_exists; then echo "state: absent"; return 0; fi
    echo "state: $(host_state)"
    echo "ports:"
    docker port "$P" 2>/dev/null | grep -E "^($(echo "$MK_PORTS" | tr ' ' '|'))/tcp" | sed 's/^/  /' || true
    echo "namespaces:"
    kc get ns --no-headers 2>/dev/null | awk '$1 ~ /^(edp|edp-kafka|edp-lgtm|cnpg-system)$/ {print "  " $1, $2}' || true
}

case "${1:-}" in
    up) cmd_up ;;
    down) cmd_down ;;
    status) cmd_status ;;
    *) echo "usage: cluster.sh up|down|status" >&2; exit 2 ;;
esac
