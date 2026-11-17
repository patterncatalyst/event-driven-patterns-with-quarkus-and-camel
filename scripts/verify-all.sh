#!/usr/bin/env bash
# Run the examples' verify.sh one at a time from fresh state; stop on the first failure; print a summary.
#
#   scripts/verify-all.sh [group] [--from <dir>] [--list]
#   scripts/verify-all.sh --mvn
#
# Groups (default: compose):
#   compose       examples 00 to 27 (docker compose; each verify.sh starts with `down -v` and ends torn down)
#   k8s           examples 28 to 35 on the minikube profile from examples/_k8s/cluster.sh (start it first)
#   k8s-appendix  Appendix B's minikube half, then Appendix F (same profile)
#   appendix      Appendices A, B (compose half), C, E, G, then B's compare (no stack)
# Appendix H (OpenShift Local) runs on its own: examples/ah-openshift-local/README.md.
#
#   --from <dir>  start at that example directory (after a fix), skipping the ones before it
#   --list        print what would run and exit
#   --mvn         `mvn -B verify` over domain-model (installed first) and every other pom, on Dev Services
#
# RUN_DIR (default: a new directory under ${TMPDIR:-/tmp}) receives one log per example, filtered through
# scripts/host_ids.py so no host identifier is kept, and results.tsv:
#   example  checks  exit  wall_s  teardown
# teardown is "clean" when no compose container or volume of the project remains (compose), or the seconds
# k8s_teardown reported (k8s); "LEFT ..." otherwise.
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
ROOT="$PWD"
shopt -s nullglob

group="compose" from="" list=0 mvn=0
while [[ $# -gt 0 ]]; do
    case "$1" in
        compose|k8s|k8s-appendix|appendix) group="$1" ;;
        --from) from="${2:?--from needs a directory}"; from="${from%/}"; from="${from#examples/}"; shift ;;
        --list) list=1 ;;
        --mvn) mvn=1 ;;
        *) echo "usage: scripts/verify-all.sh [compose|k8s|k8s-appendix|appendix] [--from <dir>] [--list] | --mvn" >&2; exit 2 ;;
    esac
    shift
done

RUN_DIR="${RUN_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/verify-all.XXXXXX")}"
mkdir -p "$RUN_DIR"
TSV="$RUN_DIR/results.tsv"
[[ -f "$TSV" ]] || printf 'example\tchecks\texit\twall_s\tteardown\n' > "$TSV"

scrub() { python3 -I "$ROOT/scripts/host_ids.py" filter; }

# --- mvn mode ---------------------------------------------------------------------------------------------
if [[ $mvn -eq 1 ]]; then
    poms=(examples/domain-model/pom.xml)
    while IFS= read -r p; do [[ "$p" == examples/domain-model/pom.xml ]] || poms+=("$p"); done < <(
        find examples -name pom.xml -not -path '*/target/*' -not -path '*/node_modules/*' | sort | while read -r p; do
            # only top-level poms of a build: skip modules listed by a parent pom in the directory above
            parent="$(dirname "$(dirname "$p")")/pom.xml"
            if [[ -f "$parent" ]] && grep -q "<module>$(basename "$(dirname "$p")")</module>" "$parent"; then continue; fi
            echo "$p"
        done)
    if [[ $list -eq 1 ]]; then printf '%s\n' "${poms[@]}"; exit 0; fi
    for p in "${poms[@]}"; do
        name="mvn:$(dirname "$p")"; log="$RUN_DIR/$(dirname "$p" | tr / _).mvn.log"
        goal=verify; [[ "$p" == examples/domain-model/pom.xml ]] && goal=install
        echo "==> $name ($goal)"
        t0=$SECONDS
        (cd "$(dirname "$p")" && mvn -B -ntp "$goal") 2>&1 | scrub > "$log"
        rc=${PIPESTATUS[0]}
        tests="$(grep -E 'Tests run: [0-9]+, Failures' "$log" | tail -1 | sed -E 's/.*Tests run: ([0-9]+), Failures: ([0-9]+), Errors: ([0-9]+).*/\1 run, \2 failed, \3 errors/')"
        printf '%s\t%s\t%s\t%s\t%s\n' "$name" "${tests:-no tests}" "$rc" "$((SECONDS - t0))" "-" >> "$TSV"
        if [[ $rc -ne 0 ]]; then echo "  FAIL $name (log $log)"; echo "verify-all: results in $TSV"; exit 1; fi
    done
    echo "verify-all: ${#poms[@]} poms passed; results in $TSV"
    exit 0
