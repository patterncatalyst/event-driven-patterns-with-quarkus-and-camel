#!/usr/bin/env bash
# Shared infrastructure stack (examples/_infra/compose.yaml).
#   scripts/setup-stack.sh up       start it and wait for healthy services
#   scripts/setup-stack.sh down     stop it, keep volumes
#   scripts/setup-stack.sh --clean  stop it and remove volumes (down -v)
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
FILE=examples/_infra/compose.yaml
[[ -f "$FILE" ]] || { echo "setup-stack: $FILE does not exist yet" >&2; exit 1; }
case "${1:-up}" in
    up)       docker compose -f "$FILE" up -d --wait ;;
    down)     docker compose -f "$FILE" down ;;
    --clean)  docker compose -f "$FILE" down -v ;;
    *) echo "usage: $0 up|down|--clean" >&2; exit 2 ;;
esac
