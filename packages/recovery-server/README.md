# AURA Recovery API

AURA's first remote recovery service: PostgreSQL-backed device enrollment, account-scoped device registry, signed command queue, device acknowledgements, and an audit trail.

## What is implemented

- Ed25519 device enrollment proof with a five-minute one-time challenge.
- The challenge binds the device ID, device public-key fingerprint, platform, label, and exact capability list.
- Random 256-bit device bearer tokens are returned once; only SHA-256 token hashes are stored.
- Account-scoped device listing and revocation.
- Account-scoped command history and audit-event history endpoints, with bounded result limits.
- Server-side Ed25519 signatures for queued commands. Device identity keys and command-signing keys are separate.
- Capability checks, explicit account approval for screen-lock and location-refresh commands, short command expiry, and device acknowledgements.
- PostgreSQL audit events for enrollment, revocation, command issuance, acknowledgements, and consented location updates.
- Opt-in location check-ins are accepted only when the enrolled device declared the location-consent capability; stale or out-of-range coordinates are rejected. Account owners can view the last reported coordinates and clear them.
- No remote wipe, passcode reset, covert tracking, camera activation, or bypass of native operating-system permissions.

## Required environment

- Node.js 22+
- PostgreSQL
- `DATABASE_URL`
- `AURA_COMMAND_PRIVATE_KEY_B64`: base64-encoded Ed25519 PKCS#8 private-key PEM. Keep this secret on the server only.
- Optional first account bootstrap:
  - `AURA_BOOTSTRAP_ACCOUNT_ID`: stable ID, 8–128 characters from letters, numbers, dot, underscore, colon, or hyphen.
  - `AURA_BOOTSTRAP_ACCOUNT_TOKEN`: random account API token of at least 32 characters. The database stores only its SHA-256 hash.
- `PORT` (defaults to 8787).

Generate an Ed25519 server key pair with OpenSSL:

```bash
openssl genpkey -algorithm ED25519 -out aura-command-private.pem
openssl pkey -in aura-command-private.pem -pubout -out aura-command-public.pem
base64 -w 0 aura-command-private.pem
```

Store the base64 output as `AURA_COMMAND_PRIVATE_KEY_B64` in the server secret manager. Never commit the private key or put it in the Android app.

## Run locally with Docker

Generate local secrets without overwriting an existing `.env`:

```bash
bash scripts/bootstrap-recovery-secrets.sh
docker compose --env-file .env -f docker-compose.recovery.yml up -d --build
```

The script creates a private Ed25519 signing key under the Git-ignored `secrets/` directory and a Git-ignored `.env` containing random account and database credentials. For hosted Railway deployment, generate the same values locally and add them as service secrets instead of committing them.

The Compose stack starts PostgreSQL, applies the migrations, and starts the API. For Railway, add a PostgreSQL service and set `DATABASE_URL`, `AURA_COMMAND_PRIVATE_KEY_B64`, `AURA_BOOTSTRAP_ACCOUNT_ID`, and `AURA_BOOTSTRAP_ACCOUNT_TOKEN` as service variables. The root `Dockerfile` and `railway.json` configure the deployment and health check.

## Run directly with Node

From the repository root:

```bash
npm install
npm run migrate --workspace @aura/recovery-server
npm run start --workspace @aura/recovery-server
```

The service must be exposed through HTTPS (for example, behind Caddy or another TLS reverse proxy). Do not expose the raw HTTP listener directly to the public internet.

## Account console

After deploying the API, open `https://your-recovery-host/console`. Enter the bootstrap account token to view enrolled devices, inspect the last consented location, review command outcomes and audit activity, request a confirmed screen lock, revoke a device, or clear stored location. The browser keeps the token only in page memory; disconnect or close the page to clear it.

## API

- `GET /health`
- `GET /v1/server-key` — public Ed25519 command-verification key.
- `POST /v1/enrollment/challenges` — account bearer token; device ID, public key, platform, label, and allowlisted capabilities.
- `POST /v1/enrollment/complete` — account bearer token; challenge ID, same public key, and device signature.
- `GET /v1/devices` — account bearer token.
- `GET /v1/commands?limit=50` — account bearer token; latest 1–100 commands, including queued/expired/acknowledged status.
- `GET /v1/audit?limit=100` — account bearer token; latest 1–200 audit events.
- `POST /v1/devices/{deviceId}/revoke` — account bearer token.
- `POST /v1/devices/{deviceId}/location/clear` — account bearer token; clears the last stored coordinates.
- `POST /v1/device/checkin` — device bearer token; heartbeat, with optional coordinates only when the device enrolled with explicit location consent.
- `POST /v1/commands` — account bearer token; target device, allowlisted command, and explicit `userApproved: true` for protected actions.
- `GET /v1/device/commands` — device bearer token; returns unexpired signed commands.
- `POST /v1/device/commands/{commandId}/ack` — device bearer token; `executed`, `failed`, or `rejected`.

All command signatures cover a canonical envelope containing the version, command ID, device ID, command, issue and expiry times, nonce, and requesting account. The device must verify the Ed25519 signature, its own device ID, expiry, replay state, and local capability before execution.

## Integration status

The Android app now includes device-key enrollment, encrypted device credentials, server-signature verification, replay protection, WorkManager polling, lock execution and consented location refresh. The iOS app can enroll itself with a device-bound Ed25519 key but currently advertises no remote-action capabilities. The browser console includes account-scoped command and audit history. The browser console is served at `/console`. End-to-end operation still requires deploying this API with PostgreSQL and server secrets, building/installing the Android app, enrolling a device and testing against a real Android device. A successful cloud APK build and live server deployment have not yet been verified.
