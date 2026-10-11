#!/usr/bin/env bash
set -euo pipefail

doc_dir="$(dirname "$(realpath "${BASH_SOURCE[0]}")")"
root="$(dirname "$(dirname "$doc_dir")")"
revision="${DOC_REVISION:-$(git -C "$root" rev-parse --short=12 HEAD)}"
build_date="${DOC_DATE:-$(date -u +%F)}"

if ! command -v typst >/dev/null 2>&1; then
  printf '%s\n' 'Install Typst 0.14.2 or newer; see docs/documentation/README.md.' >&2
  exit 1
fi

# Only static fonts: Typst 0.14 does not fully support variable font instances.
font_dir="$(mktemp -d "${TMPDIR:-/tmp}/fsp-career-doc-fonts.XXXXXX")"
trap 'rm -rf "$font_dir"' EXIT
for font in Montserrat-Regular Montserrat-Bold Play-Regular Play-Bold; do
  cp "$root/apps/web/static/fonts/$font.ttf" "$font_dir/"
done

typst compile \
  --root "$root" \
  --ignore-system-fonts \
  --font-path "$font_dir" \
  --input "revision=$revision" \
  --input "build-date=$build_date" \
  "$doc_dir/project.typ" \
  "$root/docs/FSP-Career-documentation.pdf"
