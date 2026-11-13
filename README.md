# Plant Shop

A small, production-grade web store for indoor plants: browse a paged catalog, search plants by
name (prefix, case- and accent-insensitive) and manage a cart. Every page requires login: GitHub
in production, local in-memory users for development and E2E.

> **Status:** milestones **M0–M3** complete (tooling, catalog API, authentication, cart API). The
> SPA follows in M4–M6 per the [implementation roadmap](docs/plan/12-implementation-roadmap.md).

| Part | Tech | Docs |
|---|---|---|
| [`frontend/`](frontend/) | React 19, TypeScript, Vite, Tailwind + Radix, served by nginx | [frontend/README.md](frontend/README.md) |
| [`backend/`](backend/) | Spring Boot 4.1 on Java 17, Spring Security (OAuth2), JPA, Flyway | [backend/README.md](backend/README.md) |
| MySQL | 8.4 LTS, `utf8mb4_0900_ai_ci` | [data model](docs/plan/06-data-model-and-migrations.md) |

## Quick start

Prerequisite: Docker with Compose v2.

```bash
docker compose up -d --build --wait
```

| URL | What |
|---|---|
| <http://127.0.0.1:3000> | The store (SPA) |
| <http://127.0.0.1:8080/swagger-ui.html> | Swagger UI ("Authorize" with user `shopper`, password `plants-dev-only`) |

Stop with `docker compose down`; reset the database with `docker compose down -v`.

A clean checkout runs the `e2e` profile with local login and needs no secrets. To use GitHub login,
follow [Run with GitHub login](docs/runbook.md#3-run-with-github-login-on-your-own-machine) in the
runbook.

## Development

- Day-to-day loop: `docker compose up -d mysql`, then run the backend and frontend locally
  (see each service README).
- Full gates: `./mvnw verify` in `backend/`, `npm run ci` in `frontend/`.
- E2E: `docker compose -f compose.yaml -f compose.e2e.yaml up -d --build --wait`, then
  `npm run e2e` in `frontend/`.

## Documentation

- Plan and requirements: [docs/plan/](docs/plan/00-overview.md)
- Architecture decisions: [docs/plan/adr/](docs/plan/adr/001-record-architecture-decisions.md)
- Decision log: [docs/plan/decision-log.md](docs/plan/decision-log.md)
- Runbook (run, test, debug, GitHub setup): [docs/runbook.md](docs/runbook.md)
- API contract: [docs/api/openapi.yaml](docs/api/openapi.yaml)

## Contributing

Trunk-based development with short-lived branches, Conventional Commits and squash merges. CI
(`.github/workflows/ci.yml`) must be green. See
[conventions](docs/plan/10-code-quality-and-conventions.md).
