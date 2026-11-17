#!/usr/bin/env bash
# Refuses unless OpenShift Local is running and the current context is crc-admin. It never starts, stops or
# configures CRC. Run it, or source it from another script.
_crc_fail() { echo "crc-check: $1" >&2; return 1 2>/dev/null || exit 1; }
command -v crc >/dev/null 2>&1 || { _crc_fail "crc is not on PATH"; return 1 2>/dev/null || exit 1; }
for _tool in oc buildah skopeo podman jq curl; do
    command -v "$_tool" >/dev/null 2>&1 || { _crc_fail "$_tool is not on PATH"; return 1 2>/dev/null || exit 1; }
done
unset _tool
_crc_status="$(crc status 2>/dev/null || true)"
if [[ ! "$_crc_status" =~ OpenShift:[[:space:]]+Running ]]; then
    _crc_fail "OpenShift Local is not running (crc status); start it yourself, this script never does"
    return 1 2>/dev/null || exit 1
fi
unset _crc_status
_crc_ctx="$(oc config current-context 2>/dev/null || true)"
if [[ "$_crc_ctx" != crc-admin ]]; then
    _crc_fail "the current context is '${_crc_ctx:-none}', not crc-admin; refusing to touch another cluster"
    return 1 2>/dev/null || exit 1
fi
unset _crc_ctx
oc whoami >/dev/null 2>&1 || { _crc_fail "oc cannot reach the cluster as the current user"; return 1 2>/dev/null || exit 1; }
