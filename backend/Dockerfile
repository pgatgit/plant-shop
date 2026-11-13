# syntax=docker/dockerfile:1.7
# Plant Shop backend image. See docs/plan/08-docker-and-environments.md §2.1.
#
# Optional BuildKit secrets for builds behind a TLS-intercepting proxy or private mirror
# (both are ignored when absent):
#   --secret id=extra_ca,src=/path/to/ca.crt        extra CA trusted by curl and the JDK
#   --secret id=maven_settings,src=~/.m2/settings.xml  Maven settings (mirrors/proxies)

########################  build  ########################
FROM eclipse-temurin:17-jdk-noble AS build
WORKDIR /workspace

RUN --mount=type=secret,id=extra_ca,required=false \
    if [ -s /run/secrets/extra_ca ]; then \
      cp /run/secrets/extra_ca /usr/local/share/ca-certificates/extra-ca.crt \
      && update-ca-certificates >/dev/null \
      && keytool -importcert -noprompt -cacerts -storepass changeit -alias extra-ca \
           -file /run/secrets/extra_ca >/dev/null; \
    fi

# Dependencies first for layer caching.
COPY mvnw pom.xml ./
COPY .mvn/ .mvn/
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=secret,id=maven_settings,required=false \
    SETTINGS=""; [ -s /run/secrets/maven_settings ] && SETTINGS="-s /run/secrets/maven_settings"; \
    ./mvnw -B -ntp $SETTINGS dependency:go-offline

# Tests and static analysis run in CI (`./mvnw verify`), not in the image build.
COPY config/ config/
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=secret,id=maven_settings,required=false \
    SETTINGS=""; [ -s /run/secrets/maven_settings ] && SETTINGS="-s /run/secrets/maven_settings"; \
    ./mvnw -B -ntp $SETTINGS -DskipTests -Dspotless.check.skip -Dcheckstyle.skip \
      -Dspotbugs.skip -Djacoco.skip -Denforcer.skip package \
    && java -Djarmode=tools -jar target/plant-shop-backend.jar extract --layers --launcher \
         --destination target/extracted

########################  runtime  ########################
FROM eclipse-temurin:17-jre-noble AS runtime

ARG VERSION=dev
ARG REVISION=unknown
ARG CREATED=unknown
LABEL org.opencontainers.image.title="plant-shop-backend" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}" \
      org.opencontainers.image.created="${CREATED}"

# Non-root user (uid/gid 10001). curl ships with the base image and is used by health checks.
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app

WORKDIR /app
# Least-frequently-changing layers first.
COPY --from=build --chown=10001:10001 /workspace/target/extracted/dependencies/ ./
COPY --from=build --chown=10001:10001 /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build --chown=10001:10001 /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=10001:10001 /workspace/target/extracted/application/ ./

USER 10001:10001
EXPOSE 8080 8081
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
  CMD curl -fsS http://127.0.0.1:8081/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
