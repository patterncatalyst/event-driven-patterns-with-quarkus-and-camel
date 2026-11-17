#!/usr/bin/env bash
#
# deck_render.sh - render slides of a .pptx to PNG for visual inspection.
# Adapted from cloud-native-design-patterns scripts/deck_render.sh.
#
#   scripts/deck_render.sh <deck.pptx> [outdir] [page ...]
#
# soffice (headless) converts the deck to PDF, pdftoppm writes <outdir>/<deck>-<page>.png at 90 dpi
# (all pages, or only the listed 1-based pages). outdir defaults to $TMPDIR/deck_render/out
# (TMPDIR defaults to /tmp) and is never inside the repository. Refuses to run unless Red Hat
# Display, Red Hat Text and Red Hat Mono are installed, because a render with substituted fonts
# hides overflow.
# Environment: LO_PROFILE sets the LibreOffice profile directory
# (default: $TMPDIR/deck_render/lo-profile).
set -euo pipefail

if [[ $# -lt 1 ]]; then
    echo "usage: $0 <deck.pptx> [outdir] [page ...]" >&2
    exit 2
fi

deck=$1
shift
tmproot=${TMPDIR:-/tmp}
outdir=${1:-$tmproot/deck_render/out}
[[ $# -gt 0 ]] && shift
pages=("$@")

if [[ ! -f "$deck" ]]; then
    echo "deck_render: $deck not found" >&2
    exit 2
fi

repo=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
case "$(realpath -m "$outdir")/" in
    "$repo"/*)
        echo "deck_render: outdir must be outside the repository" >&2
        exit 2
        ;;
esac
mkdir -p "$outdir"

fonts=$(fc-list 2>/dev/null || true)
missing=()
for f in "Red Hat Display" "Red Hat Text" "Red Hat Mono"; do
    if ! grep -qi -- "$f" <<<"$fonts"; then
        missing+=("$f")
    fi
done
if [[ ${#missing[@]} -gt 0 ]]; then
    echo "deck_render: missing brand font(s): ${missing[*]}" >&2
    echo "Install the Red Hat Display, Text and Mono font packages first." >&2
    exit 3
fi

profile=${LO_PROFILE:-$tmproot/deck_render/lo-profile}
mkdir -p "$profile"
tmp=$(mktemp -d "$tmproot/deck_render.XXXXXX")
trap 'rm -rf "$tmp"' EXIT

soffice --headless "-env:UserInstallation=file://$profile" --convert-to pdf --outdir "$tmp" "$deck" >/dev/null
base=$(basename "${deck%.*}")
pdf="$tmp/$base.pdf"
if [[ ! -f "$pdf" ]]; then
    echo "deck_render: soffice produced no PDF for $deck" >&2
    exit 1
fi

if [[ ${#pages[@]} -eq 0 ]]; then
    pdftoppm -png -r 90 "$pdf" "$outdir/$base"
else
    for n in "${pages[@]}"; do
        pdftoppm -png -r 90 -f "$n" -l "$n" "$pdf" "$outdir/$base"
    done
fi
echo "deck_render: wrote PNGs for $base in $outdir"
