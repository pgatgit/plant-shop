# ADR-002: GitHub OAuth App with GitHub tokens used directly as API bearer tokens

- Status: Accepted (incl. check-a-token refinement, approved 2026-11-13 as P1)
- Date: 2026-11-13
- Decision log: D1, D2, D3, D5, R3-Q3

## Context

Production identity must come from GitHub. GitHub OAuth Apps are plain OAuth 2 (no OIDC), issue **opaque, non-expiring** tokens, require a `client_secret` at the token endpoint and do not allow browser CORS calls to it. The SPA runs on its own origin and must use Authorization Code + PKCE with in-memory tokens (ADR-004).

## Options considered

1. **Our Spring Authorization Server federating GitHub, issuing our own JWTs** — standard, JWTs, but more moving parts (recommended in interview; declined).
2. Backend mints custom JWTs after GitHub exchange — hand-rolled protocol.
3. **GitHub token used directly** — backend provides a token-exchange proxy (adds the secret) and validates tokens against GitHub with caching. *(chosen)*

## Decision

- `POST /api/v1/auth/token` proxies the code exchange (allowlisted `client_id`/`redirect_uri`, rate-limited).
- The API is an OAuth2 resource server with a custom `OpaqueTokenIntrospector` that validates tokens via GitHub's **"check a token"** API (`POST /applications/{client_id}/token`, basic auth), caching positive results 5 min and negative 30 s.
- **Refinement:** the interview mentioned `GET /user`; the check-a-token endpoint is used instead because `/user` accepts tokens of *any* app (incl. PATs), enabling token substitution (threat T1).

## Consequences

- Positive: no AS to operate in prod; no key management; standard bearer flow for SPA and Swagger.
- Negative: no JWTs in prod; API availability depends on GitHub on cache misses; tokens don't expire (mitigated by memory-only storage); custom introspection code to test; prod Swagger needs a second OAuth App if on another host.
- Fallbacks if the M2 spike disproves assumptions: (a) GitHub ignores PKCE → keep flow, PKCE becomes defence-in-depth only; (b) loopback rule fails → register two OAuth Apps (SPA, Swagger), proxy accepts both client ids; (c) check-a-token unsuitable → `/user` + documented T1 risk, or revisit option 1. Any fallback is brought to the product owner first.
- Considered and declined (P7, 2026-11-13): revoke-on-logout via `DELETE /applications/{client_id}/token`. Logout is client-side only; a leaked token stays valid until the user revokes it in GitHub settings.
