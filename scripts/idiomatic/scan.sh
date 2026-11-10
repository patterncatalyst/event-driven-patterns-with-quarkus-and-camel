#!/usr/bin/env bash
# Scan example / tutorial code for high-signal, non-idiomatic anti-patterns.
#
#   scan.sh [options] PATH...         # summary (files and/or directories): hits per pattern, worst first
#   scan.sh --files PATH              # hits per file, worst first (for splitting a sweep)
#   scan.sh --hits on_event PATH     # file:line excerpts for patterns whose label matches
#   scan.sh --hits all --tier ban PATH
#
# Options:
#   --tier ban|watch|all   which tier to report (default: all)
#                          ban   = clear anti-pattern, fix by default
#                          watch = review by hand; many are legitimate in context
#   --hits LABEL           print excerpts for patterns whose label contains LABEL ("all" = every pattern)
#   --files                print per-file hit totals instead of per-pattern
#   --ext LIST             comma-separated extensions to scan; a leading + appends to the default
#                          (default: py,java,yaml,yml,properties,xml,tpl,cs,csproj,go,
#                          cpp,cc,cxx,hpp,hh,hxx,h,cmake; e.g. --ext +md to also scan code
#                          fenced inside Markdown tutorial pages)
#   --exclude NAME         prune a directory or file name (repeatable); _plans is pruned by default
#   --patterns FILE        extra TSV patterns (same format as patterns.tsv); repeatable
#   --fail                 exit 1 when any ban-tier pattern hits (pre-commit / CI gate)
#
# Generated code (*.pb.go, *.pb.cc, *.pb.h, *.g.cs, *.Designer.cs) and build output
# (target, build, bin, obj, cmake-build-*) are skipped.
#
# Read-only: the tree is never modified. This scanner is deliberately
# conservative — it carries only anti-patterns that rarely false-positive.
# Everything nuanced (a missing response_model, a wrong probe, field injection)
# is left to the per-stack checklists in ../references/, not grepped.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
pattern_files=("$here/patterns.tsv")
tier=all; hits=""; mode=summary; fail=0
default_exts="py,java,yaml,yml,properties,xml,tpl,cs,csproj,go,cpp,cc,cxx,hpp,hh,hxx,h,cmake"
exts="$default_exts"
excludes=(_plans)
roots=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --tier)     tier="$2"; shift 2 ;;
    --hits)     hits="$2"; mode=hits; shift 2 ;;
    --files)    mode=files; shift ;;
    --ext)      if [[ "$2" == +* ]]; then exts="$default_exts,${2#+}"; else exts="$2"; fi; shift 2 ;;
    --exclude)  excludes+=("$2"); shift 2 ;;
    --patterns) pattern_files+=("$2"); shift 2 ;;
    --fail)     fail=1; shift ;;
    -h|--help)  sed -n '2,29p' "$0" | sed 's/^# \?//'; exit 0 ;;
    -*)         echo "unknown option: $1" >&2; exit 2 ;;
    *)          roots+=("$1"); shift ;;
  esac
