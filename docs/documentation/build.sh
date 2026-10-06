#!/usr/bin/env bash
# Builds docs/FSP-Career-documentation.pdf from the Markdown parts in this directory.
# Requires pandoc and Google Chrome (headless printing renders the Mermaid diagrams).
set -euo pipefail

cd "$(dirname "$0")"
OUT_HTML="$(mktemp -d)/documentation.html"
OUT_PDF="../FSP-Career-documentation.pdf"
CHROME="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"

PARTS=(
  01-overview.md 02-architecture.md 03-assessment.md 04-matching.md
  05-validation.md concepts.md 06-api.md 07-stack.md 08-deploy.md 09-limitations.md
)

pandoc "${PARTS[@]}" \
  --from markdown+pipe_tables+fenced_divs+header_attributes \
  --to html5 --standalone --toc --toc-depth=2 \
  --metadata title="ФСП Карьера — сопроводительная документация" \
  --metadata lang=ru \
  --css "file://$PWD/style.css" \
  --include-before-body cover.html \
  --include-after-body mermaid.html \
  --output "$OUT_HTML"

"$CHROME" --headless=new --disable-gpu --no-pdf-header-footer \
  --virtual-time-budget=20000 --run-all-compositor-stages-before-draw \
  --print-to-pdf="$OUT_PDF" "file://$OUT_HTML" 2>/dev/null
echo "Wrote $(cd .. && pwd)/FSP-Career-documentation.pdf"
