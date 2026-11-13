# ADR-012: Runtime frontend configuration via generated `/config.json`

- Status: Accepted
- Date: 2026-11-13
- Decision log: defaults round 8

## Context

12-factor: one image must run in any environment; Vite `import.meta.env` bakes values at build time.

## Options considered

1. **nginx entrypoint writes `/config.json` from env; SPA fetches it before render** *(chosen)*.
2. Build-time `VITE_*` vars — image per environment.
3. Inject `window.__CONFIG__` into `index.html` via envsubst — works but mixes config into a cached HTML file.

## Decision

Option 1; `config.json` served `no-store`; startup script fails if required vars are missing; contains only public values (client id, URLs).

## Consequences

- Positive: one image, auditable config, testable loader.
- Negative: one extra request before first render (~1 ms locally).
