# Plant Shop — Backend

Spring Boot 4.1 (Java 17) REST API for the Plant Shop: catalog, cart, name search and the OAuth2
resource server. Design: [docs/plan/04-backend-design.md](../docs/plan/04-backend-design.md).

> **Status:** M2 done: catalog API plus OAuth2 in both modes (GitHub tokens in `prod`, the embedded
> Local Authorization Server with in-memory users in `dev`/`e2e`). The cart arrives in M3.

## Prerequisites

- JDK 17 (Temurin recommended). Newer JDKs build too, but CI and the image use 17.
- Docker: repository tests and integration tests start MySQL 8.4 with Testcontainers (no H2).
- No local Maven needed: use the wrapper `./mvnw`.

## Commands

| Task | Command |
|---|---|
| Full build with every quality gate | `./mvnw verify` |
| Unit tests only (`*Test`) | `./mvnw test` |
| One integration test | `./mvnw verify -Dit.test=ActuatorIT -Djacoco.skip=true` |
| Format code | `./mvnw spotless:apply` |
| Regenerate the committed OpenAPI contract | `./mvnw verify -Pupdate-openapi` (then commit `docs/api/openapi.yaml`) |
| Run locally (dev profile) | `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` |
| Build image | `docker build -t plantshop/backend:local .` |

`./mvnw verify` runs, in order: Maven Enforcer → Spotless check → Checkstyle → compile
(`-Xlint:all -Werror`) → unit tests + ArchUnit → package → integration tests → JaCoCo merged
coverage check (line ≥ 85 %, branch ≥ 75 %) → SpotBugs + FindSecBugs.

Reports: `target/site/jacoco/index.html`, `target/surefire-reports`, `target/failsafe-reports`,
`target/spotbugsXml.xml`, `target/checkstyle-result.xml`.

## Ports and endpoints

| Port | Purpose |
|---|---|
| 8080 | Public API (`/api/v1/**`); Swagger UI at `/swagger-ui.html` and the spec at `/v3/api-docs` when `APP_SWAGGER_ENABLED=true` (default in `dev`/`e2e`, off in `prod`) |
| 8081 | Management: `/actuator/health/{liveness,readiness}`, `/actuator/prometheus`, `/actuator/info`. Not published outside the compose network. |

## Profiles

`dev` (local run, readable logs), `e2e` (compose default, JSON logs), `prod` (GitHub login, JSON
logs). Details: [08 §5](../docs/plan/08-docker-and-environments.md#5-profiles).

## API

| Endpoint | Scope | Notes |
|---|---|---|
| `GET /api/v1/products?page&size&sort&q` | `catalog.read` | Page-number pagination (default 12, max 48), `sort` ∈ `name,asc`·`name,desc`·`price,asc`·`price,desc`, `q` = case/accent-insensitive **name prefix** (2–50 chars) |
| `GET /api/v1/me` | any valid token | The signed-in shopper (`ownerId`, `displayName`, `avatarUrl`, `provider`) |
| `POST /api/v1/auth/token` | public, 10/min/IP | GitHub mode only: Authorization Code + PKCE exchange; the backend adds the client secret |

## Authentication

`app.auth.mode` selects token sources: `github` (default; `prod`), `local` (default in `dev`/`e2e`)
or `github,local`. `TokenAuthenticationManagerResolver` routes a JWT issued by the Local AS to the
in-process decoder and everything else to `GitHubTokenIntrospector`, which calls GitHub's "check a
token" API and caches results (5 minutes valid, 30 seconds invalid). `AuthStartupValidator`
refuses to start with unsafe combinations (for example `local` in `prod`). The Local AS (dev/e2e
only) serves `/oauth2/authorize`, `/oauth2/token`, `/oauth2/jwks`, `/login` and
`/connect/logout`. See [07-auth-design](../docs/plan/07-auth-design.md) and the
[runbook](../docs/runbook.md) for local users and GitHub setup.

Errors are RFC 9457 Problem Details with a stable `code` (see `ProblemCode`). The contract is
`docs/api/openapi.yaml`; `OpenApiSnapshotIT` fails on drift and every integration-test exchange is
validated against it.

## Database

Flyway owns the schema (`src/main/resources/db/migration`, forward-only, never edit an applied
file); Hibernate only validates. `V5__seed_products.sql` seeds 28 plants. The `e2e` profile also
applies `db/e2e/R__e2e_fixtures.sql`. Reset a local database with `docker compose down -v`.

## Package layout

`com.plantshop.<feature>.{api,service,domain,repository,mapper}` plus `common`; rules are enforced
by `ArchitectureTest` (ArchUnit). See [10 §4](../docs/plan/10-code-quality-and-conventions.md#4-backend-conventions).

## Building behind a TLS-intercepting proxy

The Dockerfile accepts optional BuildKit secrets: `extra_ca` (a PEM CA certificate trusted by
curl and the JDK during the build) and `maven_settings` (a Maven `settings.xml` with mirrors or
proxies). Example:

```bash
docker build --secret id=extra_ca,src=/path/to/ca.crt --secret id=maven_settings,src=$HOME/.m2/settings.xml -t plantshop/backend:local .
```
