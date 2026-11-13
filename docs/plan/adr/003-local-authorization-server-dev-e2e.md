# ADR-003: Embedded Spring Authorization Server with in-memory users for dev/e2e only

- Status: Accepted
- Date: 2026-11-13
- Decision log: D4, R1-Q4, R2-Q2, R3-Q2

## Context

Automated tests and local development cannot log into real GitHub (2FA, bot detection, secrets in CI). The user chose in-memory users for dev/test and a "dev-only local issuer". The fixed constraints name Spring Authorization Server.

## Options considered

1. **Spring Authorization Server, profile-gated** (`dev`, `e2e`), form login for in-memory users, JWT access tokens. *(chosen)*
2. Mock GitHub (WireMock / mock-oauth2-server) in compose — exercises prod path but adds a container.
3. Hand-rolled dev token endpoint — non-standard.

## Decision

Embed Spring Authorization Server in the backend under `@Profile({"dev","e2e"})`, with public PKCE clients `plant-spa` and `swagger-ui`, in-memory users (`shopper`, `viewer`, `e2e-user-1..4`), RS256 JWTs (15 min) validated in-process. A resolver chooses JWT vs GitHub validation by token shape and `iss`. Prod has a startup guard rejecting local mode.

## Consequences

- Positive: same SPA PKCE code in all modes; Swagger Authorize works offline; real scope tests (`viewer`).
- Negative: E2E does not exercise the GitHub path → covered by WireMock ITs and manual verification; two token types in the resolver.
