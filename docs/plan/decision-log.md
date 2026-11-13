# Decision Log

> Every question asked during Phase 1, the answer given, and the date. `D#` = consolidated decision used across the plan. Changing an answer → update this file, the linked ADR and all affected plan files ([11 §3](11-documentation-plan.md#3-adr-process--template)).

## Consolidated decisions

| ID | Decision | Source | ADR |
|---|---|---|---|
| D1 | GitHub OAuth App is the prod identity provider (scope `read:user`) | R1-Q1, R3-Q3 | [002](adr/002-github-oauth-direct-token.md) |
| D2 | GitHub token used directly; backend `POST /api/v1/auth/token` proxies code exchange (adds secret) | R2-Q1, R3-Q1 | [002](adr/002-github-oauth-direct-token.md) |
| D3 | Backend validates GitHub tokens with custom introspector, cached ~5 min; owner id `github:{id}` via GitHub "check a token" endpoint (not `/user`) — P1 approved | R2-Q1, R3-Q1 | [002](adr/002-github-oauth-direct-token.md) |
| D4 | dev/e2e: profile-gated Spring Authorization Server with in-memory users issuing JWTs | R1-Q4, R2-Q2, R3-Q2 | [003](adr/003-local-authorization-server-dev-e2e.md) |
| D5 | SPA: Authorization Code + PKCE, token in memory | R1-Q2 | [004](adr/004-spa-token-in-memory-pkce.md) |
| D6 | Login required for everything | R1-Q3 | [006](adr/006-cart-rules.md) |
| D7 | Cart server-side per user; live prices; re-add increments; 1..min(stock,10), 409 otherwise; no expiry | R2-Q3, R2-Q4 | [006](adr/006-cart-rules.md) |
| D8 | Page-number pagination, 12/page; sort by name/price; no filters | R3-Q4, R4-Q1 | [015](adr/015-page-number-pagination.md) |
| D9 | Prefix search, case/accent-insensitive via collation, min 2 chars, 300 ms debounce | R4-Q2 | [005](adr/005-prefix-search-via-collation.md) |
| D10 | Cards only (no detail page); exact stock count; `/cart` page + badge; local SVG images | R6-Q1…Q4 | — |
| D11 | Frontend: TS strict, Vite, React Router, TanStack Query, Tailwind + Radix | R4-Q3, defaults | [010](adr/010-frontend-stack.md) |
| D12 | Backend: Maven, layered package-by-feature, JPA, MapStruct, Problem Details, `/api/v1` | R5-Q1, defaults | [008](adr/008-layered-package-by-feature.md) |
| D13 | Actuator + JSON logs + Prometheus; Bucket4j on `/auth/token` only | R5-Q2, R5-Q3 | [014](adr/014-observability-and-rate-limiting.md) |
| D14 | Cross-origin SPA :3000 / API :8080 with strict CORS | R7-Q1 | [007](adr/007-cross-origin-cors-topology.md) |
| D15 | Dev loop: local apps + compose MySQL | R7-Q2 | — |
| D16 | CI: build, test, E2E, image build (no push) | R7-Q3 | — |
| D17 | Coverage gates BE 85/75, FE 80/70, each story ≥1 E2E | R5-Q4 | [011](adr/011-testing-strategy.md) |
| D18 | Trunk-based, Conventional Commits, squash merge | R7-Q4 | — |
| D19 | USD, en-US, `DECIMAL(10,2)`, HALF_UP | R4-Q4 | — |
| D20 | Swagger UI off in prod unless `APP_SWAGGER_ENABLED=true` | R8-Q1 | — |
| D21 | Spring Boot **4.1.x** on Java 17 (amends round-1 default "Boot 3.x") — P2 approved; minor line 4.1 chosen at M0 | planning, R10 | [013](adr/013-spring-boot-4-on-java-17.md) |
| D22 | Logout is client-side only in GitHub mode; no token revocation call | R9 (P7) | [002](adr/002-github-oauth-direct-token.md) |

## Questions & answers (all dated 2026-11-13)

| Ref | Question | Options offered (recommended first) | Answer |
|---|---|---|---|
| R1-Q1 | Where should the OAuth2 Authorization Server live? | Embedded AS+RS · Separate auth service · Keycloak container | **Other: "use github authorization with jwt"** |
| R1-Q2 | How should the React app get and hold tokens? | PKCE in-memory · BFF + httpOnly cookie · PKCE + sessionStorage | **PKCE, in-memory** |
| R1-Q3 | Who can do what without logging in? | Browse anon, cart needs login · Guest cart + merge · Login required for all | **Login required for all** |
| R1-Q4 | Where do users come from? | Seeded demo users · Seeded + self-registration · In-memory users | **In-memory users** |
| R2-Q1 | How should "GitHub + JWT" be built? | Our AS federates GitHub · Backend mints custom JWT · Use GitHub token directly | **Use GitHub token directly** |
| R2-Q2 | Given GitHub login, what are in-memory users for? | Dev/test profiles only · Alongside GitHub all envs · Drop; mock GitHub in tests | **Dev/test profiles only** |
| R2-Q3 | How should cart prices work? | Always live price · Snapshot + change notice | **Always live price** |
| R2-Q4 | Cart quantity/stock rules? | 1..min(stock,10), 409 · Capped at stock only · No stock checks | **1..min(stock,10), 409 if over** |
| R3-Q1 | Keep direct GitHub tokens (no JWT, proxy + custom check) or switch? | Switch: our AS + GitHub · Keep GitHub token direct | **Keep GitHub token direct** |
| R3-Q2 | How do dev/e2e in-memory users get tokens? | Mock GitHub in dev/e2e · Dev-only local issuer | **Dev-only local issuer** |
| R3-Q3 | Which GitHub app type? | OAuth App · GitHub App | **OAuth App** |
| R3-Q4 | Listing pagination style? | Page numbers 12/page · Load more/infinite · No pagination | **Page numbers, 12/page** |
| R4-Q1 | Sorts and filters? | Sort + category + pet-safe · Sort only · Rich filters | **Sort only** |
| R4-Q2 | Search behaviour? | Contains, 2+ chars, debounced · Prefix only · Fuzzy/full-text | **Prefix only** |
| R4-Q3 | UI/styling? | MUI · Tailwind + Radix · CSS Modules only | **Tailwind + Radix** |
| R4-Q4 | Currency/format? | USD en-US · EUR de-DE · INR en-IN | **USD, en-US** |
| R5-Q1 | Backend code organisation? | Layered package-by-feature · Hexagonal | **Layered, package-by-feature** |
| R5-Q2 | Observability depth? | Actuator + JSON logs + metrics · + OTel/Grafana · Health only | **Actuator + JSON logs + metrics** |
| R5-Q3 | Caching / rate limiting? | Rate-limit /auth/token only · + product cache · None | **Rate-limit /auth/token only** |
| R5-Q4 | Coverage gates? | BE 85/75, FE 80/70 · 90/80 both · + mutation testing | **BE 85/75, FE 80/70** |
| R6-Q1 | Product detail page? | Yes /plants/:slug · No, cards only · Quick-view dialog | **No, cards only** |
| R6-Q2 | Cart presentation? | /cart page + badge · Slide-over drawer · Both | **/cart page + header badge** |
| R6-Q3 | Product images? | Local SVG placeholders · External photo URLs · Backend-served files | **Local SVG placeholders** |
| R6-Q4 | Stock display? | Bands + disable at 0 · Exact count · Hidden | **Exact count** |
| R7-Q1 | Browser → backend topology? | Cross-origin + strict CORS · nginx proxies /api | **Cross-origin + strict CORS** |
| R7-Q2 | Dev loop? | Local apps + compose MySQL · All in containers | **Local apps + compose MySQL** |
| R7-Q3 | CI/CD extent? | Build, test, E2E, images · + push to GHCR · Tests only | **Build, test, E2E, images** |
| R7-Q4 | Branching/commits? | Trunk + short branches · GitFlow | **Trunk + short branches** |
| R8-Q1 | Swagger UI in prod? | Off in prod, env toggle · On everywhere | **Off in prod, env toggle** |
| R8-Q2 | Approve decision log and write plan? | Approve · Change something first | **Approve, write the plan** |
| R9 | Review of planning refinements P1–P7 | Approve / reject each | **P1–P6 approved; P7 skipped** |
| R10 | (M0) Pin Boot 4.1.2 or 4.0.9? 4.0 OSS ends ~Dec 2026 | 4.1.2 · 4.0.9 as approved | **4.1.2** |

## Defaults accepted without objection (2026-11-13)

TypeScript strict; Vite; React Router; TanStack Query + auth context; Maven wrapper; Spring Data JPA; MapStruct; Bean Validation; Problem Details; `/api/v1`; MySQL 8.4 LTS utf8mb4 / `utf8mb4_0900_ai_ci`; `DECIMAL(10,2)`; Flyway `V{n}__`/`R__`, forward-only; JUnit 5, Mockito, AssertJ, Testcontainers, Vitest, RTL, MSW, Playwright, JaCoCo; Spotless, Checkstyle, SpotBugs, ESLint, Prettier; GitHub Actions; multi-stage Docker, Temurin JRE, nginx-unprivileged, non-root; Actuator probes; English only; mobile-first; WCAG 2.2 AA; no SEO; runtime `config.json`; catalog seed in versioned migration + e2e fixtures folder; API p95 < 200 ms (cached); JS ≤ 200 KB gz; milestones M0–M7; anonymous visitors redirected straight to login; cart keyed by token subject with no users table; GitHub OAuth App registered by you, secret via env only; card description clamp + "Show more"; add-to-cart +1 per click; 409 shown as live alert.

## Planning refinements (reviewed 2026-11-13)

| # | Refinement | Where | Outcome |
|---|---|---|---|
| P1 | Validate GitHub tokens via "check a token" (`POST /applications/{client_id}/token`) instead of `GET /user` (prevents foreign-token substitution) | [07 §2](07-auth-design.md#2-prod-flow--github-direct-token-d1d3), ADR-002 | **Approved** |
| P2 | Spring Boot **4.x** (4.1 per R10) instead of 3.x (3.5 OSS support has ended) | ADR-013 | **Approved** |
| P3 | Clean-checkout `docker compose up` runs profile `e2e` with local login (no GitHub secrets needed) | [08 §3](08-docker-and-environments.md#3-compose-composeyaml) | **Approved** |
| P4 | Local URLs use `127.0.0.1`; one GitHub OAuth App with loopback callback `http://127.0.0.1/` serves SPA and Swagger | [07 §4](07-auth-design.md#4-registered-clients) | **Approved** |
| P5 | Extra in-memory users: `viewer` (catalog only, for 403 tests) and `e2e-user-1..4` (one per Playwright worker) | [07 §3](07-auth-design.md#3-deve2e-flow--local-as-d4) | **Approved** |
| P6 | Small `GET /api/v1/me` endpoint for header display name | [04 §4](04-backend-design.md#4-rest-api-contract) | **Approved** |
| P7 | Revoke GitHub token on logout via `DELETE /applications/{client_id}/token` | ADR-002, [07 §5](07-auth-design.md#5-spa-token-handling-d5) | **Declined** — logout stays client-side only |
