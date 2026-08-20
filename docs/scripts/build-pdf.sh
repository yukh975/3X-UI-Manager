#!/usr/bin/env bash
# Build PDF versions of the manual from the Markdown sources, one per language.
#
#   pandoc  : Markdown (GFM) -> HTML fragment (GitHub-style anchors).
#   render  : WeasyPrint (preferred — paged media: page numbers + footer),
#             else headless Google Chrome (no page numbers).
#
# Inputs : 3X-UI-MANAGER.<code>.md   (ru, en, uk, zh-CN, zh-TW, fa, ar, tr, pt, es, ja, id, vi)
# Outputs: pdf/3X-UI-MANAGER.<code>.pdf
#
# ar/fa render right-to-left; the font stack covers Latin/Cyrillic/CJK/Arabic so
# every language's glyphs resolve (per-glyph fallback via fontconfig).
#
# Deps: brew install pandoc ; WeasyPrint (brew install pango + ~/.venv-pdf with
#       `pip install weasyprint`) or Google Chrome.
set -euo pipefail
cd "$(dirname "$0")/.."

WEASY="${WEASY:-$HOME/.venv-pdf/bin/weasyprint}"
CHROME="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
command -v pandoc >/dev/null || { echo "pandoc not found (brew install pandoc)" >&2; exit 1; }

FONTS='"PT Sans","Helvetica Neue",Arial,"Noto Sans","PingFang SC","PingFang TC","Hiragino Sans","Hiragino Kaku Gothic ProN","Noto Sans CJK SC","Microsoft YaHei","Geeza Pro","Noto Naskh Arabic","Noto Sans Arabic",sans-serif'

css() {
  local rtl="$1"
  printf '@page { size: A4; margin: 18mm 15mm 20mm 15mm; @bottom-center { content: counter(page) " / " counter(pages); font-size: 8.5pt; color: #888; } @top-right { content: string(doctitle); font-size: 8pt; color: #aaa; } }\n'
  printf 'body { font-family: %s; font-size: 10.5pt; line-height: 1.5; color: #1a1a1a;' "$FONTS"
  [ "$rtl" = 1 ] && printf ' direction: rtl;'
  printf ' }\n'
  cat <<'CSS'
h1 { string-set: doctitle content(); font-size: 22pt; color: #1f2d5a; margin: 0 0 4pt; }
h1 + p, h1 + p + blockquote { color: #555; }
h2 { font-size: 16pt; color: #1f2d5a; border-bottom: 2px solid #d7deec; padding-bottom: 3pt; margin-top: 0; break-before: page; }
h3 { font-size: 13pt; color: #2a3a66; margin-top: 14pt; }
h4 { font-size: 11.5pt; color: #33415f; }
h1, h2, h3, h4 { break-after: avoid; }
p, li { orphans: 2; widows: 2; }
a { color: #1f5fae; text-decoration: none; }
code, pre { font-family: "SF Mono", Menlo, "DejaVu Sans Mono", "Noto Sans Mono", monospace; direction: ltr; }
code { font-size: 9pt; background: #f3f4f6; padding: 0.5pt 3pt; border-radius: 3px; unicode-bidi: embed; }
pre { background: #f6f8fa; border: 1px solid #e1e4e8; border-radius: 5px; padding: 7pt 9pt; font-size: 8.7pt; line-height: 1.4; white-space: pre-wrap; overflow-wrap: anywhere; break-inside: avoid; text-align: left; }
pre code { background: none; padding: 0; }
table { width: 100%; border-collapse: collapse; margin: 8pt 0; font-size: 9pt; break-inside: auto; }
th, td { border: 1px solid #ccd2dd; padding: 4pt 6pt; text-align: start; vertical-align: top; }
th { background: #eef1f7; }
tr { break-inside: avoid; }
blockquote { border-inline-start: 3px solid #c7d0e0; margin: 8pt 0; padding: 2pt 12pt; color: #4a4a4a; background: #f8f9fb; }
hr { border: none; border-top: 1px solid #dfe3ea; margin: 14pt 0; }
ul, ol { padding-inline-start: 20pt; }
img { max-width: 100%; }
CSS
}

build_one() {
  local md="$1" code rtl dir out body full
  code="${md#3X-UI-MANAGER.}"; code="${code%.md}"
  case "$code" in ar|fa) rtl=1; dir=rtl;; *) rtl=0; dir=ltr;; esac
  out="pdf/3X-UI-MANAGER.${code}.pdf"
  body="$(mktemp -t xuibody).html"; full="$(mktemp -t xuifull).html"
  # GitHub keeps the zero-width joiners (U+200C/U+200D) in heading anchors but
  # pandoc strips them, so fa link targets (which keep them, for GitHub) wouldn't
  # resolve in the PDF. Drop them from link targets only — heading/body text keeps
  # them so Persian still shapes correctly.
  src="$(mktemp -t xuisrc).md"
  python3 -c 'import sys,re; s=open(sys.argv[1],encoding="utf-8").read(); open(sys.argv[2],"w",encoding="utf-8").write(re.sub(r"\]\(#([^)]+)\)", lambda m:"](#"+m.group(1).replace("‌","").replace("‍","")+")", s))' "$md" "$src"
  pandoc -f gfm -t html5 "$src" -o "$body"; rm -f "$src"
  {
    printf '<!DOCTYPE html><html lang="%s" dir="%s"><head><meta charset="utf-8"><style>\n' "$code" "$dir"
    css "$rtl"
    printf '</style></head><body>\n'
    cat "$body"
    printf '\n</body></html>\n'
  } > "$full"
  if [ -x "$WEASY" ]; then
    "$WEASY" "$full" "$out"; echo "  weasyprint -> $out"
  elif [ -x "$CHROME" ]; then
    "$CHROME" --headless=new --disable-gpu --no-sandbox --print-to-pdf="$out" --no-pdf-header-footer "file://$full" >/dev/null 2>&1
    echo "  chrome -> $out  (no page numbers)"
  else
    echo "no PDF renderer (WeasyPrint or Chrome) found" >&2; exit 1
  fi
  rm -f "$body" "$full"
}

mkdir -p pdf
echo "==> building manual PDFs"
shopt -s nullglob
mds=(3X-UI-MANAGER.*.md)
[ ${#mds[@]} -gt 0 ] || { echo "no 3X-UI-MANAGER.<code>.md files found" >&2; exit 1; }
# optional: build only the codes passed as args (e.g. ./build-pdf.sh ru en)
if [ "$#" -gt 0 ]; then
  for code in "$@"; do build_one "3X-UI-MANAGER.${code}.md"; done
else
  for md in "${mds[@]}"; do build_one "$md"; done
fi
echo "==> done"
