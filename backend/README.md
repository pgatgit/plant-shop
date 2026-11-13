# Plant Shop — Backend

Spring Boot 4.1 (Java 17) REST API for the Plant Shop: catalog, cart, name search and the OAuth2
resource server. Design: [docs/plan/04-backend-design.md](../docs/plan/04-backend-design.md).

> **Status:** M0 scaffold. Feature endpoints arrive in M1 (catalog), M2 (auth), M3 (cart).

## Prerequisites

- JDK 17 (Temurin recommended). Newer JDKs build too, but CI and the image use 17.
- Docker (Testcontainers-based integration tests from M1 on, and the image build).
- No local Maven needed: use the wrapper `./mvnw`.

## Commands

| Task | Command |
|---|---|
| Full build with every quality gate | `./mvnw verify` |
| Unit tests only (`*Test`) | `./mvnw test` |
| One integration test | `./mvnw verify -Dit.test=ActuatorIT -Djacoco.skip=true` |
| Format code | `./mvnw spotless:apply` |
| Run locally (dev profile) | `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` |
| Build image | `docker build -t plantshop/backend:local .` |

`./mvnw verify` runs, in order: Maven Enforcer → Spotless check → Checkstyle → compile
(`-Xlint:all -Werror`) → unit tests + ArchUnit → package → integration tests → JaCoCo merged
coverage check (line ≥ 85 %, branch ≥ 75 %) → SpotBugs + FindSecBugs.

Reports: `target/site/jacoco/index.html`, `target/surefire-reports`, `target/failsafe-reports`,
`target/spotbugsXml.xml`, `target/checkstyle-result.xml`.

## Ports and endpoints

| Port | Purpose |
|---|---|
| 8080 | Public API (`/api/v1/**`, from M1) |
| 8081 | Management: `/actuator/health/{liveness,readiness}`, `/actuator/prometheus`, `/actuator/info`. Not published outside the compose network. |

## Profiles

`dev` (local run, readable logs), `e2e` (compose default, JSON logs), `prod` (GitHub login, JSON
logs). Details: [08 §5](../docs/plan/08-docker-and-environments.md#5-profiles).

## Package layout

`com.plantshop.<feature>.{api,service,domain,repository,mapper}` plus `common`; rules are enforced
by `ArchitectureTest` (ArchUnit). See [10 §4](../docs/plan/10-code-quality-and-conventions.md#4-backend-conventions).

## Building behind a TLS-intercepting proxy

The Dockerfile accepts optional BuildKit secrets: `extra_ca` (a PEM CA certificate trusted by
curl and the JDK during the build) and `maven_settings` (a Maven `settings.xml` with mirrors or
proxies). Example:

```bash
docker build --secret id=extra_ca,src=/path/to/ca.crt --secret id=maven_settings,src=$HOME/.m2/settings.xml -t plantshop/backend:local .
```
