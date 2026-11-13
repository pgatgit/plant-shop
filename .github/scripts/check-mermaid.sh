#!/usr/bin/env bash
# Extracts every ```mermaid block from Markdown files and renders it; any syntax error fails.
set -euo pipefail

workdir=$(mktemp -d)
trap 'rm -rf "$workdir"' EXIT

python3 - "$workdir" <<'PY'
import pathlib, re, sys
out = pathlib.Path(sys.argv[1])
count = 0
for md in sorted(pathlib.Path('.').glob('**/*.md')):
    if 'node_modules' in md.parts:
        continue
    for block in re.findall(r'```mermaid\n(.*?)```', md.read_text(encoding='utf-8'), re.S):
        count += 1
        (out / f'{count:03d}.mmd').write_text(block, encoding='utf-8')
        (out / f'{count:03d}.src').write_text(str(md), encoding='utf-8')
print(f'found {count} mermaid diagrams')
PY

echo '{"args":["--no-sandbox"]}' > "$workdir/puppeteer.json"
status=0
for diagram in "$workdir"/*.mmd; do
  [[ -e "$diagram" ]] || continue
  if ! npx -y @mermaid-js/mermaid-cli@11.17.1 -p "$workdir/puppeteer.json" \
      -i "$diagram" -o "${diagram%.mmd}.svg" >/dev/null 2>&1; then
    echo "FAIL diagram in $(cat "${diagram%.mmd}.src")"
    status=1
  fi
done
exit "$status"
