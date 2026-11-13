// Enforces NFR-PERF-05: initial JS <= 200 KB gzip, CSS <= 30 KB gzip.
// "Initial" = assets referenced directly by dist/index.html (lazy chunks are excluded).
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { gzipSync } from 'node:zlib';

const DIST = new URL('../dist/', import.meta.url).pathname;
const BUDGETS = { js: 200 * 1024, css: 30 * 1024 };

const html = readFileSync(join(DIST, 'index.html'), 'utf8');
const refs = [...html.matchAll(/(?:src|href)="\/(assets\/[^"]+\.(js|css))"/g)];

const totals = { js: 0, css: 0 };
for (const [, file, kind] of refs) {
  const size = gzipSync(readFileSync(join(DIST, file))).length;
  totals[kind] += size;
  console.log(`${file}: ${(size / 1024).toFixed(1)} KB gzip`);
}

let failed = false;
for (const kind of ['js', 'css']) {
  const ok = totals[kind] <= BUDGETS[kind];
  failed ||= !ok;
  console.log(
    `${ok ? 'OK  ' : 'FAIL'} initial ${kind}: ${(totals[kind] / 1024).toFixed(1)} KB / ${BUDGETS[kind] / 1024} KB`,
  );
}
process.exit(failed ? 1 : 0);