fi

# --- verify.sh groups ---------------------------------------------------------------------------------------
# Each entry: <dir>[:<mode>]
entries=()
case "$group" in
    compose) for d in examples/[0-2][0-9]-*/; do d="${d%/}"; d="${d#examples/}"; [[ "$d" < "28" ]] && entries+=("$d"); done ;;
    k8s) for d in examples/2[8-9]-*/ examples/3[0-9]-*/; do d="${d%/}"; entries+=("${d#examples/}"); done ;;
    k8s-appendix) entries=(ab-compose-to-kubernetes:k8s af-deploying-event-driven-services) ;;
    appendix) entries=(aa-testing-event-driven-code ab-compose-to-kubernetes:compose ac-querying-streams-without-ksqldb
                       ae-resequencing-across-partitions ag-write-forward-cache ab-compose-to-kubernetes:compare) ;;
esac

if [[ -n "$from" ]]; then
    keep=() on=0
    for e in "${entries[@]}"; do [[ "${e%%:*}" == "$from" || "$e" == "$from" ]] && on=1; [[ $on -eq 1 ]] && keep+=("$e"); done
    [[ ${#keep[@]} -gt 0 ]] || { echo "verify-all: --from $from is not in group $group" >&2; exit 2; }
    entries=("${keep[@]}")
fi
if [[ $list -eq 1 ]]; then printf '%s\n' "${entries[@]}"; exit 0; fi

leftovers() { # compose containers and volumes still present (tooling containers and the model volume excepted)
    local c v
    c="$(docker ps -a --filter label=com.docker.compose.project --format '{{.Names}}' | grep -cv '^quarkus-agent-mcp' || true)"
    v="$(docker volume ls -q --filter label=com.docker.compose.project | grep -cvx 'edp-ollama-models' || true)"
    if [[ "$c" == 0 && "$v" == 0 ]]; then echo clean; else echo "LEFT ${c} container(s), ${v} volume(s)"; fi
}

passed=()
for e in "${entries[@]}"; do
    dir="${e%%:*}"; mode=""; [[ "$e" == *:* ]] && mode="${e#*:}"
    v="examples/$dir/verify.sh"
    [[ -f "$v" ]] || { echo "verify-all: $v missing" >&2; exit 2; }
    name="$dir${mode:+:$mode}"; log="$RUN_DIR/${dir}${mode:+-$mode}.log"
    echo "==> $name"
    t0=$SECONDS
    (cd "examples/$dir" && ./verify.sh ${mode:+"$mode"}) 2>&1 | scrub > "$log"
    rc=${PIPESTATUS[0]}
    wall=$((SECONDS - t0))
    checks="$(grep -Eo '[0-9]+/[0-9]+ checks passed' "$log" | tail -1 | cut -d' ' -f1)"
    case "$group" in
        k8s|k8s-appendix) td="$(grep -Eo 'deleted in [0-9]+ s' "$log" | tail -1 | grep -Eo '[0-9]+')"; td="${td:+${td} s}" ;;
        *) td="$(leftovers)" ;;
    esac
    printf '%s\t%s\t%s\t%s\t%s\n' "$name" "${checks:--}" "$rc" "$wall" "${td:--}" >> "$TSV"
    echo "    ${checks:-no summary} exit $rc ${wall} s teardown ${td:--}"
    if [[ $rc -ne 0 ]]; then
        echo; echo "verify-all: ${#passed[@]} passed"; printf '  ok   %s\n' "${passed[@]}"
        echo "  FAIL $name (log $log)"; echo "verify-all: results in $TSV"; exit 1
    fi
    passed+=("$name")
done
echo; echo "verify-all: ${#passed[@]} passed"; printf '  ok   %s\n' "${passed[@]}"
echo "verify-all: results in $TSV"
