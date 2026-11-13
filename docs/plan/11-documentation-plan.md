# 11 — Documentation Plan

> Related: [10 conventions](10-code-quality-and-conventions.md) · [12 roadmap](12-implementation-roadmap.md)

## 1. Inventory

| Document | Location | Audience | Created | Owner of truth |
|---|---|---|---|---|
| Root README | `/README.md` | everyone | M0, completed M7 | What it is, quick start (`docker compose up`), links |
| Backend README | `backend/README.md` | backend devs | M0 → M7 | Build/test commands, profiles, config properties, package map |
| Frontend README | `frontend/README.md` | frontend devs | M0 → M7 | Scripts, config.json, testing, conventions |
| Architecture | `docs/architecture.md` | devs, reviewers | M7 (from [03](03-architecture.md)) | Living diagrams |
| ADRs | `docs/plan/adr/NNN-*.md` | devs | now; updated on change | Decisions |
| API reference | Swagger UI `/swagger-ui.html`, `docs/api/openapi.yaml` | API consumers | M1+ | Generated from code |
| Runbook | `docs/runbook.md` | operators/devs | M2 → M7 | Run, test, debug, reset DB, rotate secrets, GitHub OAuth App setup, common failures |
| Security checklist | `docs/security.md` | reviewers | M7 | ASVS L1 mapping, threat model link |
| Plan | `docs/plan/*` | you + me | now | Requirements & design |
| Changelog | `CHANGELOG.md` | everyone | M7 | Generated from Conventional Commits per milestone tag |

## 2. Runbook outline (`docs/runbook.md`)

1. Prerequisites (Docker ≥ 24 with Compose v2; for dev loop JDK 17 + Node 24).
2. Run the stack (local login) · default credentials · URLs (SPA, Swagger, Actuator inside network).
3. Run with GitHub login: create OAuth App (callback `http://127.0.0.1/`), fill `.env`, switch profile.
4. Dev loop (MySQL only + local apps).
5. Tests: backend unit/IT, frontend unit/component, E2E locally (compose e2e override + `npx playwright test`), coverage reports location.
6. Database: connect, inspect Flyway history, reset (`down -v`), add a migration (rules from [06 §4](06-data-model-and-migrations.md#4-flyway-strategy)).
7. Debugging: remote JVM debug port (dev only), logs/requestId correlation, common errors table (CORS rejected, redirect_uri mismatch, 503 auth provider, Flyway checksum mismatch, port already in use).
8. Secrets rotation (GitHub client secret).
9. Upgrading dependencies (Dependabot flow).

## 3. ADR process & template

- One ADR per major decision; numbered `NNN-kebab-title.md`; status `Proposed → Accepted → Superseded by NNN`. Never edit an accepted ADR's decision — supersede it.
- A PR that changes a decision must add/supersede an ADR **and** update [decision-log.md](decision-log.md) and every affected plan file.

```markdown
# ADR-NNN: <Title>
- Status: Proposed | Accepted | Superseded by ADR-XXX
- Date: YYYY-MM-DD
- Decision log: D#

## Context
<forces, constraints, requirements involved>

## Options considered
1. <Option> — pros / cons
2. …

## Decision
<what we chose, stated in one or two sentences>

## Consequences
- Positive: …
- Negative / risks: …
- Follow-ups: …
```

## 4. OpenAPI / Swagger conventions

- **Code-first** with springdoc, emitting **OpenAPI 3.0** (`springdoc.api-docs.version=openapi_3_0`, chosen for validator and `openapi-typescript` compatibility); an `OpenApiCustomizer` marks every non-nullable property `required` and replaces springdoc's `ProblemDetail` schema with the real wire format; the committed `docs/api/openapi.yaml` is the contract and changes only through reviewed PRs (snapshot test, [09 §3.5](09-testing-strategy.md#35-contract-tests)).
- `OpenApiConfig`: `info` (title "Plant Shop API", version from build, contact, license), servers (`http://127.0.0.1:8080`), security scheme `oauth2` (authorizationCode + PKCE, URLs per auth mode — [07 §8](07-auth-design.md#8-swagger-ui-authorize)), global security requirement, tags `Catalog`, `Cart`, `Account`, `Auth`.
- Every operation has: `operationId` (`listProducts`, `getCart`, `addCartItem`, `updateCartItem`, `removeCartItem`, `getMe`, `exchangeToken`), summary, description, required scopes in description, all response codes from [04 §4](04-backend-design.md#4-rest-api-contract) with `ProblemDetail` schema and named examples (e.g. `quantity-limit-exceeded`).
- Schemas: `@Schema` on records with `example`s, `format: decimal` + pattern for money strings, enums documented.
- Swagger UI: `persistAuthorization=false` (tokens not stored in localStorage), `tryItOutEnabled=true`, `displayRequestDuration=true`; disabled in prod by default.

## 5. Inline docs standards

Javadoc and TSDoc rules in [10 §4–5](10-code-quality-and-conventions.md#4-backend-conventions). Comments explain *why*; no commented-out code; `TODO` must reference an issue (`TODO(#12): …`; enforced by Checkstyle in Java, by PR review in TypeScript because ESLint's `no-warning-comments` cannot express the exception).
