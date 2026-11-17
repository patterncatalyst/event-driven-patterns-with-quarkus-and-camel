#!/usr/bin/env bash
# Source from an example's verify.sh:  source "$(dirname "$0")/../../scripts/stack-guard.sh" <compose-project>
# One stack at a time: returns/exits non-zero when containers from another compose project
# (label com.docker.compose.project), a minikube node container or a running CRC instance are found.
# Never stops anything.
# Containers named quarkus-agent-mcp* are tooling and are ignored.
# Part 6:  source scripts/stack-guard.sh --minikube <profile>
#   refuses when any compose-project container, another minikube profile's node, or a running
#   CRC instance is found. The named profile's own node is allowed.
# Appendix H:  source scripts/stack-guard.sh --crc
#   requires a running CRC instance and refuses when any compose-project container or minikube
#   node is found.
_sg_mk=""
_sg_crc=""
if [[ "${1:-}" == "--crc" ]]; then
    _sg_crc=1
    set -- "__crc__"
fi
if [[ "${1:-}" == "--minikube" ]]; then
    _sg_mk="${2:-}"
    if [[ -z "$_sg_mk" ]]; then
        echo "stack-guard: minikube profile name required" >&2
        return 2 2>/dev/null || exit 2
    fi
    set -- "__minikube__"
fi
_sg_project="${1:-}"
if [[ -z "$_sg_project" ]]; then
    echo "stack-guard: compose project name required" >&2
    return 2 2>/dev/null || exit 2
fi
if [[ -z "$_sg_crc" ]] && ! docker ps -q >/dev/null 2>&1; then
    echo "stack-guard: docker is not reachable, so the one-stack rule cannot be checked" >&2
    unset _sg_project _sg_mk _sg_crc
    return 2 2>/dev/null || exit 2
fi
_sg_bad=()
while IFS='|' read -r _sg_name _sg_proj _sg_kic; do
    [[ -z "$_sg_name" || "$_sg_name" == quarkus-agent-mcp* ]] && continue
    if [[ -n "$_sg_crc" ]]; then
        if [[ -n "$_sg_proj" ]]; then
            _sg_bad+=("$_sg_name (compose project $_sg_proj)")
        elif [[ -n "$_sg_kic" ]]; then
            _sg_bad+=("$_sg_name (minikube profile $_sg_kic)")
        fi
    elif [[ -n "$_sg_mk" ]]; then
        if [[ -n "$_sg_proj" ]]; then
            _sg_bad+=("$_sg_name (compose project $_sg_proj)")
        elif [[ -n "$_sg_kic" && "$_sg_kic" != "$_sg_mk" ]]; then
            _sg_bad+=("$_sg_name (minikube profile $_sg_kic)")
        fi
    elif [[ -n "$_sg_proj" && "$_sg_proj" != "$_sg_project" ]]; then
        _sg_bad+=("$_sg_name (compose project $_sg_proj)")
    elif [[ -n "$_sg_kic" ]]; then
        _sg_bad+=("$_sg_name (minikube node)")
    fi
done < <(docker ps --format '{{.Names}}|{{.Label "com.docker.compose.project"}}|{{.Label "name.minikube.sigs.k8s.io"}}')
_sg_crc_up=""
if command -v crc >/dev/null 2>&1; then
    _sg_status="$(crc status 2>/dev/null || true)"
    [[ "$_sg_status" =~ OpenShift:[[:space:]]+Running ]] && _sg_crc_up=1
    unset _sg_status
fi
if [[ -n "$_sg_crc" && -z "$_sg_crc_up" ]]; then
    _sg_bad+=("CRC is not running (start it with crc start)")
elif [[ -z "$_sg_crc" && -n "$_sg_crc_up" ]]; then
    _sg_bad+=("CRC (crc status reports Running)")
fi
if [[ ${#_sg_bad[@]} -gt 0 ]]; then
    echo "stack-guard: another stack is running; stop it first (this guard never stops anything):" >&2
    printf '  %s\n' "${_sg_bad[@]}" >&2
    unset _sg_name _sg_proj _sg_kic _sg_project _sg_mk _sg_crc _sg_crc_up
    _sg_rc=1
    unset _sg_bad
    return "$_sg_rc" 2>/dev/null || exit "$_sg_rc"
fi
unset _sg_name _sg_proj _sg_kic _sg_project _sg_bad _sg_mk _sg_crc _sg_crc_up
