# 07 — Auth Design

> Related: [03 sequences](03-architecture.md#4-sequence-diagrams) · [04 security config](04-backend-design.md#7-security-configuration) · [05 SPA auth](05-frontend-design.md#5-auth-integration) · [09 security tests](09-testing-strategy.md#34-security-tests)
> ADRs: [002](adr/002-github-oauth-direct-token.md), [003](adr/003-local-authorization-server-dev-e2e.md), [004](adr/004-spa-token-in-memory-pkce.md), [007](adr/007-cross-origin-cors-topology.md)

## 1. Modes

| `app.auth.mode` | Profiles | Identity source | Token presented to API | Validation |
|---|---|---|---|---|
| `github` | `prod` (default there) | GitHub OAuth App | GitHub opaque token (`gho_…`) | GitHub "check a token" API, cached 5 min |
| `local` | `dev`, `e2e` (default there) | In-memory users in Local AS | JWT (RS256), 15 min | In-process `JwtDecoder` against Local AS JWK |
| `github,local` | `dev` opt-in | Both | Either | Resolver by token shape + `iss` |

`prod` **refuses to start** if `local` is in the mode or the Local AS bean is present (startup guard + test, NFR-SEC-08).

## 2. Prod flow — GitHub, direct token (D1–D3)

Sequence: [03 §4.1](03-architecture.md#41-login--prod-github-direct-token).

1. SPA builds authorize request: `client_id`, `redirect_uri=https://<spa>/auth/callback`, `scope=read:user`, random 32-byte `state`, PKCE `code_challenge` (S256). `state`, `code_verifier`, `returnTo` saved in `sessionStorage` under one key (transient).
2. GitHub redirects back with `code`, `state`. SPA checks `state` (constant-time compare), deletes the transaction from storage.
3. SPA `POST /api/v1/auth/token` (form-encoded, RFC 6749 shape). Backend:
   - rate-limits (Bucket4j, 10/min/IP);
   - requires `grant_type=authorization_code`, `client_id == app.github.client-id`, `redirect_uri ∈ app.github.allowed-redirect-uris`, non-empty `code`, `code_verifier` matching RFC 7636 charset/length 43–128;
   - calls `POST {oauth-base}/login/oauth/access_token` with `client_id`, `client_secret`, `code`, `redirect_uri`, `code_verifier`, `Accept: application/json`;
   - GitHub signals errors with HTTP 200 + `error` field → mapped to 400 `invalid_grant`/`invalid_request`; network/5xx → 502.
   - returns `{access_token, token_type:"bearer", scope}`. **The token is not cached or stored server-side at this point.**
4. SPA keeps the token in memory and calls the API with `Authorization: Bearer`.
5. Backend `GitHubTokenIntrospector` (Spring `OpaqueTokenIntrospector`):
   - cache key = SHA-256(token) (raw token never kept in memory structures beyond the request);
   - miss → `POST {api-base}/applications/{client_id}/token` with HTTP Basic `client_id:client_secret`, body `{"access_token": …}`, headers `Accept: application/vnd.github+json`, `X-GitHub-Api-Version: 2022-11-28`;
   - **200** → token is valid *and issued to our app* → principal `github:{user.id}`, attributes `login`, `name`, `avatar_url`, granted authorities `SCOPE_catalog.read SCOPE_cart.read SCOPE_cart.write ROLE_SHOPPER`; cache 5 min (Caffeine, max 10k);
   - **404/422** → invalid or foreign token → 401; negative-cached 30 s;
   - timeout / 5xx / 403-rate-limited → 503 `auth-provider-unavailable` (not cached).

> **Refinement vs. the interview (approved 2026-11-13, P1):** we discussed validating via `GET /user`. That endpoint accepts *any* GitHub token (including personal access tokens and tokens of other apps), enabling token substitution. The "check a token" endpoint verifies the token belongs to **our** OAuth App and returns the user in one call. Same cost, strictly safer. Recorded in [ADR-002](adr/002-github-oauth-direct-token.md).

## 3. Dev/e2e flow — Local AS (D4)

Sequence: [03 §4.2](03-architecture.md#42-login--deve2e-local-as). Spring Authorization Server (part of Spring Security 7 in Boot 4) runs in the backend **only** under `@Profile({"dev","e2e"})`.

| Item | Value |
|---|---|
| Issuer | `app.auth.local.issuer` (default `http://127.0.0.1:8080`) |
| Signing key | RSA 2048 generated at startup (tokens invalid after restart — acceptable for dev/e2e) |
| Endpoints | `/oauth2/authorize`, `/oauth2/token`, `/oauth2/jwks`, `/.well-known/openid-configuration`, `/login` (form), `/connect/logout` (OIDC RP-initiated logout; `post_logout_redirect_uri` = `http://127.0.0.1:3000/login`) |
| Users (in-memory) | `shopper` (all scopes), `viewer` (`catalog.read` only), `e2e-user-1…4` (all scopes, one per Playwright worker). Passwords from env with dev defaults documented in `.env.example`; `{bcrypt}` encoded at startup via `DelegatingPasswordEncoder` |
| Access token | JWT, 15 min, claims `sub=<username>`, `scope`, `iss`, `aud=plant-api` |
| Refresh tokens | Not issued to public clients; re-auth via AS session (30 min idle) |
| Consent | Disabled for first-party clients |

Principal: `local:{sub}`; authorities from `scope` claim (`SCOPE_*`). `aud` must contain `plant-api`.

## 4. Registered clients

| Mode | Client ID | Type | Grant | Redirect URIs | Scopes |
|---|---|---|---|---|---|
| github | `$GITHUB_CLIENT_ID` (OAuth App) | Confidential at GitHub; **public toward the browser** (secret held by proxy) | Authorization Code (+ PKCE) | `http://127.0.0.1:3000/auth/callback`, `http://127.0.0.1:8080/swagger-ui/oauth2-redirect.html` (both allowed by one OAuth App with callback `http://127.0.0.1/` via GitHub's loopback rule — **verify in M2**) | `read:user` |
| local | `plant-spa` | Public, PKCE required | Authorization Code | `http://127.0.0.1:3000/auth/callback` | `openid catalog.read cart.read cart.write` |
| local | `swagger-ui` | Public, PKCE required | Authorization Code | `http://127.0.0.1:8080/swagger-ui/oauth2-redirect.html` | same |

Redirect URIs are exact-match from env (`APP_AUTH_LOCAL_SPA_REDIRECT_URI`, `APP_GITHUB_ALLOWED_REDIRECT_URIS`). Real prod deployments with distinct hostnames for SPA and API need a second GitHub OAuth App for Swagger; Swagger is off in prod by default (D: Swagger prod), so this is documented, not built.

## 5. SPA token handling (D5)

- Token held in `AuthProvider` React state (closure); never in `localStorage`/`sessionStorage`/cookies; not exposed on `window`.
- `sessionStorage` holds only the PKCE transaction (`state`, `code_verifier`, `returnTo`) between redirect and callback; deleted immediately after.
- Reload → re-login (provider usually auto-approves). 401 from API → single-flight re-login.
- Logout: clear state + `queryClient.clear()`; `local` → redirect to `/connect/logout?id_token_hint=…&post_logout_redirect_uri=…` on the Local AS; `github` → client-side only; the GitHub token is **not** revoked at GitHub (P7 declined — see T4).
- CSP restricts script sources to `'self'` (NFR-SEC-10) to reduce XSS token-exfiltration risk.

## 6. Scopes, authorities, endpoint rules

| Authority | Granted to | Required by |
|---|---|---|
| `SCOPE_catalog.read` | all GitHub users; `shopper`, `viewer`, `e2e-*` | `GET /products` |
| `SCOPE_cart.read` | all GitHub users; `shopper`, `e2e-*` | `GET /cart` |
| `SCOPE_cart.write` | all GitHub users; `shopper`, `e2e-*` | `POST/PATCH/DELETE /cart/**` |
| authenticated | any | `GET /me` |

Swagger UI's OAuth security scheme declares the same scopes so "Authorize" requests them (local mode) — in github mode the scheme lists `read:user` only.

## 7. CORS, redirects, CSRF, sessions — how the backend "gives access" to the SPA on another port

- **Origins**: SPA `http://127.0.0.1:3000` ≠ API `http://127.0.0.1:8080` → cross-origin (D14). Backend `CorsConfigurationSource` from `APP_CORS_ALLOWED_ORIGINS` (exact strings, no wildcards, no `null`).
- **Allowed**: methods `GET, POST, PATCH, DELETE, OPTIONS`; request headers `Authorization, Content-Type, Accept, X-Request-Id`; exposed headers `X-Request-Id, Retry-After, WWW-Authenticate`; `allowCredentials=false` (bearer tokens, no cookies); `maxAge=3600`.
- Applied to `/api/**`, `/oauth2/token` and `/oauth2/jwks` (local). `/oauth2/authorize` and `/login` are top-level navigations (no CORS).
- **Access grant**: the backend decides which SPA may obtain tokens via (a) registered client + exact redirect URI (local) or (b) proxy allowlist of `client_id` + `redirect_uri` (github), and which origins may *call* the API via CORS. Both lists come from env.
- **CSRF**: API chain stateless + bearer-only → CSRF disabled (no ambient credentials). Local AS form login is cookie-session based → CSRF **enabled** there. `/api/v1/auth/token` is protected by PKCE + `state` + rate limit, not CSRF.
- **Sessions**: API `STATELESS`; Local AS `IF_REQUIRED` (dev/e2e only); prod creates no HTTP sessions.

## 8. Swagger UI "Authorize"

springdoc config: `springdoc.swagger-ui.oauth.client-id`, `use-pkce-with-authorization-code-grant=true`, security scheme `oauth2` / `authorizationCode`:

- local: `authorizationUrl=/oauth2/authorize`, `tokenUrl=/oauth2/token`, client `swagger-ui`;
- github: `authorizationUrl=https://github.com/login/oauth/authorize`, `tokenUrl=/api/v1/auth/token`, client `$GITHUB_CLIENT_ID`.
Verified by an E2E spec (local mode) that authorises in Swagger and executes `GET /api/v1/cart` → 200.

## 9. Threat model

STRIDE-based, scoped to this system.

| # | Threat | Vector | Mitigation | Residual |
|---|---|---|---|---|
| T1 | Spoofing: token substitution | Attacker uses a GitHub PAT / other app's token | "Check a token" endpoint scoped to our `client_id` | — |
| T2 | Spoofing: code interception | Stolen `code` in redirect | PKCE S256, `state`, exact redirect allowlist, `Referrer-Policy: no-referrer` | Depends on GitHub PKCE support (verify M2) |
| T3 | Info disclosure: XSS steals token | Injected script | Memory-only token, strict CSP, React escaping, no `dangerouslySetInnerHTML` (lint rule), dependency updates | Token readable by script while page is open |
| T4 | Info disclosure: GitHub token lifetime | Leaked token valid until revoked by user | Short client lifetime (memory); revoke-on-logout declined (P7); user can revoke in GitHub settings | Non-expiring OAuth App tokens (D: OAuth App) |
| T5 | Info disclosure: secret leak | Logs, images, browser | Env-only secret, masking, no secret in `config.json`, CI grep check | — |
| T6 | Tampering: forged local JWT | Self-signed JWT | Signature via in-process JWK, `iss`/`aud`/`exp` checks; local mode absent in prod | — |
| T7 | Elevation: dev users in prod | Misconfigured profile | Startup guard + test; profile-gated beans | — |
| T8 | DoS: hammer `/auth/token` or GitHub check | Abuse | Bucket4j on proxy; Caffeine positive/negative caching; timeouts | Single-instance limits |
| T9 | DoS: GitHub outage | External | 5-min cache; 503 + Retry-After | New logins blocked |
| T10 | Repudiation | — | Structured logs with requestId + hashed ownerId for cart mutations | — |
| T11 | Cross-user cart access (IDOR) | Guessing ids | No owner/cart id in URLs; owner from principal only | — |
| T12 | CORS misconfig | Wildcard origin | Exact allowlist, tests for disallowed origin | — |
| T13 | Open redirect via `returnTo` | Crafted callback | `returnTo` must be a same-origin relative path (validated) | — |

## 10. Open items to verify in the M2 spike

1. GitHub honours `code_challenge`/`code_verifier` for OAuth Apps.
2. Loopback rule allows `127.0.0.1:3000` and `127.0.0.1:8080` with callback `http://127.0.0.1/`.
3. "Check a token" rate limits are acceptable with caching.
If (1) or (2) fail, fallback options are written up in [ADR-002](adr/002-github-oauth-direct-token.md) and brought back to you before proceeding.
