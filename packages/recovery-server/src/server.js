import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { randomUUID, randomBytes } from "node:crypto";
import { Pool } from "pg";
import { createCommandEnvelope, CAPABILITIES, isCommandAllowlisted } from "@aura/core";
import {
  createDeviceToken,
  createEnrollmentMessage,
  exportCommandPublicKey,
  loadCommandSigner,
  sha256,
  signCommand,
  verifyEnrollmentProof
} from "./security.js";

const PORT = Number(process.env.PORT || 8787);
const CONSOLE_DIR = join(dirname(fileURLToPath(import.meta.url)), "..", "..", "..", "recovery-console");
const CONSOLE_FILES = Object.freeze({
  "/console": ["index.html", "text/html; charset=utf-8"],
  "/console/": ["index.html", "text/html; charset=utf-8"],
  "/console/app.js": ["app.js", "text/javascript; charset=utf-8"],
  "/console/styles.css": ["styles.css", "text/css; charset=utf-8"]
});
const DATABASE_URL = process.env.DATABASE_URL;
const ACCOUNT_ID = process.env.AURA_BOOTSTRAP_ACCOUNT_ID;
const ACCOUNT_TOKEN = process.env.AURA_BOOTSTRAP_ACCOUNT_TOKEN;
const COMMAND_PRIVATE_KEY_B64 = process.env.AURA_COMMAND_PRIVATE_KEY_B64;
const ID_PATTERN = /^[a-zA-Z0-9._:-]{8,128}$/;
const REQUIRED_CAPABILITY = Object.freeze({
  "session.revoke": "device.session.revoke",
  "device.lock": "device.lock.supported",
  "location.refresh": "device.location.user_consented",
  "recovery.checkin": "device.recovery.queue"
});
const APPROVAL_REQUIRED = new Set(["device.lock", "location.refresh"]);

if (!DATABASE_URL) throw new Error("DATABASE_URL is required.");
const pool = new Pool({ connectionString: DATABASE_URL, max: 10, idleTimeoutMillis: 30000 });
const commandPrivateKey = loadCommandSigner(COMMAND_PRIVATE_KEY_B64);
const commandPublicKeyPem = exportCommandPublicKey(commandPrivateKey);

async function bootstrapAccount() {
  if (!ACCOUNT_ID && !ACCOUNT_TOKEN) return;
  if (!ACCOUNT_ID || !ACCOUNT_TOKEN || !ID_PATTERN.test(ACCOUNT_ID) || ACCOUNT_TOKEN.length < 32) {
    throw new Error("Set both AURA_BOOTSTRAP_ACCOUNT_ID and a 32+ character AURA_BOOTSTRAP_ACCOUNT_TOKEN.");
  }
  await pool.query(
    `INSERT INTO aura_accounts (id, api_token_sha256) VALUES ($1, $2)
     ON CONFLICT (id) DO UPDATE SET api_token_sha256 = EXCLUDED.api_token_sha256, active = TRUE`,
    [ACCOUNT_ID, sha256(ACCOUNT_TOKEN)]
  );
}

function send(res, status, payload) {
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    "x-content-type-options": "nosniff"
  });
  res.end(JSON.stringify(payload));
}

async function readJson(req) {
  let raw = "";
  for await (const chunk of req) {
    raw += chunk;
    if (raw.length > 64 * 1024) throw Object.assign(new Error("Request body is too large."), { status: 413 });
  }
  if (!raw) return {};
  try { return JSON.parse(raw); }
  catch { throw Object.assign(new Error("Request body must be valid JSON."), { status: 400 }); }
}

function bearer(req) {
  const value = req.headers.authorization || "";
  if (!value.startsWith("Bearer ")) return "";
  return value.slice(7).trim();
}

async function accountFor(req) {
  const token = bearer(req);
  if (!token || token.length < 32) return null;
  const result = await pool.query(
    "SELECT id FROM aura_accounts WHERE api_token_sha256 = $1 AND active = TRUE LIMIT 1",
    [sha256(token)]
  );
  return result.rows[0] || null;
}

async function deviceFor(req) {
  const token = bearer(req);
  if (!token || token.length < 32) return null;
  const result = await pool.query(
    "SELECT id, account_id, capabilities FROM aura_devices WHERE token_sha256 = $1 AND revoked_at IS NULL LIMIT 1",
    [sha256(token)]
  );
  return result.rows[0] || null;
}

