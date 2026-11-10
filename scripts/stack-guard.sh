#!/usr/bin/env bash
# Source from an example's verify.sh:  source "$(dirname "$0")/../../scripts/stack-guard.sh" <compose-project>
# One stack at a time: returns/exits non-zero when containers from another compose project
# (label com.docker.compose.project) or a minikube node container are running. Never stops anything.
# Containers named quarkus-agent-mcp* are tooling and are ignored.
_sg_project="${1:-}"
if [[ -z "$_sg_project" ]]; then
    echo "stack-guard: compose project name required" >&2
    return 2 2>/dev/null || exit 2
fi
_sg_bad=()
while IFS=$'\t' read -r _sg_name _sg_proj _sg_kic; do
    [[ -z "$_sg_name" || "$_sg_name" == quarkus-agent-mcp* ]] && continue
    if [[ -n "$_sg_proj" && "$_sg_proj" != "$_sg_project" ]]; then
        _sg_bad+=("$_sg_name (compose project $_sg_proj)")
    elif [[ -n "$_sg_kic" ]]; then
        _sg_bad+=("$_sg_name (minikube node)")
    fi
done < <(docker ps --format '{{.Names}}\t{{.Label "com.docker.compose.project"}}\t{{.Label "name.minikube.sigs.k8s.io"}}')
if [[ ${#_sg_bad[@]} -gt 0 ]]; then
    echo "stack-guard: another stack is running; stop it first (this guard never stops anything):" >&2
    printf '  %s\n' "${_sg_bad[@]}" >&2
    unset _sg_name _sg_proj _sg_kic _sg_project
    _sg_rc=1
    unset _sg_bad
    return "$_sg_rc" 2>/dev/null || exit "$_sg_rc"
fi
unset _sg_name _sg_proj _sg_kic _sg_project _sg_bad
