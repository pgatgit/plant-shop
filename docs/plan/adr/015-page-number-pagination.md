# ADR-015: Offset/page-number pagination, 12 per page, sort only

- Status: Accepted
- Date: 2026-11-13
- Decision log: D8, R3-Q4, R4-Q1

## Context

~28 products; accessibility and linkability favoured; user declined filters.

## Options considered

- **Page numbers** *(chosen)* · load-more/cursor · none.
- Filters: **sort only** *(chosen)* · category + pet-safe · rich.

## Decision

`GET /api/v1/products?page&size&sort&q` returning `PageResponse` with totals; sort whitelist with `id` tie-breaker; UI 1-based `?page=`.

## Consequences

- Positive: simple, accessible, deterministic tests.
- Negative: COUNT query per page (negligible); category field displayed but not filterable.