async function audit(accountId, deviceId, eventType, metadata = {}) {
  await pool.query(
    "INSERT INTO aura_audit_events (account_id, device_id, event_type, metadata) VALUES ($1, $2, $3, $4::jsonb)",
    [accountId, deviceId || null, eventType, JSON.stringify(metadata)]
  );
}

async function route(req, res) {
  const url = new URL(req.url, "http://aura.internal");
  const path = url.pathname;

  if (req.method === "GET" && CONSOLE_FILES[path]) {
    const [fileName, contentType] = CONSOLE_FILES[path];
    const content = await readFile(join(CONSOLE_DIR, fileName));
    res.writeHead(200, {
      "content-type": contentType,
      "cache-control": "no-store",
      "x-content-type-options": "nosniff",
      "referrer-policy": "no-referrer",
      "content-security-policy": "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'"
    });
    res.end(content);
    return;
  }

  if (req.method === "GET" && path === "/health") {
    await pool.query("SELECT 1");
    return send(res, 200, { ok: true, service: "aura-recovery", version: 1 });
  }

  if (req.method === "GET" && path === "/v1/server-key") {
    return send(res, 200, { algorithm: "Ed25519", publicKeyPem: commandPublicKeyPem });
  }

  if (req.method === "POST" && path === "/v1/enrollment/challenges") {
    const account = await accountFor(req);
    if (!account) return send(res, 401, { error: "account_auth_required" });
    const body = await readJson(req);
    if (typeof body.deviceId !== "string" || !ID_PATTERN.test(body.deviceId)) {
      return send(res, 400, { error: "invalid_device_id" });
    }
    if (!["android", "ios", "desktop"].includes(body.platform)) {
      return send(res, 400, { error: "unsupported_platform" });
    }
    if (typeof body.publicKeyPem !== "string" || body.publicKeyPem.length > 4096) {
      return send(res, 400, { error: "invalid_public_key" });
    }
    if (!Array.isArray(body.capabilities) || body.capabilities.length > CAPABILITIES.length ||
        body.capabilities.some(capability => !CAPABILITIES.includes(capability))) {
      return send(res, 400, { error: "invalid_capabilities" });
    }
    try {
      const { createPublicKey } = await import("node:crypto");
      const publicKey = createPublicKey(body.publicKeyPem);
      if (publicKey.asymmetricKeyType !== "ed25519") return send(res, 400, { error: "ed25519_required" });
    } catch {
      return send(res, 400, { error: "invalid_public_key" });
    }
    const id = randomUUID();
    const challenge = randomBytes(32).toString("base64url");
    const expiresAt = new Date(Date.now() + 5 * 60 * 1000);
    await pool.query(
      `INSERT INTO aura_enrollment_challenges
       (id, account_id, device_id, public_key_sha256, challenge, expires_at, requested_capabilities, platform, label)
       VALUES ($1, $2, $3, $4, $5, $6, $7::jsonb, $8, $9)`,
      [id, account.id, body.deviceId, sha256(body.publicKeyPem), challenge, expiresAt,
       JSON.stringify(body.capabilities), body.platform, String(body.label || "").slice(0, 80)]
    );
    const message = createEnrollmentMessage({
      challengeId: id, deviceId: body.deviceId, publicKeySha256: sha256(body.publicKeyPem),
      capabilities: body.capabilities, platform: body.platform,
      label: String(body.label || "").slice(0, 80), expiresAt: expiresAt.toISOString(), challenge
    });
    return send(res, 201, { challengeId: id, message, expiresAt: expiresAt.toISOString() });
  }

  if (req.method === "POST" && path === "/v1/enrollment/complete") {
    const account = await accountFor(req);
    if (!account) return send(res, 401, { error: "account_auth_required" });
    const body = await readJson(req);
    if (typeof body.challengeId !== "string" || typeof body.publicKeyPem !== "string") {
      return send(res, 400, { error: "invalid_enrollment_payload" });
    }
    const client = await pool.connect();
    try {
      await client.query("BEGIN");
      const found = await client.query(
        "SELECT * FROM aura_enrollment_challenges WHERE id = $1 AND account_id = $2 FOR UPDATE",
        [body.challengeId, account.id]
      );
      const challenge = found.rows[0];
      if (!challenge || challenge.consumed_at || new Date(challenge.expires_at).getTime() <= Date.now()) {
        await client.query("ROLLBACK");
        return send(res, 410, { error: "challenge_expired_or_consumed" });
      }
      if (sha256(body.publicKeyPem) !== challenge.public_key_sha256) {
        await client.query("ROLLBACK");
        return send(res, 400, { error: "public_key_changed" });
      }
      const message = createEnrollmentMessage({
        challengeId: challenge.id, deviceId: challenge.device_id,
        publicKeySha256: challenge.public_key_sha256,
        capabilities: challenge.requested_capabilities, platform: challenge.platform,
        label: challenge.label, expiresAt: new Date(challenge.expires_at).toISOString(),
        challenge: challenge.challenge
      });
      if (!verifyEnrollmentProof({ publicKeyPem: body.publicKeyPem, message, signature: body.signature })) {
        await client.query("ROLLBACK");
        return send(res, 401, { error: "enrollment_proof_invalid" });
      }
      const token = createDeviceToken();
      const capabilities = challenge.requested_capabilities;
      if (!Array.isArray(capabilities) || capabilities.some(value => !CAPABILITIES.includes(value))) {
        await client.query("ROLLBACK");
        return send(res, 400, { error: "enrollment_capabilities_invalid" });
      }
      const original = await client.query(
        "SELECT capabilities FROM aura_devices WHERE id = $1 AND account_id = $2",
        [challenge.device_id, account.id]
      );
      if (original.rows[0]) {
        await client.query(
          `UPDATE aura_devices SET public_key_pem = $1, token_sha256 = $2, capabilities = $3::jsonb,
           platform = $4, label = $5, revoked_at = NULL, last_seen_at = NOW()
           WHERE id = $6 AND account_id = $7`,
          [body.publicKeyPem, sha256(token), JSON.stringify(capabilities), challenge.platform,
           challenge.label, challenge.device_id, account.id]
        );
      } else {
        await client.query(
          `INSERT INTO aura_devices (id, account_id, public_key_pem, token_sha256, capabilities, platform, label, last_seen_at)
           VALUES ($1, $2, $3, $4, $5::jsonb, $6, $7, NOW())`,
          [challenge.device_id, account.id, body.publicKeyPem, sha256(token), JSON.stringify(capabilities),
           challenge.platform, challenge.label]
        );
      }
      await client.query("UPDATE aura_enrollment_challenges SET consumed_at = NOW() WHERE id = $1", [challenge.id]);
      await client.query(
        "INSERT INTO aura_audit_events (account_id, device_id, event_type, metadata) VALUES ($1, $2, $3, $4::jsonb)",
        [account.id, challenge.device_id, "device.enrolled", JSON.stringify({ platform: challenge.platform, capabilities })]
      );
      await client.query("COMMIT");
      return send(res, 201, {
        deviceId: challenge.device_id,
        deviceToken: token,
        commandServerPublicKeyPem: commandPublicKeyPem,
        algorithm: "Ed25519"
      });
    } catch (error) {
      await client.query("ROLLBACK").catch(() => {});
      throw error;
    } finally {
      client.release();
    }
  }

  if (req.method === "GET" && path === "/v1/commands") {
    const account = await accountFor(req);
    if (!account) return send(res, 401, { error: "account_auth_required" });
    const requestedLimit = Number(url.searchParams.get("limit") || 50);
    const limit = Number.isFinite(requestedLimit) ? Math.min(100, Math.max(1, Math.floor(requestedLimit))) : 50;
    const result = await pool.query(
      `SELECT c.id, c.device_id, d.label AS device_label, d.platform,
       c.command, c.issued_at, c.expires_at, c.acknowledged_at, c.result, c.result_detail
       FROM aura_commands c
       JOIN aura_devices d ON d.id = c.device_id
       WHERE c.account_id = $1
       ORDER BY c.created_at DESC LIMIT $2`,
      [account.id, limit]
    );
    return send(res, 200, { commands: result.rows });
  }

  if (req.method === "GET" && path === "/v1/audit") {
    const account = await accountFor(req);
    if (!account) return send(res, 401, { error: "account_auth_required" });
    const requestedLimit = Number(url.searchParams.get("limit") || 100);
    const limit = Number.isFinite(requestedLimit) ? Math.min(200, Math.max(1, Math.floor(requestedLimit))) : 100;
    const result = await pool.query(
      `SELECT id, device_id, event_type, metadata, created_at
       FROM aura_audit_events
       WHERE account_id = $1
       ORDER BY created_at DESC LIMIT $2`,
      [account.id, limit]
    );
    return send(res, 200, { events: result.rows });
  }

  if (req.method === "GET" && path === "/v1/devices") {
    const account = await accountFor(req);
    if (!account) return send(res, 401, { error: "account_auth_required" });
    const result = await pool.query(
      `SELECT id, platform, label, capabilities, created_at, last_seen_at, revoked_at,
       latitude, longitude, location_accuracy_m, location_updated_at
       FROM aura_devices WHERE account_id = $1 ORDER BY created_at DESC`,
      [account.id]
    );
    return send(res, 200, { devices: result.rows });
  }

  const revokeMatch = path.match(/^\/v1\/devices\/([a-zA-Z0-9._:-]{8,128})\/revoke$/);
  if (req.method === "POST" && revokeMatch) {
    const account = await accountFor(req);
    if (!account) return send(res, 401, { error: "account_auth_required" });
    const result = await pool.query(
      "UPDATE aura_devices SET revoked_at = NOW() WHERE id = $1 AND account_id = $2 AND revoked_at IS NULL",
      [revokeMatch[1], account.id]
    );
    if (!result.rowCount) return send(res, 404, { error: "device_not_found_or_already_revoked" });
    await audit(account.id, revokeMatch[1], "device.revoked");
    return send(res, 200, { revoked: true, deviceId: revokeMatch[1] });
  }


  const clearLocationMatch = path.match(/^\/v1\/devices\/([a-zA-Z0-9._:-]{8,128})\/location\/clear$/);
  if (req.method === "POST" && clearLocationMatch) {
    const account = await accountFor(req);
    if (!account) return send(res, 401, { error: "account_auth_required" });
    const result = await pool.query(
      `UPDATE aura_devices SET latitude = NULL, longitude = NULL, location_accuracy_m = NULL,
       location_updated_at = NULL WHERE id = $1 AND account_id = $2 RETURNING id`,
      [clearLocationMatch[1], account.id]
    );
    if (!result.rowCount) return send(res, 404, { error: "device_not_found" });
    await audit(account.id, clearLocationMatch[1], "device.location.cleared");
    return send(res, 200, { cleared: true, deviceId: clearLocationMatch[1] });
  }

  if (req.method === "POST" && path === "/v1/device/checkin") {
    const device = await deviceFor(req);
    if (!device) return send(res, 401, { error: "device_auth_required" });
    const body = await readJson(req);
    if (body.location == null) {
      await pool.query("UPDATE aura_devices SET last_seen_at = NOW() WHERE id = $1", [device.id]);
      return send(res, 200, { checkedIn: true, locationUpdated: false });
    }
    if (!Array.isArray(device.capabilities) || !device.capabilities.includes("device.location.user_consented")) {
      return send(res, 403, { error: "location_consent_capability_missing" });
    }
    const location = body.location;
    const latitude = Number(location.latitude);
    const longitude = Number(location.longitude);
    const accuracy = Number(location.accuracyM);
    const capturedAt = Date.parse(location.capturedAt);
    const now = Date.now();
    if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90 ||
        !Number.isFinite(longitude) || longitude < -180 || longitude > 180 ||
        !Number.isFinite(accuracy) || accuracy < 0 || accuracy > 100000 ||
        !Number.isFinite(capturedAt) || capturedAt > now + 60_000 || capturedAt < now - 15 * 60_000) {
      return send(res, 400, { error: "invalid_or_stale_location" });
    }
    await pool.query(
      `UPDATE aura_devices SET last_seen_at = NOW(), latitude = $1, longitude = $2,
       location_accuracy_m = $3, location_updated_at = $4 WHERE id = $5`,
      [latitude, longitude, accuracy, new Date(capturedAt), device.id]
    );
    await audit(device.account_id, device.id, "device.location.updated", {
      accuracyM: Math.round(accuracy), capturedAt: new Date(capturedAt).toISOString()
    });
    return send(res, 200, { checkedIn: true, locationUpdated: true });
  }

  if (req.method === "POST" && path === "/v1/commands") {
    const account = await accountFor(req);
    if (!account) return send(res, 401, { error: "account_auth_required" });
    const body = await readJson(req);
    if (typeof body.deviceId !== "string" || !isCommandAllowlisted(body.command)) {
      return send(res, 400, { error: "invalid_device_or_command" });
    }
    const result = await pool.query(
      "SELECT capabilities, revoked_at FROM aura_devices WHERE id = $1 AND account_id = $2",
      [body.deviceId, account.id]
    );
    const device = result.rows[0];
    if (!device || device.revoked_at) return send(res, 404, { error: "device_not_available" });
    const required = REQUIRED_CAPABILITY[body.command];
    if (!required || !device.capabilities.includes(required)) {
      return send(res, 409, { error: "device_capability_missing", requiredCapability: required });
    }
    if (APPROVAL_REQUIRED.has(body.command) && body.userApproved !== true) {
      return send(res, 403, { error: "explicit_user_approval_required" });
    }
    const now = Date.now();
    const ttlMs = Math.min(15 * 60 * 1000, Math.max(30_000, Number(body.ttlMs) || 5 * 60 * 1000));
    const envelope = createCommandEnvelope({
      commandId: randomUUID(),
      deviceId: body.deviceId,
      command: body.command,
      issuedAt: new Date(now).toISOString(),
      expiresAt: new Date(now + ttlMs).toISOString(),
      nonce: randomBytes(24).toString("base64url"),
      requestedBy: account.id
    }, now);
    const signedPayload = signCommand(envelope, commandPrivateKey);
    const client = await pool.connect();
    try {
      await client.query("BEGIN");
      await client.query(
        `INSERT INTO aura_commands (id, account_id, device_id, command, signed_payload, issued_at, expires_at)
         VALUES ($1, $2, $3, $4, $5::jsonb, $6, $7)`,
        [envelope.commandId, account.id, body.deviceId, body.command, JSON.stringify(signedPayload),
         envelope.issuedAt, envelope.expiresAt]
      );
      await client.query(
        "INSERT INTO aura_audit_events (account_id, device_id, event_type, metadata) VALUES ($1, $2, $3, $4::jsonb)",
        [account.id, body.deviceId, "command.issued", JSON.stringify({ command: body.command, commandId: envelope.commandId })]
      );
      await client.query("COMMIT");
    } catch (error) {
      await client.query("ROLLBACK").catch(() => {});
      throw error;
    } finally {
      client.release();
    }
    return send(res, 201, { commandId: envelope.commandId, expiresAt: envelope.expiresAt, queued: true });
  }

  if (req.method === "GET" && path === "/v1/device/commands") {
    const device = await deviceFor(req);
    if (!device) return send(res, 401, { error: "device_auth_required" });
    await pool.query("UPDATE aura_devices SET last_seen_at = NOW() WHERE id = $1", [device.id]);
    const result = await pool.query(
      `SELECT id, signed_payload, expires_at FROM aura_commands
       WHERE device_id = $1 AND acknowledged_at IS NULL AND expires_at > NOW()
       ORDER BY created_at ASC LIMIT 20`,
      [device.id]
    );
    return send(res, 200, { commands: result.rows.map(row => ({
      commandId: row.id, signedCommand: row.signed_payload, expiresAt: row.expires_at
    })) });
  }

  const ackMatch = path.match(/^\/v1\/device\/commands\/([0-9a-f-]{36})\/ack$/i);
  if (req.method === "POST" && ackMatch) {
    const device = await deviceFor(req);
    if (!device) return send(res, 401, { error: "device_auth_required" });
    const body = await readJson(req);
    if (!["executed", "failed", "rejected"].includes(body.result)) {
      return send(res, 400, { error: "invalid_command_result" });
    }
    const detail = typeof body.detail === "string" ? body.detail.slice(0, 1000) : "";
    const updated = await pool.query(
      `UPDATE aura_commands SET acknowledged_at = NOW(), result = $1, result_detail = $2
       WHERE id = $3 AND device_id = $4 AND acknowledged_at IS NULL RETURNING account_id`,
      [body.result, detail, ackMatch[1], device.id]
    );
    if (!updated.rowCount) return send(res, 404, { error: "pending_command_not_found" });
    await audit(updated.rows[0].account_id, device.id, "command.acknowledged", {
      commandId: ackMatch[1], result: body.result
    });
    return send(res, 200, { acknowledged: true });
  }

  return send(res, 404, { error: "not_found" });
}

const server = createServer(async (req, res) => {
  try {
    await route(req, res);
  } catch (error) {
    const status = Number.isInteger(error.status) ? error.status : 500;
    if (status >= 500) process.stderr.write(`AURA recovery error: ${error.stack || error.message}\n`);
    if (!res.headersSent) send(res, status, { error: status >= 500 ? "internal_error" : error.message });
    else res.destroy();
  }
});

await bootstrapAccount();
server.listen(PORT, "0.0.0.0", () => {
  process.stdout.write(`AURA recovery API listening on :${PORT}\n`);
});

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => server.close(() => pool.end().finally(() => process.exit(0))));
}
