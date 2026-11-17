#!/usr/bin/env python3
"""Extract slide text and speaker notes from a .pptx, standard library only.

    python3 notes.py deck.pptx            speaker notes, one block per slide
    python3 notes.py --slides deck.pptx   slide text, one block per slide

pptxgenjs writes a notes slide for every slide, empty ones included, so a
count of notes files proves nothing. This reads the text runs of each notes
slide and drops the trailing slide-number field. Output feeds the voice scan.
"""

from __future__ import annotations

import re
import sys
import zipfile
from xml.etree import ElementTree

NS = {"a": "http://schemas.openxmlformats.org/drawingml/2006/main"}
PART = re.compile(r"ppt/(slides/slide|notesSlides/notesSlide)(\d+)\.xml$")


def paragraphs(xml: bytes) -> list[str]:
    root = ElementTree.fromstring(xml)
    lines = []
    for para in root.iter(f"{{{NS['a']}}}p"):
        text = "".join(t.text or "" for t in para.iter(f"{{{NS['a']}}}t"))
        if text.strip():
            lines.append(text)
    return lines


def extract(path: str, kind: str) -> dict[int, list[str]]:
    found: dict[int, list[str]] = {}
    with zipfile.ZipFile(path) as deck:
        for name in deck.namelist():
            match = PART.match(name)
            if match is None:
                continue
            is_notes = match.group(1).startswith("notes")
            if is_notes != (kind == "notes"):
                continue
            lines = paragraphs(deck.read(name))
            if is_notes and lines and lines[-1].strip().isdigit():
                lines = lines[:-1]  # the slide-number field
            found[int(match.group(2))] = lines
    return dict(sorted(found.items()))


def main(argv: list[str]) -> int:
    kind = "notes"
    args = argv[1:]
    if args and args[0] == "--slides":
        kind = "slides"
        args = args[1:]
    if len(args) != 1:
        print("usage: notes.py [--slides] deck.pptx", file=sys.stderr)
        return 2
    for number, lines in extract(args[0], kind).items():
        print(f"--- slide {number}")
        print("\n".join(lines))
        print()
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
