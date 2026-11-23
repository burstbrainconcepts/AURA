#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

command -v docker >/dev/null 2>&1 || { echo "Docker Engine is required." >&2; exit 1; }
docker compose version >/dev/null 2>&1 || { echo "Docker Compose v2 is required." >&2; exit 1; }
command -v curl >/dev/null 2>&1 || { echo "curl is required for the HTTPS health check." >&2; exit 1; }

if [[ ! -f .env ]]; then
  echo "No .env found. Generate private recovery secrets first:"
  echo "  bash scripts/bootstrap-recovery-secrets.sh"
  exit 2
fi

set -a
# shellcheck disable=SC1091
source .env
set +a

: "${POSTGRES_PASSWORD:?Set POSTGRES_PASSWORD in .env}"
: "${AURA_COMMAND_PRIVATE_KEY_B64:?Set AURA_COMMAND_PRIVATE_KEY_B64 in .env}"
: "${AURA_BOOTSTRAP_ACCOUNT_ID:?Set AURA_BOOTSTRAP_ACCOUNT_ID in .env}"
: "${AURA_BOOTSTRAP_ACCOUNT_TOKEN:?Set AURA_BOOTSTRAP_ACCOUNT_TOKEN in .env}"
: "${AURA_DOMAIN:?Set AURA_DOMAIN in .env}"
: "${AURA_ACME_EMAIL:?Set AURA_ACME_EMAIL in .env}"

if [[ "$AURA_DOMAIN" == *"://"* || "$AURA_DOMAIN" == */* || "$AURA_DOMAIN" == *" "* || "$AURA_DOMAIN" == *"example.com"* ]]; then
  echo "AURA_DOMAIN must be your real DNS hostname only (for example, api.yourdomain.com)." >&2
  exit 1
fi

COMPOSE=(docker compose --env-file .env -f docker-compose.production.yml)
"${COMPOSE[@]}" config --quiet
"${COMPOSE[@]}" up -d --build

echo "Waiting for https://$AURA_DOMAIN/health ..."
for attempt in $(seq 1 36); do
  if curl --fail --silent --show-error --max-time 5 "https://$AURA_DOMAIN/health" >/dev/null 2>&1; then
    echo "AURA Recovery API is healthy at https://$AURA_DOMAIN"
    exit 0
  fi
  sleep 5
done

echo "The HTTPS health check did not succeed. Recent service logs:" >&2
"${COMPOSE[@]}" logs --tail=100 recovery caddy >&2 || true
exit 1
