# 00 — Overview

> Status: **Draft for review** · Phase 1 (planning) · Last updated 2026-11-13
> Decisions referenced as `D#` live in [decision-log.md](decision-log.md); ADRs live in [adr/](adr/).

## 1. Vision

**Plant Shop** is a small, production-grade web store for indoor plants. It is intentionally tiny in
features and deliberately strict in engineering quality: every capability is fully tested at every
layer, documented, observable, containerised and secured with OAuth 2.

## 2. In scope

| # | Capability | Summary | Spec |
|---|---|---|---|
| C1 | Product listing | Paged catalog (12 per page), sortable by name or price; all product info on cards | [01 §US-03, US-04](01-functional-requirements.md#us-03-browse-the-catalog) |
| C2 | Cart | Server-side per-user cart: add, view, update quantity, remove | [01 §US-06…US-09](01-functional-requirements.md#us-06-add-a-product-to-the-cart) |
| C3 | Search | Product **name prefix** search, case- and accent-insensitive | [01 §US-05](01-functional-requirements.md#us-05-search-products-by-name) |
| — | Authentication (enabler) | Login required for everything; GitHub in prod, local in-memory users in dev/e2e | [07](07-auth-design.md) |

## 3. Out of scope

Checkout, payments, orders, stock reservation, reviews/ratings, wishlists, admin UI or admin APIs,
product detail page (D10), catalog filters (D8), user self-registration and profile management,
email, i18n beyond `en-US`, SEO/SSR, multi-currency, guest carts, cart expiry, pushing images to a
registry (D16), Kubernetes/cloud deployment, distributed tracing backend (D13).

## 4. Glossary

| Term | Meaning |
|---|---|
| **Owner ID** | Stable identifier of the cart owner: `github:{numericId}` (prod) or `local:{username}` (dev/e2e). The only user data persisted. |
| **Local AS** | Spring Authorization Server embedded in the backend, **enabled only in `dev`/`e2e` profiles**, serving in-memory users and issuing JWTs (D4). |
| **Token exchange proxy** | `POST /api/v1/auth/token` — backend endpoint that adds the GitHub client secret to an Authorization-Code+PKCE exchange (D2). |
| **Token check** | Validation of a GitHub opaque token by the backend against GitHub, cached ≈5 min (D3). |
| **Line** | One cart item = one product + quantity. Re-adding a product increments its line. |
| **Line limit** | `min(stock, 10)` — max quantity of a line (D7). |
| **Prefix search** | `name LIKE 'term%'` under `utf8mb4_0900_ai_ci` collation (D9). |
| **Problem Details** | RFC 9457 (supersedes 7807) JSON error body, `application/problem+json`. |
| **Profiles** | `dev` (local apps), `e2e` (compose stack for E2E/demo), `prod` (GitHub only). See [08 §5](08-docker-and-environments.md#5-profiles). |

## 5. Assumptions

1. A single backend instance is run; in-memory caches (token check, rate limits) are per instance. ([02 §5](02-non-functional-requirements.md#5-reliability--scalability))
2. The repository owner registers **one GitHub OAuth App** for local use with callback URL `http://127.0.0.1/` and relies on GitHub's loopback rule (any port allowed for loopback redirect URIs) so the SPA (`:3000`) and Swagger UI (`:8080`) share it. Real prod hostnames need one app per origin. *(To verify in M2 spike — [12 §M2](12-implementation-roadmap.md#m2--authentication-both-modes--swagger-authorize).)*
3. GitHub's authorize endpoint accepts `code_challenge`/`S256` (PKCE). If not, PKCE degrades to defence-in-depth; the secret still never leaves the backend. *(Verify in M2 spike.)*
4. Local URLs use `127.0.0.1`, not `localhost`, so origins, redirect URIs and CORS are consistent.
5. `docker compose up` from a clean checkout runs the **`e2e` profile with local login**, because a clean checkout has no GitHub secrets. GitHub mode is enabled by setting env vars in `.env` ([08](08-docker-and-environments.md)).
6. Toolchain versions are "latest stable at M0, then pinned": Java 17 (Temurin), **Spring Boot 4.1.x** (Boot 3.5 OSS support has ended; 4.0 OSS ends ~Dec 2026 — [ADR-013](adr/013-spring-boot-4-on-java-17.md)), Node 24 LTS, React 19, MySQL 8.4 LTS.
7. Catalog size stays small (≈30 rows); designs are nonetheless index-correct for 10⁵ rows.

## 6. Document map

| File | Purpose |
|---|---|
| [01-functional-requirements.md](01-functional-requirements.md) | User stories, Given/When/Then, business rules, edge cases |
| [02-non-functional-requirements.md](02-non-functional-requirements.md) | Performance, security, a11y, reliability, observability, privacy |
| [03-architecture.md](03-architecture.md) | Context/container/sequence diagrams, folder structures |
| [04-backend-design.md](04-backend-design.md) | Packages, domain, REST contract, errors, security config |
| [05-frontend-design.md](05-frontend-design.md) | Routes, components, state, auth integration, UX states |
| [06-data-model-and-migrations.md](06-data-model-and-migrations.md) | ERD, tables, indexes, Flyway plan, seed catalog |
| [07-auth-design.md](07-auth-design.md) | OAuth flows, clients, scopes, CORS, threat model |
| [08-docker-and-environments.md](08-docker-and-environments.md) | Dockerfiles, compose, ports, env vars, profiles |
| [09-testing-strategy.md](09-testing-strategy.md) | Pyramid, tools, gates, examples, traceability |
| [10-code-quality-and-conventions.md](10-code-quality-and-conventions.md) | Linters, conventions, CI gates |
| [11-documentation-plan.md](11-documentation-plan.md) | Doc inventory, ADR template, OpenAPI conventions |
| [12-implementation-roadmap.md](12-implementation-roadmap.md) | Milestones, tasks, risks, DoD |
| [decision-log.md](decision-log.md) | Every question, answer and date |