done
[[ ${#roots[@]} -gt 0 ]] || { echo "usage: scan.sh [options] PATH... (see --help)" >&2; exit 2; }
for r in "${roots[@]}"; do [[ -e "$r" ]] || { echo "no such file or directory: $r" >&2; exit 2; }; done
case "$tier" in ban|watch|all) ;; *) echo "--tier must be ban, watch, or all" >&2; exit 2 ;; esac

# UTF-8 so non-ASCII in source files matches predictably.
if locale -a 2>/dev/null | grep -qi '^c\.utf-\?8$'; then export LC_ALL=C.UTF-8; else export LC_ALL=en_US.UTF-8; fi

mirror="$(mktemp -d)"
# The hit list lives outside the mirror: inside it, grep would read its own output back.
out="$(mktemp)"
trap 'rm -rf "$mirror" "$out"' EXIT

# --- Build the mirror: candidate files only (copied verbatim; code is the subject).
# Container build files have no extension, and CMakeLists.txt shares .txt with prose,
# so match them by name as well.
find_args=()
IFS=',' read -ra ext_list <<< "$exts"
for e in "${ext_list[@]}"; do find_args+=(-o -name "*.$e"); done
find_args+=(-o -name Containerfile -o -name Dockerfile -o -name CMakeLists.txt)
find_args=("${find_args[@]:1}")
prune_args=()
for x in .git node_modules _site vendor target dist build .bundle .jekyll-cache \
         __pycache__ .venv venv .sass-cache bin obj 'cmake-build-*' "${excludes[@]}"; do
  [[ -n "$x" ]] && prune_args+=(-o -name "$x")
done
prune_args=("${prune_args[@]:1}")

while IFS= read -r -d '' f; do
  if [[ ${#roots[@]} -eq 1 && -d "${roots[0]}" ]]; then
    rel="$(realpath --relative-to="${roots[0]}" "$f")"
  else
    rel="$(realpath --relative-to=. "$f")"; rel="${rel#../}"; rel="${rel//..\//}"
  fi
  mkdir -p "$mirror/$(dirname "$rel")"
  cp "$f" "$mirror/$rel"
done < <(find "${roots[@]}" \( "${prune_args[@]}" \) -prune \
  -o -type f \( "${find_args[@]}" \) ! -name '*.min.js' ! -name 'package-lock.json' \
  ! -name '*.pb.go' ! -name '*.pb.cc' ! -name '*.pb.h' ! -name '*.g.cs' ! -name '*.Designer.cs' -print0)

# --- Load patterns: tier \t case \t label \t regex [\t only [\t if]]
tiers=(); cases=(); labels=(); regexes=(); onlys=(); ifs=()
for pf in "${pattern_files[@]}"; do
  [[ -f "$pf" ]] || { echo "patterns file not found: $pf" >&2; exit 2; }
  while IFS=$'\t' read -r t c l r x y; do
    [[ -z "$t" || "$t" == \#* ]] && continue
    [[ "$tier" != all && "$t" != "$tier" ]] && continue
    [[ "${x:-}" == - ]] && x=""
    tiers+=("$t"); cases+=("$c"); labels+=("$l"); regexes+=("$r"); onlys+=("${x:-}"); ifs+=("${y:-}")
  done < "$pf"
done

grep_pat() {  # grep_pat INDEX [-o]  -- with -o, print a ~70-char excerpt around each hit
  local i="$1" re="${regexes[$1]}" opt=()
  local ci=(); [[ "${cases[$i]}" == i ]] && ci=(-i)
  local inc=()  # optional 5th column: extensions (py) or exact file names (CMakeLists.txt)
  if [[ -n "${onlys[$i]}" ]]; then
    local e; IFS=',' read -ra _exts <<< "${onlys[$i]}"
    for e in "${_exts[@]}"; do
      if [[ "$e" == *.* ]]; then inc+=("--include=$e"); else inc+=("--include=*.$e"); fi
    done
  fi
  if [[ "${2:-}" == -o ]]; then opt=(-o); re=".{0,70}(${re}).{0,70}"; fi
  if [[ -n "${ifs[$i]}" ]]; then
    # optional 6th column: only files that also match this ERE (e.g. a framework import)
    local -a in_scope=()
    mapfile -t in_scope < <(grep -rlE "${inc[@]}" -e "${ifs[$i]}" "$mirror" 2>/dev/null || true)
    [[ ${#in_scope[@]} -eq 0 ]] && return 0
    grep -HnE "${ci[@]}" "${opt[@]}" -e "$re" -- "${in_scope[@]}" 2>/dev/null || true
    return 0
  fi
  grep -rnE "${ci[@]}" "${opt[@]}" "${inc[@]}" -e "$re" "$mirror" 2>/dev/null || true
}

ban_hits=0
: > "$out"

case "$mode" in
  summary)
    for i in "${!labels[@]}"; do
      res="$(grep_pat "$i")"
      [[ -z "$res" ]] && continue
      n=$(printf '%s\n' "$res" | wc -l)
      nf=$(printf '%s\n' "$res" | cut -d: -f1 | sort -u | wc -l)
      [[ "${tiers[$i]}" == ban ]] && ban_hits=$((ban_hits + n))
      printf '%6d\t%5d\t%-5s\t%s\n' "$n" "$nf" "${tiers[$i]}" "${labels[$i]}" >> "$out"
    done
    printf '%6s\t%5s\t%-5s\t%s\n' LINES FILES TIER PATTERN
    sort -t$'\t' -k1,1nr "$out"
    total=$(awk -F'\t' '{s+=$1} END{print s+0}' "$out")
    echo "--"
    echo "total: $total matching lines ($ban_hits ban-tier) under ${roots[*]}"
    ;;
  files)
    for i in "${!labels[@]}"; do
      res="$(grep_pat "$i")"
      [[ -z "$res" ]] && continue
      printf '%s\n' "$res" | cut -d: -f1 >> "$out"
      [[ "${tiers[$i]}" == ban ]] && ban_hits=$((ban_hits + $(printf '%s\n' "$res" | wc -l)))
    done
    printf '%6s\t%s\n' HITS FILE
    sed "s|^$mirror/||" "$out" | sort | uniq -c | sort -k1,1nr | awk '{printf "%6d\t%s\n", $1, $2}'
    ;;
  hits)
    for i in "${!labels[@]}"; do
      [[ "$hits" != all && "${labels[$i],,}" != *"${hits,,}"* ]] && continue
      res="$(grep_pat "$i" -o | sed "s|^$mirror/||")"
      [[ -z "$res" ]] && continue
      [[ "${tiers[$i]}" == ban ]] && ban_hits=$((ban_hits + $(printf '%s\n' "$res" | wc -l)))
      echo "## [${tiers[$i]}] ${labels[$i]}"
      printf '%s\n' "$res" | awk -F: '!seen[$1":"$2]++'
      echo
    done
    ;;
esac

[[ $fail -eq 1 && $ban_hits -gt 0 ]] && exit 1
exit 0
