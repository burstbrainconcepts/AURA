CREATE TABLE IF NOT EXISTS aura_accounts (
  id TEXT PRIMARY KEY,
  api_token_sha256 CHAR(64) NOT NULL UNIQUE,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS aura_devices (
  id TEXT PRIMARY KEY,
  account_id TEXT NOT NULL REFERENCES aura_accounts(id) ON DELETE CASCADE,
  public_key_pem TEXT NOT NULL,
  token_sha256 CHAR(64) NOT NULL UNIQUE,
  capabilities JSONB NOT NULL DEFAULT '[]'::jsonb,
  platform TEXT NOT NULL DEFAULT 'android',
  label TEXT NOT NULL DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_seen_at TIMESTAMPTZ,
  revoked_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS aura_devices_account_idx ON aura_devices(account_id);

CREATE TABLE IF NOT EXISTS aura_enrollment_challenges (
  id UUID PRIMARY KEY,
  account_id TEXT NOT NULL REFERENCES aura_accounts(id) ON DELETE CASCADE,
  device_id TEXT NOT NULL,
  public_key_sha256 CHAR(64) NOT NULL,
  challenge TEXT NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  consumed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS aura_enrollment_expiry_idx ON aura_enrollment_challenges(expires_at);

CREATE TABLE IF NOT EXISTS aura_commands (
  id UUID PRIMARY KEY,
  account_id TEXT NOT NULL REFERENCES aura_accounts(id) ON DELETE CASCADE,
  device_id TEXT NOT NULL REFERENCES aura_devices(id) ON DELETE CASCADE,
  command TEXT NOT NULL,
  signed_payload JSONB NOT NULL,
  issued_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  acknowledged_at TIMESTAMPTZ,
  result TEXT,
  result_detail TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS aura_commands_device_pending_idx
  ON aura_commands(device_id, created_at)
  WHERE acknowledged_at IS NULL;

CREATE TABLE IF NOT EXISTS aura_audit_events (
  id BIGSERIAL PRIMARY KEY,
  account_id TEXT NOT NULL REFERENCES aura_accounts(id) ON DELETE CASCADE,
  device_id TEXT,
  event_type TEXT NOT NULL,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
