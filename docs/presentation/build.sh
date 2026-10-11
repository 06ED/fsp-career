#!/usr/bin/env bash
set -euo pipefail

doc_dir="$(dirname "$(realpath "${BASH_SOURCE[0]}")")"
root="$(dirname "$(dirname "$doc_dir")")"

for tool in python3 soffice; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    printf 'Missing %s; see docs/presentation/README.md.\n' "$tool" >&2
    exit 1
  fi
done

work="$(mktemp -d "${TMPDIR:-/tmp}/fsp-career-presentation.XXXXXX")"
trap 'rm -rf "$work"' EXIT
mkdir "$work/fonts"
for font in Montserrat-Regular Montserrat-Bold Play-Regular Play-Bold; do
  cp "$root/apps/web/static/fonts/$font.ttf" "$work/fonts/"
done

python3 "$doc_dir/build.py" --output "$work/FSP-Career-presentation.pptx"
SAL_LOG="${SAL_LOG:--WARN}" FONTCONFIG_FILE="$work/fontconfig.xml" soffice \
  "-env:UserInstallation=file://$work/libreoffice-profile" \
  --headless --convert-to pdf:impress_pdf_Export \
  --outdir "$work" "$work/FSP-Career-presentation.pptx"

test -s "$work/FSP-Career-presentation.pdf"
cp "$work/FSP-Career-presentation.pptx" "$root/docs/FSP-Career-presentation.pptx"
cp "$work/FSP-Career-presentation.pdf" "$root/docs/FSP-Career-presentation.pdf"
