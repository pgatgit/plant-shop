# ADR-009: Flyway versioned, forward-only migrations; Hibernate validate only

- Status: Accepted
- Date: 2026-11-13
- Decision log: fixed constraint; defaults round 1

## Context

Schema must be managed only via Flyway; no Hibernate DDL beyond validate. Seed data must ship via migration.

## Options considered

1. **Forward-only `V{n}__` migrations, immutable once applied, repeatable `R__` only for e2e fixtures, `clean` disabled** *(chosen)*.
2. Flyway undo (`U`) scripts — Teams/Enterprise feature, rarely reliable for data.
3. Liquibase — excluded by constraint.

## Decision

See [06 §4](../06-data-model-and-migrations.md#4-flyway-strategy). Corrections are new migrations; local reset by dropping the volume.

## Consequences

- Positive: reproducible schema everywhere; drift caught by `validate`.
- Negative: mistakes stay in history; seed changes need new migrations.
