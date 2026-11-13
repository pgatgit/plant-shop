# ADR-005: Case-insensitive prefix search via `utf8mb4_0900_ai_ci` collation and B-tree index

- Status: Accepted
- Date: 2026-11-13
- Decision log: D9, R4-Q2

## Context

Search by product name must be case-insensitive. The user chose prefix matching (not contains/fuzzy).

## Options considered

1. **Column collation `utf8mb4_0900_ai_ci` + `LIKE 'term%'` + index on `name`** — sargable range scan; also accent-insensitive. *(chosen)*
2. `LOWER(name) LIKE LOWER(?)` — function on column defeats the index unless a functional index is added; redundant with a CI collation.
3. Separate normalised column (`name_search`) — explicit control, extra write-path complexity.
4. FULLTEXT ngram — fuzzy, unpredictable relevance; rejected with "contains"/"fuzzy" options.

## Decision

Option 1. Repository uses Spring Data `findByNameStartingWith` (escapes `%`/`_`) **without** `IgnoreCase`. Normalisation (trim, collapse spaces, 2–50 chars) in `SearchTerm` and mirrored in the SPA with shared test vectors.

## Consequences

- Positive: O(log n) lookup, no extra columns, accent-insensitive for free.
- Negative: "Monstera" doesn't find "Swiss Cheese Monstera" (accepted product behaviour); behaviour tied to MySQL collation → all repository tests run on real MySQL (no H2); EXPLAIN test needs ≥5k rows.
