# Vendored from the lgtm-diagram-generator skill (scripts/svg_to_png.py), unchanged
# except for this header. Pinned run: uv run --no-project --with cairosvg==2.9.1.
# Do not edit here; fix the skill and copy it again.
#!/usr/bin/env python3
"""Render a generated diagram SVG to PNG for a deck or a document.

cairosvg uses only the first family in a font-family list and does not fall back per
glyph. Red Hat Text has no arrow glyphs, so a label such as "execute(C1) → execute(C2)"
renders the arrows as boxes. This script finds every <text> element that holds such a
glyph and sets that element (only) in a sans font that has it, chosen by fontconfig.
The SVG on disk is not changed; browsers already fall back per glyph.

Usage:
    uv run --no-project --quiet --with cairosvg python svg_to_png.py IN.svg OUT.png [WIDTH]
WIDTH defaults to 1600 (deck slides); use 1400 for DOCX figures.
"""

import re
import subprocess
import sys

import cairosvg

# Glyphs Red Hat Text lacks that labels commonly use.
GLYPHS = "→←↔⇒⇐✓✗"
GLYPH_RE = re.compile(f"[{GLYPHS}]")
TEXT_RE = re.compile(r"<text\b[^>]*>[^<]*(?:<tspan[^>]*>[^<]*</tspan>[^<]*)*</text>")


def fallback_family() -> str:
    """A sans family that has U+2192, from fontconfig; DejaVu Sans if fc-match is absent."""
    try:
        out = subprocess.run(
            ["fc-match", "--format=%{family[0]}", "sans-serif:charset=2192"],
            capture_output=True,
            text=True,
            check=True,
        ).stdout.strip()
        return out or "DejaVu Sans"
    except (OSError, subprocess.CalledProcessError):
        return "DejaVu Sans"


def with_glyph_fallback(svg: str, family: str) -> str:
    def fix(m: re.Match) -> str:
        el = m.group(0)
        if not GLYPH_RE.search(el):
            return el
        head, rest = el.split(">", 1)
        head = re.sub(r'\sfont-family="[^"]*"', "", head)
        return f'{head} font-family="{family}">{rest}'

    return TEXT_RE.sub(fix, svg)


def svg_to_png(svg_path: str, png_path: str, width: int = 1600) -> None:
    svg = open(svg_path, encoding="utf-8").read()
    if GLYPH_RE.search(svg):
        svg = with_glyph_fallback(svg, fallback_family())
    cairosvg.svg2png(bytestring=svg.encode("utf-8"), write_to=png_path, output_width=width)


if __name__ == "__main__":
    if len(sys.argv) not in (3, 4):
        sys.exit(__doc__)
    svg_to_png(sys.argv[1], sys.argv[2], int(sys.argv[3]) if len(sys.argv) == 4 else 1600)
