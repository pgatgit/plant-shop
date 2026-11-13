# ADR-014: Actuator + ECS JSON logs + Prometheus metrics; Bucket4j only on token exchange

- Status: Accepted
- Date: 2026-11-13
- Decision log: D13, R5-Q2, R5-Q3

## Context

Production-grade but small; one endpoint (token exchange) uses our GitHub secret and is public.

## Options considered

- Observability: **Actuator + JSON logs + Prometheus** *(chosen)* · + OTel/Grafana stack · health only.
- Protection: **Bucket4j on `/auth/token`** *(chosen)* · + product cache · none.

## Decision

Spring Boot built-in structured logging (ECS), Micrometer Tracing for trace ids (no exporter), Prometheus registry on management port 8081, custom metrics (cart mutations, token checks, exchanges). In-memory Bucket4j bucket per IP (10/min) and Caffeine token cache.

## Consequences

- Positive: operable without extra containers.
- Negative: in-memory state → single instance only (NFR-REL-07); no tracing UI.
