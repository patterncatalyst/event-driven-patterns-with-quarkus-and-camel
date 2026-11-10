#!/usr/bin/env python3
"""House-voice measurements the pattern scanner cannot make.

Called by `scan.sh --stats` on its prose-only mirror (fenced and inline code
already blanked), one row per file:

    WORDS     prose words
    CONTR     contracted forms / (contracted + expanded) for the common pairs
              ("don't" / "do not", "it's" / "it is", ...); house voice is contracted
    SENT      sentences
    LEN       sentence length in words: median and p10-p90 spread; a narrow
              spread on a long file reads as uniform, generated cadence
    TRIAD     candidate rhetorical triads ("X, Y, and Z" of short items, or one
              word opening three parallel phrases)
    DASH      em dashes (zero tolerance)

`--hits` prints the triad candidates and expanded forms with file:line, for a
reviewer to judge: a list of three real things is not a rhetorical triad.

`--terse` measures the terse pattern register instead (`scan.sh --terse-stats`,
which passes a raw mirror: fences and inline code intact). Markdown only, one
row per file, then the median row:

    WORDS     prose words (code, tables, headings, Liquid, HTML, quotes excluded)
    SECMED    median prose words per H2 section (target <= 150)
    LEAD      median prose words before a section's first code block, table
              or figure (target <= 60)
    PAR       median prose paragraph words, list items left out (target <= 40)
    ABSTR/k   abstraction words per 1,000 prose words: force(s), intent,
              decision(s), design, assumption(s), trade-off(s), ... (<= 1.5)
    TESTS     backticked `test_*` names on prose lines, counted in the raw
              Markdown; a line that starts `*Tests:*` doesn't count (target 0)

Spine sections (objectives, deeper coverage, references, build/run/observe,
what you can do now) are left out of SECMED and LEAD. With `--hits`, the
TESTS lines are printed as file:line.
Stdlib only.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

PAIRS = {
    r"do not": "don't", r"does not": "doesn't", r"did not": "didn't",
    r"is not": "isn't", r"are not": "aren't", r"was not": "wasn't",
    r"were not": "weren't", r"cannot": "can't", r"can not": "can't",
    r"will not": "won't", r"would not": "wouldn't", r"should not": "shouldn't",
    r"could not": "couldn't", r"has not": "hasn't", r"have not": "haven't",
    r"it is": "it's", r"that is": "that's", r"there is": "there's",
    r"you are": "you're", r"they are": "they're", r"we are": "we're",
    r"let us": "let's",
}  # fmt: skip
EXPANDED = re.compile(r"\b(" + "|".join(PAIRS) + r")\b", re.IGNORECASE)
CONTRACTED = re.compile(
    r"\b(" + "|".join(sorted({re.escape(v) for v in PAIRS.values()})) + r")",
    re.IGNORECASE,
)
# "that is" and "there is" are often not contractible ("the value that is read");
# count them as expanded only when they open a clause.
WEAK = re.compile(r"\b(that is|there is|it is)\b", re.IGNORECASE)

WORD = re.compile(r"[A-Za-z][A-Za-z'’-]*")
SENT_END = re.compile(r"(?<=[.!?])\s+(?=[A-Z(\"'*_])")
TRIAD = re.compile(
    r"\b(\w+(?: \w+){0,2}), (\w+(?: \w+){0,2}),? (?:and|or) (\w+(?: \w+){0,2})\b"
)
ANAPHORA = re.compile(r"\b(\w+ \w+) [^,;.]{1,30}, \1 [^,;.]{1,30}, (?:and )?\1 ")
DASH = "—"


def prose_lines(text: str) -> list[tuple[int, str]]:
    """Lines that are prose: no front matter, Liquid tags, tables, headings, HTML."""
    out, in_front = [], False
    for n, line in enumerate(text.splitlines(), 1):
        s = line.strip()
        if n == 1 and s == "---":
            in_front = True
            continue
        if in_front:
            in_front = s != "---"
            continue
        if not s or s.startswith(("#", "|", "<", "{%", "{{", ">")):
            continue
        out.append((n, s.lstrip("-*0123456789. ")))
    return out


def percentile(values: list[int], q: float) -> int:
    if not values:
        return 0
    v = sorted(values)
    return v[min(len(v) - 1, int(q * (len(v) - 1) + 0.5))]


def measure(path: Path) -> tuple[dict, list[str]]:
    text = path.read_text(encoding="utf-8", errors="replace")
    lines = prose_lines(text)
    prose = " ".join(s for _, s in lines)
    words = len(WORD.findall(prose))
    expanded = [(n, m.group(0)) for n, s in lines for m in EXPANDED.finditer(s)]
    expanded = [
        (n, e)
        for n, e in expanded
        if not (WEAK.fullmatch(e) and not _opens(lines, n, e))
    ]
    contracted = len(CONTRACTED.findall(prose.replace("’", "'")))
    sentences = [s for s in SENT_END.split(prose) if WORD.search(s)]
    lens = [len(WORD.findall(s)) for s in sentences]
    triads = [
        (n, m.group(0))
        for n, s in lines
        for m in list(TRIAD.finditer(s)) + list(ANAPHORA.finditer(s))
        if not re.search(r"\d", m.group(0))  # p50, p99 and p99.9 is data, not cadence
    ]
    dashes = text.count(DASH)
    total = contracted + len(expanded)
    row = {
        "words": words,
        "contr": f"{contracted}/{total}" if total else "-",
        "ratio": contracted / total if total else None,
        "sent": len(sentences),
        "len": f"{percentile(lens, 0.5)} ({percentile(lens, 0.1)}-{percentile(lens, 0.9)})",
        "spread": percentile(lens, 0.9) - percentile(lens, 0.1),
        "triad": len(triads),
        "dash": dashes,
    }
    hits = [f"{path}:{n}: expanded: {e}" for n, e in expanded]
    hits += [f"{path}:{n}: triad?: {t}" for n, t in triads]
    return row, hits


def _opens(lines: list[tuple[int, str]], n: int, phrase: str) -> bool:
    """True when the weak phrase starts a sentence or clause on that line."""
    for ln, s in lines:
        if ln == n:
            return bool(
                re.search(rf"(^|[.;:]\s+){re.escape(phrase)}\b", s, re.IGNORECASE)
            )
    return False


# --- Terse pattern register (--terse)

ABSTRACT = re.compile(
    r"\b(forces?|intent|thesis|theses|assumptions?|registers?|notions?|essentially"
    r"|in the sense that|what matters|the point is|design|decisions?|trade-offs?"
    r"|temporal)\b",
    re.IGNORECASE,
)
SPINE = re.compile(
    r"objectives|deeper coverage|references|further reading|bibliography"
    r"|build, run|what you (can do now|learned)|where this leaves you|take-aways",
    re.IGNORECASE,
)
FENCE = re.compile(r"^\s*(```|~~~)")
HL_OPEN = re.compile(r"\{%-?\s*highlight")
HL_CLOSE = re.compile(r"\{%-?\s*endhighlight")
FIGURE = re.compile(
    r"\{%-?\s*include\s+\S*(code|fig|excalidraw|diagram|image|img|svg|mermaid)"
    r"|<(figure|img|object|svg)\b|!\[",
    re.IGNORECASE,
)
TEST_NAME = re.compile(r"`test_\w+")
TESTS_LINE = re.compile(r"^\s*[-*]?\s*\*Tests?:\*")
LIST_ITEM = re.compile(r"^\s*([-*+]|\d+\.)\s")


def median(values: list[float]) -> float:
    if not values:
        return 0
    v = sorted(values)
    m = len(v) // 2
    return v[m] if len(v) % 2 else (v[m - 1] + v[m]) / 2


def terse_measure(path: Path) -> tuple[dict, list[str]]:
    """Section, lead-in, paragraph, abstraction and test-name counts for one doc."""
    text = path.read_text(encoding="utf-8", errors="replace")
    sections: list[dict] = []  # H2 sections: words, lead-in words, device seen
    sec = None
    paras: list[int] = []
    para = 0
    words = abstr = 0
    tests: list[tuple[int, int]] = []
    in_front = in_fence = in_hl = in_list = False

    def end_para() -> None:
        nonlocal para
        if para:
            paras.append(para)
        para = 0

    for n, line in enumerate(text.splitlines(), 1):
        s = line.strip()
        if n == 1 and s == "---":
            in_front = True
            continue
        if in_front:
            in_front = s != "---"
            continue
        device = False
        if FENCE.match(line):
            device = not in_fence
            in_fence = not in_fence
        elif HL_OPEN.search(line):
            device, in_hl = True, True
        elif HL_CLOSE.search(line):
            in_hl = False
        elif in_fence or in_hl:
            continue
        elif s.startswith("|") or FIGURE.search(s):
            device = True
        if device:
            end_para()
            if sec is not None:
                sec["dev"] = True
            continue
        if FENCE.match(line) or HL_CLOSE.search(line):
            continue
        if s.startswith("## "):
            end_para()
            sec = {"spine": bool(SPINE.search(s)), "words": 0, "lead": 0, "dev": False}
            sections.append(sec)
            continue
        if not s or s.startswith(("#", "<", "{%", "{{", ">")):
            end_para()
            in_list = False
            continue
        k = len(TEST_NAME.findall(s))
        if k and not TESTS_LINE.match(s):
            tests.append((n, k))
        if LIST_ITEM.match(line):
            end_para()
            in_list = True
        prose = re.sub(r"`[^`]*`", "", s.lstrip("-*+0123456789. "))
        w = len(WORD.findall(prose))
        words += w
        if not in_list:
            para += w
        abstr += len(ABSTRACT.findall(prose))
        if sec is not None:
            sec["words"] += w
            if not sec["dev"]:
                sec["lead"] += w
    end_para()
    body = [s for s in sections if not s["spine"] and s["words"]]
    row = {
        "words": words,
        "secmed": median([s["words"] for s in body]),
        "lead": median([s["lead"] for s in body]),
        "par": median(paras),
        "abstr": 1000 * abstr / words if words else 0.0,
        "tests": sum(k for _, k in tests),
    }
    hits = [f"{path}:{n}: test names in prose: {k}" for n, k in tests]
    return row, hits


def terse_main(root: Path, show_hits: bool) -> int:
    files = sorted(
        p for p in root.rglob("*") if p.is_file() and p.suffix in {".md", ".markdown"}
    )
    print(f"{'WORDS':>6} {'SECMED':>6} {'LEAD':>5} {'PAR':>5} {'ABSTR/k':>7} "
          f"{'TESTS':>5}  FILE  FLAGS")  # fmt: skip
    rows, all_hits = [], []
    for f in files:
        row, hits = terse_measure(f)
        if row["words"] < 150:
            continue
        rows.append(row)
        flags = []
        if row["secmed"] > 150:
            flags.append("long sections")
        if row["lead"] > 60:
            flags.append("late code")
        if row["par"] > 40:
            flags.append("long paragraphs")
        if row["abstr"] > 1.5:
            flags.append("abstract")
        if row["tests"]:
            flags.append("tests in prose")
        print(
            f"{row['words']:>6} {row['secmed']:>6g} {row['lead']:>5g} {row['par']:>5g} "
            f"{row['abstr']:>7.1f} {row['tests']:>5}  {f.relative_to(root)}  "
            f"{'; '.join(flags)}"
        )
        all_hits += [h.replace(f"{root}/", "", 1) for h in hits]
    if rows:
        med = {k: median([r[k] for r in rows]) for k in rows[0]}
        print("--")
        print(
            f"{med['words']:>6g} {med['secmed']:>6g} {med['lead']:>5g} {med['par']:>5g} "
            f"{med['abstr']:>7.1f} {sum(r['tests'] for r in rows):>5}  "
            f"median of {len(rows)} files (TESTS: total)"
        )
    if show_hits and all_hits:
        print()
        print("\n".join(all_hits))
    return 0


def main() -> int:
    args = sys.argv[1:]
    show_hits = "--hits" in args
    terse = "--terse" in args
    args = [a for a in args if a not in ("--hits", "--terse")]
    if len(args) != 1:
        print("usage: voice_stats.py [--hits] [--terse] MIRROR_DIR", file=sys.stderr)
        return 2
    root = Path(args[0])
    if terse:
        return terse_main(root, show_hits)
    files = sorted(
        p
        for p in root.rglob("*")
        if p.is_file() and p.suffix in {".md", ".markdown", ".html"}
    )
    print(f"{'WORDS':>6} {'CONTR':>8} {'SENT':>5} {'LEN med (p10-p90)':>18} "
          f"{'TRIAD':>5} {'DASH':>4}  FILE  FLAGS")  # fmt: skip
    all_hits = []
    for f in files:
        row, hits = measure(f)
        if row["words"] < 150:
            continue
        flags = []
        if row["ratio"] is not None and row["ratio"] < 0.5 and row["contr"] != "-":
            flags.append("stiff: few contractions")
        if row["sent"] >= 20 and row["spread"] < 10:
            flags.append("uniform sentence length")
        if row["triad"]:
            flags.append("check triads")
        if row["dash"]:
            flags.append("em dash")
        rel = f.relative_to(root)
        print(
            f"{row['words']:>6} {row['contr']:>8} {row['sent']:>5} {row['len']:>18} "
            f"{row['triad']:>5} {row['dash']:>4}  {rel}  {'; '.join(flags)}"
        )
        all_hits += [h.replace(f"{root}/", "", 1) for h in hits]
    if show_hits and all_hits:
        print()
        print("\n".join(all_hits))
    return 0


if __name__ == "__main__":
    sys.exit(main())
