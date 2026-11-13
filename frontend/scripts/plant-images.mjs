// Generates the placeholder plant illustrations in public/images/plants/ from the seed migration,
// so images never drift from the catalog (D10: local SVG placeholders, no licensing concerns).
//
//   node scripts/plant-images.mjs           write/overwrite all SVGs
//   node scripts/plant-images.mjs --check   fail if any SVG is missing or out of date (CI)
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const SEED = join(ROOT, '../backend/src/main/resources/db/migration/V5__seed_products.sql');
const OUT = join(ROOT, 'public/images/plants');

/** Parses `('SKU', 'Name', ... code = 'CATEGORY'))` rows from the seed migration. */
function readCatalog() {
  const sql = readFileSync(SEED, 'utf8');
  const row = /^\s*\('([A-Z0-9-]+)', '((?:[^']|'')+)',.*code = '([A-Z_]+)'\)\)[,;]?$/gm;
  const plants = [...sql.matchAll(row)].map(([, sku, name, category]) => ({
    sku,
    name: name.replaceAll("''", "'"),
    category,
  }));
  if (plants.length === 0) throw new Error(`No products parsed from ${SEED}`);
  return plants;
}

/** Small deterministic PRNG seeded from the SKU, so output is stable across runs. */
function random(seedText) {
  let h = 2166136261;
  for (const c of seedText) h = Math.imul(h ^ c.charCodeAt(0), 16777619);
  return () => {
    h = Math.imul(h ^ (h >>> 15), 2246822507);
    h = Math.imul(h ^ (h >>> 13), 3266489909);
    return ((h ^= h >>> 16) >>> 0) / 4294967296;
  };
}

const escapeXml = (s) =>
  s
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll("'", '&#39;');

const r1 = (n) => Math.round(n * 10) / 10;

function leaf(cx, cy, rx, ry, angle, fill) {
  return `<ellipse cx="${r1(cx)}" cy="${r1(cy)}" rx="${r1(rx)}" ry="${r1(ry)}" transform="rotate(${r1(angle)} ${r1(cx)} ${r1(cy)})" fill="${fill}"/>`;
}

/** Foliage shapes per category; all drawn above the pot rim at y = 128. */
const drawers = {
  FOLIAGE(rnd, c) {
    const n = 5 + Math.floor(rnd() * 3);
    let s = '';
    for (let i = 0; i < n; i++) {
      const a = -70 + (140 * i) / (n - 1) + (rnd() - 0.5) * 10;
      const len = 34 + rnd() * 14;
      const rad = ((a - 90) * Math.PI) / 180;
      s += `<path d="M100 128 L${r1(100 + Math.cos(rad) * len * 0.8)} ${r1(128 + Math.sin(rad) * len * 0.8)}" stroke="${c.stem}" stroke-width="2.5"/>`;
      s += leaf(
        100 + Math.cos(rad) * len,
        128 + Math.sin(rad) * len,
        13 + rnd() * 5,
        22 + rnd() * 6,
        a,
        i % 2 ? c.leaf : c.leaf2,
      );
    }
    return s;
  },
  TRAILING(rnd, c) {
    // Only the crown sits behind the pot; vines are drawn in front by TRAILING_FRONT.
    let s = '';
    for (let i = 0; i < 6; i++)
      s += leaf(70 + i * 12, 118 - rnd() * 14, 10, 7, rnd() * 60 - 30, c.leaf);
    return s;
  },
  FERNS(rnd, c) {
    let s = '';
    const n = 7;
    for (let i = 0; i < n; i++) {
      const a = -75 + (150 * i) / (n - 1);
      const rad = ((a - 90) * Math.PI) / 180;
      const len = 52 + rnd() * 10;
      const ex = 100 + Math.cos(rad) * len;
      const ey = 128 + Math.sin(rad) * len;
      s += `<path d="M100 128 Q${r1((100 + ex) / 2)} ${r1(Math.min(128, ey) - 12)} ${r1(ex)} ${r1(ey)}" stroke="${c.stem}" stroke-width="1.6" fill="none"/>`;
      for (let k = 1; k < 8; k++) {
        const t = k / 8;
        s += leaf(100 + (ex - 100) * t, 128 + (ey - 128) * t - 3, 3.2, 7 - t * 3, a + 70, c.leaf);
        s += leaf(100 + (ex - 100) * t, 128 + (ey - 128) * t + 3, 3.2, 7 - t * 3, a - 70, c.leaf2);
      }
    }
    return s;
  },
  PALMS(rnd, c) {
    let s = `<rect x="97" y="74" width="6" height="56" rx="3" fill="${c.stem}"/>`;
    const n = 9;
    for (let i = 0; i < n; i++) {
      const a = -168 + (156 * i) / (n - 1) + (rnd() - 0.5) * 6;
      const rad = (a * Math.PI) / 180;
      s += leaf(
        100 + Math.cos(rad) * 28,
        74 + Math.sin(rad) * 22,
        30,
        5,
        a,
        i % 2 ? c.leaf : c.leaf2,
      );
    }
    return s;
  },
  SUCCULENTS(rnd, c) {
    let s = '';
    for (const [ring, count, len, w] of [
      [0, 9, 30, 11],
      [1, 7, 21, 9],
      [2, 5, 12, 7],
    ]) {
      for (let i = 0; i < count; i++) {
        const a = (360 * i) / count + ring * 17 + rnd() * 6;
        const rad = (a * Math.PI) / 180;
        s += leaf(
          100 + Math.cos(rad) * len * 0.8,
          112 + Math.sin(rad) * len * 0.45,
          w,
          len * 0.55,
          a + 90,
          ring === 1 ? c.leaf2 : c.leaf,
        );
      }
    }
    return s;
  },
  FLOWERING(rnd, c) {
    let s = drawers.FOLIAGE(rnd, c);
    for (let i = 0; i < 3; i++) {
      const x = 72 + i * 28 + (rnd() - 0.5) * 8;
      const y = 62 + rnd() * 16;
      s += `<path d="M100 128 Q${r1(x)} 110 ${r1(x)} ${r1(y + 6)}" stroke="${c.stem}" stroke-width="2" fill="none"/>`;
      for (let p = 0; p < 5; p++)
        s += leaf(
          x + Math.cos(p * 1.256) * 6,
          y + Math.sin(p * 1.256) * 6,
          5,
          8,
          p * 72 + 90,
          c.flower,
        );
      s += `<circle cx="${r1(x)}" cy="${r1(y)}" r="3.5" fill="#f5d36b"/>`;
    }
    return s;
  },
};

