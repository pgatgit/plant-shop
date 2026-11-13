# ADR-006: Server-side per-user cart with live prices and line limit min(stock, 10)

- Status: Accepted
- Date: 2026-11-13
- Decision log: D6, D7, R1-Q3, R2-Q3, R2-Q4

## Context

Login is required for everything; there is no checkout, so price snapshots and stock reservations have no downstream consumer.

## Options considered

- Persistence: server-side per user *(chosen; guest carts moot since login is required)* vs client-side.
- Pricing: **live** *(chosen)* vs snapshot + change notice.
- Limits: **1..min(stock,10), 409 if exceeded** *(chosen)* vs stock-only cap vs no checks.

## Decision

One cart per owner id (`github:{id}`/`local:{name}`), created lazily, no expiry. Re-adding increments the line. Quantity bounded by `min(stock,10)`; violations return 409 Problem Details with `limit`. Prices and totals computed at read time; each line reports `stockStatus`. Optimistic locking with one retry handles concurrent mutations.

## Consequences

- Positive: simple, correct totals; deterministic tests.
- Negative: stock shown/checked can be stale between read and add; carts are never cleaned up.
