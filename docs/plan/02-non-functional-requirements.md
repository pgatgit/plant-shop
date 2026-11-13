# 02 — Non-Functional Requirements

> Related: [07 threat model](07-auth-design.md#9-threat-model) · [08 environments](08-docker-and-environments.md) · [09 gates](09-testing-strategy.md#6-coverage--quality-gates)

Each NFR has an ID, a measurable target and a verification method. "CI" = enforced automatically.

## 1. Performance

| ID | Requirement | Target | Verified by |
|---|---|---|---|
| NFR-PERF-01 | Catalog/search API latency (token check cached) | p95 < 200 ms at 20 rps on compose stack | Non-gating JUnit perf-smoke IT (200 calls, reports p95) + Prometheus `http.server.requests` histogram review at M7 (no load-test tool added) |
| NFR-PERF-02 | Cart mutation latency (cached) | p95 < 250 ms | Same as above |
| NFR-PERF-03 | Token check on cache miss | p95 < 800 ms; timeouts connect 2 s / read 3 s | WireMock delay tests |
| NFR-PERF-04 | Prefix search uses index | `EXPLAIN` shows range access on `ix_product_name` with ≥5k rows | CI (Testcontainers IT) |
| NFR-PERF-05 | Initial JS bundle | ≤ 200 KB gzip; CSS ≤ 30 KB gzip | CI (bundle-size script) |
| NFR-PERF-06 | Catalog page LCP | < 2.5 s on Lighthouse "mobile" preset against compose stack | Manual at M7, recorded in runbook |
| NFR-PERF-07 | Search request rate | Debounce 300 ms; in-flight request cancelled on new input | Component test (fake timers) |

## 2. Security

| ID | Requirement | Verified by |
|---|---|---|
| NFR-SEC-01 | All endpoints except BR-01 allowlist return 401 unauthenticated | Security IT iterating over all mapped endpoints |
| NFR-SEC-02 | Scope enforcement: `catalog.read`, `cart.read`, `cart.write` | `@WebMvcTest` + IT with `viewer` user |
| NFR-SEC-03 | GitHub client secret only in backend env; never logged, never sent to browser | Log-capture test; config review |
| NFR-SEC-04 | Tokens never persisted in browser storage; PKCE verifier/state in `sessionStorage` only during the redirect round-trip and deleted on callback | Frontend unit test; E2E asserts storage empty after login |
| NFR-SEC-05 | Tokens/`Authorization` headers masked in logs | Log-capture test |
| NFR-SEC-06 | CORS: exact-origin allowlist from env; no wildcard; no credentials | Security IT (allowed vs disallowed origin preflight) |
| NFR-SEC-07 | Only tokens issued **for our OAuth App** are accepted (GitHub "check a token" API, not `/user`) | WireMock IT: foreign-app token → 401 |
| NFR-SEC-08 | Local AS and in-memory users **impossible** in `prod` (bean absent; `/oauth2/**` → 404) | Profile IT |
| NFR-SEC-09 | Rate limit on `/api/v1/auth/token`: 10 req/min/IP → 429 `Retry-After` | IT |
| NFR-SEC-10 | Security headers from nginx: CSP (`default-src 'self'; connect-src 'self' ${API_ORIGIN}; img-src 'self' https://avatars.githubusercontent.com; frame-ancestors 'none'`), `Referrer-Policy: no-referrer`, `X-Content-Type-Options: nosniff`, `Permissions-Policy`; HSTS in prod behind TLS | E2E header assertions |
| NFR-SEC-11 | Containers run as non-root; no secrets in images | CI image inspect step |
| NFR-SEC-12 | Dependency updates via Dependabot (Maven, npm, Actions, Docker) | Config present |
| NFR-SEC-13 | SQL only via JPA parameter binding / Spring Data; no string-concatenated SQL | SpotBugs/FindSecBugs + review |
| NFR-SEC-14 | OWASP ASVS 5.0 Level 1 controls relevant to scope are met | Checklist in `docs/security.md` at M7 |

## 3. Accessibility (WCAG 2.2 AA)

| ID | Requirement | Verified by |
|---|---|---|
| NFR-A11Y-01 | No serious/critical axe violations on every page/state | `vitest-axe` in component tests; `@axe-core/playwright` per page in E2E (CI) |
| NFR-A11Y-02 | Full keyboard operation; visible focus (2 px, ≥3:1); skip link | E2E keyboard-only journey |
| NFR-A11Y-03 | Live regions: search result count, add-to-cart status, errors (`role="alert"`) | Component tests |
| NFR-A11Y-04 | Text contrast ≥ 4.5:1, UI components ≥ 3:1; target size ≥ 24×24 px (2.5.8) | axe + design tokens review |
| NFR-A11Y-05 | Respects `prefers-reduced-motion`; no information by colour alone (stock/pet badges have text) | Review + component tests |
| NFR-A11Y-06 | Responsive 320 px → 1440 px without horizontal scroll (1.4.10 Reflow) | Playwright mobile + desktop projects |

## 4. Usability / UX

English (`en-US`) only, USD formatted via `Intl.NumberFormat('en-US', {style:'currency', currency:'USD'})`. Mobile-first grid: 1 col < 640 px, 2 cols ≥ 640, 3 cols ≥ 1024, 4 cols ≥ 1280.

## 5. Reliability & scalability

| ID | Requirement |
|---|---|
| NFR-REL-01 | Liveness `/actuator/health/liveness`, readiness `/actuator/health/readiness` (includes DB) on management port 8081 |
| NFR-REL-02 | Compose startup ordering via `depends_on: condition: service_healthy`; `docker compose up --wait` succeeds from clean checkout in < 3 min on CI runner |
| NFR-REL-03 | Graceful shutdown (`server.shutdown=graceful`, 20 s) |
| NFR-REL-04 | Flyway validates on start; app fails fast on checksum mismatch |
| NFR-REL-05 | GitHub outage tolerance: cached token checks keep working ≤ 5 min; misses → 503 with `Retry-After: 30` |
| NFR-REL-06 | HikariCP pool 10, connection timeout 5 s; DB down → readiness DOWN, API 503 |
| NFR-REL-07 | **Known limit:** single instance. Token cache and rate-limit buckets are in-memory; horizontal scale would need Redis (out of scope, recorded in [ADR-014](adr/014-observability-and-rate-limiting.md)) |

## 6. Observability

| ID | Requirement |
|---|---|
| NFR-OBS-01 | Structured JSON logs (Spring Boot built-in ECS format) in `e2e`/`prod`; human-readable in `dev` |
| NFR-OBS-02 | Every log line carries `traceId`/`spanId` (Micrometer Tracing, no exporter) and `ownerId` hash when authenticated; `X-Request-Id` echoed in responses |
| NFR-OBS-03 | Prometheus endpoint `/actuator/prometheus` on 8081 (not published in prod) |
| NFR-OBS-04 | Custom metrics: `plantshop.cart.mutations{op,outcome}`, `plantshop.auth.token_check{source=cache\|github,outcome}`, `plantshop.auth.token_exchange{outcome}`, Caffeine cache stats |
| NFR-OBS-05 | Log levels: INFO default; auth failures WARN without token content; 5xx ERROR with stack trace; 4xx DEBUG |

## 7. Privacy & compliance

- Persisted personal data: **owner ID only** (`github:{id}` / `local:{name}`). Display name/avatar are read from the token check response and never stored.
- No analytics, no third-party scripts, no cookies in prod SPA (Local AS uses a session cookie in dev/e2e only) → no consent banner required.
- Carts persist indefinitely (D7); a data-deletion process is **out of scope** and noted as a risk in [12](12-implementation-roadmap.md#5-risks).
- Images are original placeholder SVGs → no licensing exposure.

## 8. Maintainability

Coverage and static-analysis gates per [09](09-testing-strategy.md#6-coverage--quality-gates) and [10](10-code-quality-and-conventions.md#8-ci-quality-gates); ArchUnit-enforced layering; every major decision has an ADR.
