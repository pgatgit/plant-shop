# Runbook

How to run, test, debug and operate Plant Shop. Started in M2; completed in M7
([documentation plan](plan/11-documentation-plan.md#2-runbook-outline-docsrunbookmd)).

## 1. Prerequisites

- Docker Engine ≥ 24 with Compose v2 (`docker compose version`).
- For the day-to-day loop without containers: JDK 17 and Node.js 24.

## 2. Run the stack (local login, no secrets needed)

```bash
docker compose up -d --build --wait
```

| URL | What |
|---|---|
| <http://127.0.0.1:3000> | The store (SPA; login UI arrives in M4) |
| <http://127.0.0.1:8080/swagger-ui.html> | Swagger UI |
| <http://127.0.0.1:8080/login> | Local Authorization Server login page |

Always use `127.0.0.1`, not `localhost`: origins, redirect URIs and CORS are configured for it.

### Local users (profiles `dev` and `e2e` only)

All share the password from `APP_LOCAL_USER_PASSWORD` (default `plants-dev-only`).

| User | Scopes | Purpose |
|---|---|---|
| `shopper` | `catalog.read cart.read cart.write` | Manual testing |
| `viewer` | `catalog.read` | Checking 403 on cart endpoints |
| `e2e-user-1` … `e2e-user-4` | all | One per Playwright worker |

### Try the API in Swagger UI

1. Open <http://127.0.0.1:8080/swagger-ui.html> and click **Authorize**.
2. `client_id` is pre-filled with `swagger-ui`. Click **select all** for the scopes, then **Authorize**.
3. Sign in as `shopper` in the pop-up. The pop-up closes when done.
4. **Close** the dialog, expand an operation, click **Try it out**, then **Execute**.

The **Cart** operations act on the signed-in user's own cart: `POST /api/v1/cart/items` with
`{"productId": <id>}` adds one; product ids come from `GET /api/v1/products`.

## 3. Run with GitHub login (on your own machine)

The browser must reach `127.0.0.1:3000` on the machine running the stack, so do this locally, not
in a remote sandbox.

### 3.1 Register a GitHub OAuth App (once)

GitHub → profile picture → **Settings** → **Developer settings** → **OAuth Apps** → **New OAuth
App** (or the organization's Developer settings):

| Field | Value |
|---|---|
| Application name | `Plant Shop (local)` |
| Homepage URL | `http://127.0.0.1:3000` |
| Authorization callback URL | `http://127.0.0.1/` |
| Enable Device Flow | unchecked |

Then **Register application**, copy the **Client ID**, click **Generate a new client secret** and
copy it immediately (GitHub shows it once). Do not use a personal access token: the backend only
accepts tokens issued to this OAuth App.

### 3.2 Configure `.env` (git-ignored)

```bash
cp .env.example .env
```

Set these values in `.env`:

```dotenv
SPRING_PROFILES_ACTIVE=prod
GITHUB_CLIENT_ID=<client id>
GITHUB_CLIENT_SECRET=<client secret>
APP_SWAGGER_ENABLED=true
AUTH_PROVIDER=github
AUTH_AUTHORIZE_URL=https://github.com/login/oauth/authorize
AUTH_TOKEN_URL=http://127.0.0.1:8080/api/v1/auth/token
AUTH_LOGOUT_URL=
AUTH_CLIENT_ID=<client id>
AUTH_SCOPE=read:user
```

`APP_SWAGGER_ENABLED=true` is optional; Swagger is off in `prod` by default.

```bash
docker compose up -d --build --wait
```

### 3.3 Verify GitHub login (M2 spike checklist)

These confirm the assumptions in [ADR-002](plan/adr/002-github-oauth-direct-token.md). Until the
SPA login arrives in M4, use Swagger UI:

1. Open <http://127.0.0.1:8080/swagger-ui.html> → **Authorize**. `client_id` shows your GitHub
   client id; tick `read:user`; **Authorize**. GitHub asks you to authorize the app.
2. **Loopback rule (P4):** the pop-up returns to
   `http://127.0.0.1:8080/swagger-ui/oauth2-redirect.html` even though the registered callback is
   `http://127.0.0.1/`. If GitHub says the redirect URI does not match, the loopback assumption is
   wrong; report it.
3. **PKCE:** the authorize URL in the pop-up contains `code_challenge=…&code_challenge_method=S256`
   and the exchange succeeds. A failure mentioning the verifier means GitHub rejected PKCE; report
   it.
4. **Check-a-token:** **Try it out** on `GET /api/v1/me` returns `200` with
   `"ownerId": "github:<your numeric id>"`.
5. A personal access token is rejected with `401`:
   `curl -i -H "Authorization: Bearer <personal access token>" http://127.0.0.1:8080/api/v1/me`.

## 4. Day-to-day development loop

```bash
docker compose up -d mysql
```

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

```bash
cd frontend && npm run dev
```

The `dev` profile uses local login. To also accept GitHub tokens, export `APP_AUTH_MODE=github,local`
plus `GITHUB_CLIENT_ID` and `GITHUB_CLIENT_SECRET` before starting the backend.

## 5. Tests

| What | Command |
|---|---|
| Backend: everything (needs Docker for Testcontainers) | `cd backend && ./mvnw verify` |
| Backend: regenerate the committed OpenAPI contract | `cd backend && ./mvnw verify -Pupdate-openapi` |
| Frontend: everything | `cd frontend && npm run ci` |
| E2E: start the stack | `docker compose -f compose.yaml -f compose.e2e.yaml up -d --build --wait` |
| E2E: run Playwright | `cd frontend && npm run e2e` |

Reports: `backend/target/site/jacoco/index.html`, `frontend/coverage/index.html`,
`frontend/playwright-report/index.html`.

## 6. Database

| Task | Command |
|---|---|
| SQL shell | `docker compose exec mysql mysql -uplantshop -pplantshop-dev plantshop` |
| Migration history | `SELECT version, script, success FROM flyway_schema_history;` |
| Reset everything | `docker compose down -v && docker compose up -d --wait` |

Never edit an applied migration; add a new `V{n}__*.sql`
([06 §4](plan/06-data-model-and-migrations.md#4-flyway-strategy)).

## 7. Common problems

| Symptom | Cause and fix |
|---|---|
| Backend exits with `Invalid authentication configuration` | Missing `GITHUB_CLIENT_ID`/`SECRET` in GitHub mode, or `local` mode in `prod`. The message lists every problem. |
| `401` with `error="invalid_token"` | Token expired (local tokens last 15 minutes), revoked, or issued to another GitHub app. |
| `503 auth-provider-unavailable` | GitHub could not be reached; retry after `Retry-After` seconds. Cached tokens keep working for up to 5 minutes. |
| `429 rate-limited` on `/api/v1/auth/token` | More than 10 token requests per minute from one IP. |
| Browser: CORS error | The page origin is not in `APP_CORS_ALLOWED_ORIGINS` (use `http://127.0.0.1:3000`, not `localhost`). |
| GitHub: "redirect_uri is not associated with this application" | Callback URL in the OAuth App is not `http://127.0.0.1/`, or the redirect URI is missing from `APP_GITHUB_ALLOWED_REDIRECT_URIS`. |
| Flyway `checksum mismatch` | An applied migration was edited. Revert it, or reset a local DB with `docker compose down -v`. |
| `409 quantity-limit-exceeded` / `out-of-stock` | A cart line may hold at most min(stock, 10); `limit` in the body says how many. |
| `409 concurrent-modification` | Two requests changed the same cart at once and the automatic retry also conflicted. Repeat the request. |
| Port already in use | Another process uses 3000, 8080 or 3306; stop it or change the published port in `compose.yaml`. |

## 8. Tracing a request in the logs

Every response has an `X-Request-Id` header (a caller may send its own, up to 64 characters of
`A-Z a-z 0-9 . _ -`), and every error body has a `traceId`. Both appear on each log line of that
request: in `e2e`/`prod` as the ECS JSON fields `requestId` and `traceId`, in `dev` as
`[<traceId>-<spanId>]` after the thread name. Authenticated requests also log `ownerHash`, the first 8 hex
characters of SHA-256 of the owner id, never the id itself:

```bash
docker compose logs backend | grep <traceId>
```

Cart mutations are counted in `plantshop_cart_mutations_total{op,outcome}`. The management port
8081 is not published, so read it from inside the container:

```bash
docker compose exec backend curl -s http://127.0.0.1:8081/actuator/prometheus | grep plantshop_cart
```

## 9. Rotating the GitHub client secret

Generate a new secret on the OAuth App page, update `GITHUB_CLIENT_SECRET`, restart the backend
(`docker compose up -d backend`), then delete the old secret on GitHub.
