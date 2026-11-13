# ADR-007: SPA and API on separate origins with strict CORS allowlist

- Status: Accepted
- Date: 2026-11-13
- Decision log: D14, R7-Q1

## Context

The brief requires the frontend on its own port and a deliberate CORS/redirect design.

## Options considered

1. **Cross-origin (SPA :3000, API :8080) + exact-origin CORS, no credentials** *(chosen)*.
2. nginx reverse-proxies `/api` → same origin, no CORS.

## Decision

Backend `CorsConfigurationSource` driven by `APP_CORS_ALLOWED_ORIGINS`; applies to `/api/**`, `/oauth2/token`, `/oauth2/jwks`; `allowCredentials=false`; preflight cached 1 h. Local URLs use `127.0.0.1` consistently.

## Consequences

- Positive: CORS exercised in every E2E run; API independently deployable.
- Negative: preflight per new request shape; two origins to configure per environment; CSP must list API origin.
