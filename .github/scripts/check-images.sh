#!/usr/bin/env bash
# Fails if an image runs as root or if its build history mentions a known secret variable.
# Usage: check-images.sh <image> [<image> ...]   (NFR-SEC-11, docs/plan/08 §6)
set -euo pipefail

status=0
for image in "$@"; do
  user=$(docker image inspect --format '{{.Config.User}}' "$image")
  if [[ -z "$user" || "$user" == "root" || "$user" == "0" || "$user" == 0:* ]]; then
    echo "FAIL $image runs as root (User='${user}')"
    status=1
  else
    echo "OK   $image runs as user '${user}'"
  fi

  if docker history --no-trunc --format '{{.CreatedBy}}' "$image" \
      | grep -Eiq 'GITHUB_CLIENT_SECRET=|MYSQL_(ROOT_)?PASSWORD=|DB_PASSWORD='; then
    echo "FAIL $image build history contains a secret assignment"
    status=1
  else
    echo "OK   $image history has no secret assignments"
  fi
done
exit "$status"
