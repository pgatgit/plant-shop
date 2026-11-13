# ADR-010: Frontend stack — TypeScript, Vite, React Router, TanStack Query, Tailwind + Radix

- Status: Accepted
- Date: 2026-11-13
- Decision log: D11, R4-Q3, defaults round 1

## Context

Need strict typing, fast builds, accessible widgets (WCAG 2.2 AA), small bundle.

## Options considered

- UI: MUI (recommended, heavier) · **Tailwind + Radix** *(chosen)* · CSS Modules only.
- State: **TanStack Query + context** *(default)* vs Redux Toolkit.
- Build: **Vite** *(default)*.

## Decision

TS strict + Vite + React Router + TanStack Query; Tailwind v4 with Radix primitives; `openapi-typescript` for API types; `oauth4webapi` for OAuth helpers; no form library.

## Consequences

- Positive: small bundle, custom look, accessible primitives.
- Negative: more own components (Button, Alert, Stepper, Pagination) to build and test.
