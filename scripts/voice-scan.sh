#!/usr/bin/env bash
# Professional-voice scan over the reader-facing surfaces.
#   scripts/voice-scan.sh                       gate: ban tier must be 0 (CI runs this)
#   scripts/voice-scan.sh --all-tiers-summary   all-tier totals (never fails)
#   scripts/voice-scan.sh [scan.sh options] [PATH...]
# The scanner is vendored in scripts/voice/ (see its README); fix it upstream.
set -euo pipefail
START_DIR="$PWD"
cd "$(dirname "${BASH_SOURCE[0]}")/.."
SCAN=(bash scripts/voice/scan.sh)

SITE_ROOTS=(_docs _parts _example_pages _includes _layouts index.html chapters.html README.md bibliography.md diagrams)
SITE_ROOTS=($(for p in "${SITE_ROOTS[@]}"; do [[ -e "$p" ]] && echo "$p"; done))
EXAMPLE_ROOTS=()
[[ -d examples ]] && EXAMPLE_ROOTS=(examples)

run_both() {
    local rc=0
    if [[ ${#SITE_ROOTS[@]} -gt 0 ]]; then
        echo "== site"
        "${SCAN[@]}" "$@" "${SITE_ROOTS[@]}" || rc=1
    fi
    if [[ ${#EXAMPLE_ROOTS[@]} -gt 0 ]]; then
        echo "== examples (Markdown only)"
        "${SCAN[@]}" --ext md "$@" "${EXAMPLE_ROOTS[@]}" || rc=1
    fi
    return "$rc"
}

if [[ $# -eq 0 ]]; then
    if run_both --tier ban --fail; then
        echo "voice scan: 0 ban-tier hits"
        exit 0
    fi
    echo
    echo "voice scan: ban-tier phrases found (rewrite them; see lgtm-professional-voice):"
    run_both --tier ban --hits all || true
    exit 1
fi

if [[ "$1" == --all-tiers-summary ]]; then
    run_both --tier all || true
    exit 0
fi

opts=() paths=()
while [[ $# -gt 0 ]]; do
    case "$1" in
        --tier|--hits|--ext|--exclude|--patterns)
            [[ $# -ge 2 ]] || { echo "voice-scan.sh: $1 needs a value" >&2; exit 2; }
            opts+=("$1" "$2"); shift 2 ;;
        -*) opts+=("$1"); shift ;;
        *)  p="$1"; [[ "$p" = /* ]] || p="$START_DIR/$p"
            paths+=("$(realpath --relative-to="$PWD" "$p")"); shift ;;
    esac
done
if [[ ${#paths[@]} -gt 0 ]]; then
    "${SCAN[@]}" "${opts[@]}" "${paths[@]}"
else
    run_both "${opts[@]}"
fi
