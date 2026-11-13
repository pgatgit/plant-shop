# ADR-008: Layered architecture, packaged by feature, enforced by ArchUnit

- Status: Accepted
- Date: 2026-11-13
- Decision log: D12, R5-Q1

## Context

Three small features; testability and clear boundaries matter more than framework independence.

## Options considered

1. **Package-by-feature with controller → service → repository layers** *(chosen)*.
2. Hexagonal ports/adapters — framework-free domain, more ceremony.

## Decision

Packages `catalog`, `cart`, `auth`, `common`, each with `api/service/domain/repository/mapper`. ArchUnit enforces dependency direction, no cycles, DTO/entity separation. JPA entities double as domain model; MapStruct maps to DTO records.

## Consequences

- Positive: low ceremony, easy navigation, rules machine-checked.
- Negative: domain coupled to JPA annotations; a future move to hexagonal needs refactoring.
