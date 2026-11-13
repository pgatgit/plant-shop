# ADR-001: Record architecture decisions

- Status: Accepted
- Date: 2026-11-13
- Decision log: process

## Context

The plan is negotiated in rounds and will change. Decisions must stay traceable and consistent across plan files.

## Options considered

1. ADRs in-repo (Markdown, Nygard-style) — versioned with code, reviewable in PRs.
2. Wiki / external doc — drifts from code.
3. No formal record — knowledge lost.

## Decision

Use numbered Markdown ADRs in `docs/plan/adr/` with the template in [11 §3](../11-documentation-plan.md#3-adr-process--template); keep [decision-log.md](../decision-log.md) as the Q&A index.

## Consequences

- Positive: auditable history; PRs that change decisions are visible.
- Negative: discipline required to supersede rather than edit.
