# Plant Shop — Frontend

React 19 + TypeScript single-page app, built with Vite and served by nginx in production. Design:
[docs/plan/05-frontend-design.md](../docs/plan/05-frontend-design.md).

> **Status:** M0 scaffold. Routing, login and features arrive in M4–M6.

## Prerequisites

Node.js 24 LTS (`engines` enforces `>=24`).

## Scripts

| Script                            | Purpose                                                                                        |
| --------------------------------- | ---------------------------------------------------------------------------------------------- |
| `npm run dev`                     | Vite dev server with HMR on <http://127.0.0.1:3000> (strict port)                              |
| `npm run build`                   | Type-check and build to `dist/`                                                                |
| `npm test` / `npm run test:watch` | Vitest unit and component tests (jsdom, RTL, MSW, vitest-axe)                                  |
| `npm run test:coverage`           | Tests with v8 coverage; fails below 80 % lines / 70 % branches                                 |
| `npm run lint`                    | ESLint (type-checked, jsx-a11y strict, boundaries), zero warnings                              |
| `npm run format` / `format:check` | Prettier (with Tailwind class sorting)                                                         |
| `npm run typecheck`               | `tsc -b` in strict mode                                                                        |
| `npm run check:bundle`            | Initial JS ≤ 200 KB gzip, CSS ≤ 30 KB gzip                                                     |
| `npm run e2e`                     | Playwright against the running compose stack (`E2E_BASE_URL`, default `http://127.0.0.1:3000`) |
| `npm run ci`                      | Everything CI runs, in order                                                                   |

## Runtime configuration

The app reads `/config.json` at start-up, so one image works in every environment. In
development it is `public/config.json`. In the container, `nginx/40-write-config.sh` writes it
from environment variables (`API_BASE_URL`, `API_ORIGIN`, `AUTH_*`) and refuses to start if one
is missing. See [ADR-012](../docs/plan/adr/012-runtime-frontend-config.md) and
[08 §4.2](../docs/plan/08-docker-and-environments.md#42-frontend-container-runtime--configjson).

## Container

`docker build -t plantshop/frontend:local .` builds a multi-stage image (`node:24-alpine` →
`nginxinc/nginx-unprivileged`, uid 101, port 8080). nginx adds the security headers (CSP,
`Referrer-Policy: no-referrer`, `nosniff`), serves `/healthz`, and marks `index.html` and
`config.json` as `no-store`.

## Tests layout

- `src/**/*.test.tsx`: unit and component tests next to the code.
- `src/test/`: setup, MSW server and handlers.
- `e2e/specs/`: Playwright specs (desktop and Pixel 7 projects).
