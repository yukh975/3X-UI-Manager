#!/usr/bin/env python3
"""Verify every in-document #anchor link resolves, the way GitHub renders it.

Uses CommonMark-correct fences (an info-string fence ```lang only OPENS; only a
bare ``` of >= the opener length closes) so headings hidden inside code blocks are
excluded from BOTH the anchor set and the link scan — matching GitHub. Anchors use
GitHub's slug (lowercase; keep alnum/`-`/`_`/ZWNJ/ZWJ; spaces->-; drop other
punctuation; duplicates get -1,-2 in document order).

Exit code is non-zero if any link is broken. Usage: python3 scripts/lib/checklinks.py
"""
import sys, re, glob, unicodedata

KEEP = "-_‌‍"
fence_re = re.compile(r"^(\s*)(`{3,}|~{3,})(.*)$")


def slug(text, seen):
    s = "".join(c if (c in KEEP or unicodedata.category(c)[0] in "LMN")
                else ("-" if c.isspace() else "") for c in text.strip().lower())
    n = seen.get(s, 0); seen[s] = n + 1
    return s if n == 0 else f"{s}-{n}"


def live_lines(lines):
    """Yield (lineno, text) for lines NOT inside a fenced code block."""
    inside = False; marker = ""; olen = 0
    for i, ln in enumerate(lines):
        m = fence_re.match(ln)
        if m:
            ticks, info = m.group(2), m.group(3).strip()
            if not inside:
                inside = True; marker = ticks[0]; olen = len(ticks)
            elif ticks[0] == marker and len(ticks) >= olen and info == "":
                inside = False
            continue
        if not inside:
            yield i, ln


def check(path):
    lines = open(path, encoding="utf-8").read().split("\n")
    live = list(live_lines(lines))
    seen = {}; anchors = set()
    for _, ln in live:
        m = re.match(r"#{1,6}\s+(.*)", ln)
        if m:
            anchors.add(slug(m.group(1), seen))
    broken = []
    for _, ln in live:
        for tgt in re.findall(r"\]\(#([^)]+)\)", ln):
            if tgt not in anchors:
                broken.append(tgt)
    return broken


bad = 0
for path in sorted(glob.glob("3X-UI-MANAGER.*.md")):
    code = path.split("MANAGER.")[1][:-3]
    broken = check(path)
    bad += len(broken)
    mark = "OK" if not broken else f"BROKEN {len(broken)}"
    print(f"  {code:6} {mark}")
    for b in broken:
        print(f"        #{b}")
sys.exit(1 if bad else 0)
