#!/usr/bin/env bash
# Run every examples/NN-*/verify.sh one at a time; stop on the first failure; print a summary.
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
shopt -s nullglob
passed=() failed=""
for dir in examples/[0-9][0-9]-*/; do
    v="${dir}verify.sh"
    [[ -f "$v" ]] || continue
    echo "==> $dir"
    if bash "$v"; then passed+=("$dir"); else failed="$dir"; break; fi
done
echo
echo "verify-all: ${#passed[@]} passed"
printf '  ok   %s\n' "${passed[@]}"
if [[ -n "$failed" ]]; then echo "  FAIL $failed"; exit 1; fi
[[ ${#passed[@]} -gt 0 ]] || echo "verify-all: no examples yet"
