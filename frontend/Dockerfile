# syntax=docker/dockerfile:1.7
# Plant Shop frontend image. See docs/plan/08-docker-and-environments.md §2.2.
#
# Optional BuildKit secret for builds behind a TLS-intercepting proxy (ignored when absent):
#   --secret id=extra_ca,src=/path/to/ca.crt

########################  build  ########################
FROM node:25-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    --mount=type=secret,id=extra_ca,required=false \
    if [ -s /run/secrets/extra_ca ]; then export NODE_EXTRA_CA_CERTS=/run/secrets/extra_ca; fi; \
    npm ci --no-audit --no-fund

COPY . .
RUN npm run build

########################  runtime  ########################
FROM nginxinc/nginx-unprivileged:stable-alpine AS runtime

ARG VERSION=dev
ARG REVISION=unknown
ARG CREATED=unknown
LABEL org.opencontainers.image.title="plant-shop-frontend" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}" \
      org.opencontainers.image.created="${CREATED}"

# The dev-only public/config.json is replaced at start-up by 40-write-config.sh, so the html
# directory (only) must be writable by the nginx user (uid 101).
USER root
COPY --from=build --chown=101:101 /app/dist/ /usr/share/nginx/html/
RUN rm -f /usr/share/nginx/html/config.json && chown 101:101 /usr/share/nginx/html
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --chmod=0755 nginx/40-write-config.sh /docker-entrypoint.d/40-write-config.sh

# Defaults match the local compose stack; override per environment.
ENV API_BASE_URL=http://127.0.0.1:8080/api/v1 \
    API_ORIGIN=http://127.0.0.1:8080 \
    AUTH_PROVIDER=local \
    AUTH_AUTHORIZE_URL=http://127.0.0.1:8080/oauth2/authorize \
    AUTH_TOKEN_URL=http://127.0.0.1:8080/oauth2/token \
    AUTH_LOGOUT_URL=http://127.0.0.1:8080/logout \
    AUTH_CLIENT_ID=plant-spa \
    AUTH_SCOPE="openid catalog.read cart.read cart.write" \
    AUTH_REDIRECT_URI=http://127.0.0.1:3000/auth/callback

USER 101
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1
