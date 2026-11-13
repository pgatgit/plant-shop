# ADR-011: Testing strategy — real MySQL via Testcontainers, no H2; contract via committed OpenAPI

- Status: Accepted
- Date: 2026-11-13
- Decision log: D17, R5-Q4

## Context

Testing is the top priority. Search semantics depend on MySQL collation; auth depends on external GitHub.

## Options considered

- DB in tests: **Testcontainers MySQL 8.4** *(chosen)* vs H2 (MySQL mode) — H2 collations differ, false confidence.
- External IdP in tests: **WireMock** for GitHub *(chosen)*.
- Contract: **committed OpenAPI snapshot + response validation + generated TS types** *(chosen)* vs Pact (overkill for one consumer).
- Coverage: **BE 85/75, FE 80/70** *(chosen)*; mutation testing declined.

## Decision

Full pyramid as in [09](../09-testing-strategy.md); Playwright E2E on the compose stack (e2e profile).

## Consequences

- Positive: high-fidelity tests; spec drift caught on both sides.
- Negative: slower IT suite; Docker required for backend tests.
