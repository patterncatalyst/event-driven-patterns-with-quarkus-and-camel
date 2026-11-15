#!/usr/bin/env bash
# Checks that minikube and kubectl on PATH are the pinned versions in versions.env. Downloads nothing.
# Run it, or source it and call check_tools.
_K8S_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=versions.env
source "$_K8S_DIR/versions.env"

check_tools() {
    local have bad=0
    if ! command -v minikube >/dev/null 2>&1; then
        echo "tools: minikube is not on PATH; install $MINIKUBE_VERSION" >&2; bad=1
    else
        have="$(minikube version --short 2>/dev/null)"
        [[ "$have" == "$MINIKUBE_VERSION" ]] || { echo "tools: minikube is $have, expected $MINIKUBE_VERSION" >&2; bad=1; }
    fi
    if ! command -v kubectl >/dev/null 2>&1; then
        echo "tools: kubectl is not on PATH; install $KUBECTL_VERSION" >&2; bad=1
    else
        have="$(kubectl version --client 2>/dev/null | awk '/^Client Version:/ {print $3}')"
        [[ "$have" == "$KUBECTL_VERSION" ]] || { echo "tools: kubectl is $have, expected $KUBECTL_VERSION" >&2; bad=1; }
    fi
    if ! command -v docker >/dev/null 2>&1; then
        echo "tools: docker is not on PATH" >&2; bad=1
    fi
    return "$bad"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    set -euo pipefail
    check_tools && echo "tools: minikube $MINIKUBE_VERSION and kubectl $KUBECTL_VERSION"
fi
