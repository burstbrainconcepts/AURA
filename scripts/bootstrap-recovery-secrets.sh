#!/usr/bin/env bash
set -euo pipefail

command -v openssl >/dev/null 2>&1 || { echo "OpenSSL is required." >&2; exit 1; }
if [[ -e .env ]]; then
  echo ".env already exists. Refusing to overwrite your existing secrets." >&2
  exit 1
fi

mkdir -p secrets
chmod 700 secrets
openssl genpkey -algorithm ED25519 -out secrets/aura-command-private.pem
chmod 600 secrets/aura-command-private.pem

PRIVATE_KEY_B64="$(base64 < secrets/aura-command-private.pem | tr -d '\n')"
ACCOUNT_TOKEN="$(openssl rand -hex 32)"
POSTGRES_PASSWORD="$(openssl rand -hex 24)"

cat > .env <<EOF
DATABASE_URL=postgresql://aura_user:${POSTGRES_PASSWORD}@localhost:5432/aura_recovery
AURA_COMMAND_PRIVATE_KEY_B64=${PRIVATE_KEY_B64}
AURA_BOOTSTRAP_ACCOUNT_ID=account-owner-01
AURA_BOOTSTRAP_ACCOUNT_TOKEN=${ACCOUNT_TOKEN}
POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
PORT=8787
EOF
chmod 600 .env

echo "Created .env and secrets/aura-command-private.pem."
echo "Keep both private. They are excluded from Git."
echo "Your account API token is stored in .env; do not paste it into public chats or commit it."
echo "Start the local stack with: docker compose --env-file .env -f docker-compose.recovery.yml up -d --build"
