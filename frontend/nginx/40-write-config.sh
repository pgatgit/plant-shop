#!/bin/sh
# Writes /usr/share/nginx/html/config.json from environment variables (ADR-012).
# Runs from /docker-entrypoint.d/ before nginx starts; exits non-zero if a required value is missing.
set -eu

OUT=/usr/share/nginx/html/config.json

require() {
  eval "value=\${$1:-}"
  if [ -z "$value" ]; then
    echo "40-write-config: required environment variable $1 is not set" >&2
    exit 1
  fi
}

# JSON-escape backslashes and double quotes.
json() {
  printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'
}

for var in API_BASE_URL API_ORIGIN AUTH_PROVIDER AUTH_AUTHORIZE_URL AUTH_TOKEN_URL AUTH_CLIENT_ID AUTH_SCOPE AUTH_REDIRECT_URI; do
  require "$var"
done

case "$AUTH_PROVIDER" in
  local | github) ;;
  *)
    echo "40-write-config: AUTH_PROVIDER must be 'local' or 'github', got '$AUTH_PROVIDER'" >&2
    exit 1
    ;;
esac

cat >"$OUT" <<JSON
{
  "apiBaseUrl": "$(json "$API_BASE_URL")",
  "auth": {
    "provider": "$(json "$AUTH_PROVIDER")",
    "authorizeUrl": "$(json "$AUTH_AUTHORIZE_URL")",
    "tokenUrl": "$(json "$AUTH_TOKEN_URL")",
    "logoutUrl": "$(json "${AUTH_LOGOUT_URL:-}")",
    "clientId": "$(json "$AUTH_CLIENT_ID")",
    "scope": "$(json "$AUTH_SCOPE")",
    "redirectUri": "$(json "$AUTH_REDIRECT_URI")"
  }
}
JSON

echo "40-write-config: wrote $OUT (provider=$AUTH_PROVIDER)"
