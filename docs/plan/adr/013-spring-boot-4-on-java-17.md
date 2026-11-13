# ADR-013: Spring Boot 4.1.x on Java 17

- Status: Accepted (amends round-1 default "Boot 3.x"; approved 2026-11-13 as P2; minor line set to 4.1 at M0, R10)
- Date: 2026-11-13
- Decision log: D21

## Context

Java 17 is fixed. As of 2026-11, Spring Boot 3.5's open-source support window has ended. Boot 4 (Spring Framework 7, Spring Security 7 with Authorization Server included, Jackson 3) supports a Java 17 baseline. At M0 version verification, Maven Central offered 4.0.9 and 4.1.2; the 4.0 line's OSS support ends around 2026-12.

## Options considered

1. **Boot 4.1.x latest patch (4.1.2 at M0)** — longest OSS runway. *(chosen)*
2. Boot 4.0.x — the originally approved line; OSS support ends in about a month.
3. Boot 3.5.x — OSS support ended (commercial only).

## Decision

Use Boot 4.1.x, pinning exact versions of springdoc (3.x line), Testcontainers (2.x), MapStruct, Bucket4j at M0 after verifying compatibility.

## Consequences

- Positive: security patches, long runway.
- Negative: newer APIs (Jackson 3 packages, Security 7 DSL); fewer community examples.
