#!/usr/bin/env bash
# Idiomatic-code scan over examples/ (vendored scanner in scripts/idiomatic/).
#   scripts/idiomatic-scan.sh            gate: ban tier must be 0 (CI runs this)
#   scripts/idiomatic-scan.sh [options]  pass scan.sh options through, e.g. --tier watch --hits all
# Passes when examples/ does not exist yet.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
if [[ ! -d examples ]]; then
    echo "idiomatic scan: no examples/ yet, nothing to scan"
    exit 0
fi
if [[ $# -eq 0 ]]; then
    bash scripts/idiomatic/scan.sh --tier ban --fail examples
    echo "idiomatic scan: 0 ban-tier hits"
else
    bash scripts/idiomatic/scan.sh "$@" examples
fi