/** Vines hanging over the pot rim (drawn after the pot). */
function trailingFront(rnd, c) {
  let s = '';
  for (let v = 0; v < 4; v++) {
    const x0 = 68 + v * 21;
    const len = 34 + rnd() * 26;
    s += `<path d="M${x0} 126 C${x0 - 10} 146 ${x0 + 10} 160 ${x0 - 3} ${r1(126 + len)}" stroke="${c.stem}" stroke-width="2" fill="none"/>`;
    for (let k = 0; k < 5; k++) {
      const y = 132 + (k * len) / 5;
      s += leaf(x0 + (k % 2 ? 6 : -6), y, 5.5, 4, k % 2 ? 30 : -30, k % 2 ? c.leaf : c.leaf2);
    }
  }
  return s;
}

const FLOWER_COLOURS = ['#e8eef2', '#d9485f', '#e58fb3', '#f0a04b', '#b07cd8'];

function svgFor({ sku, name, category }) {
  const rnd = random(sku);
  const hue = 95 + Math.floor(rnd() * 50);
  const colours = {
    leaf: `hsl(${hue} 45% 34%)`,
    leaf2: `hsl(${hue + 8} 40% 44%)`,
    stem: `hsl(${hue} 35% 26%)`,
    flower: FLOWER_COLOURS[Math.floor(rnd() * FLOWER_COLOURS.length)],
  };
  const pot = ['#c8704d', '#b65f3e', '#d9d2c5', '#5d6b78'][Math.floor(rnd() * 4)];
  const draw = drawers[category];
  if (!draw) throw new Error(`No drawing for category ${category} (${sku})`);
  return [
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" width="200" height="200" role="img" aria-labelledby="t">`,
    `<title id="t">Illustration of a ${escapeXml(name)}</title>`,
    `<rect width="200" height="200" rx="16" fill="hsl(${hue} 35% 95%)"/>`,
    `<ellipse cx="100" cy="182" rx="46" ry="6" fill="#00000014"/>`,
    draw(rnd, colours),
    `<path d="M66 128 H134 L126 180 H74 Z" fill="${pot}"/>`,
    `<rect x="62" y="124" width="76" height="12" rx="3" fill="${pot}"/>`,
    `<rect x="62" y="124" width="76" height="4" rx="2" fill="#ffffff33"/>`,
    category === 'TRAILING' ? trailingFront(rnd, colours) : '',
    `</svg>`,
    '',
  ].join('\n');
}

const check = process.argv.includes('--check');
const plants = readCatalog();
mkdirSync(OUT, { recursive: true });

const problems = [];
const expected = new Set();
for (const plant of plants) {
  const file = `${plant.sku.toLowerCase()}.svg`;
  expected.add(file);
  const path = join(OUT, file);
  const svg = svgFor(plant);
  if (check) {
    if (!existsSync(path)) problems.push(`missing ${file}`);
    else if (readFileSync(path, 'utf8') !== svg) problems.push(`outdated ${file}`);
  } else {
    writeFileSync(path, svg);
  }
}
for (const file of readdirSync(OUT)) {
  if (file.endsWith('.svg') && !expected.has(file)) problems.push(`orphaned ${file}`);
}

if (problems.length > 0) {
  console.error(
    `Plant images out of sync (run: node scripts/plant-images.mjs):\n  ${problems.join('\n  ')}`,
  );
  process.exit(1);
}
console.log(
  `${check ? 'Checked' : 'Wrote'} ${plants.length} plant images in public/images/plants/`,
);
