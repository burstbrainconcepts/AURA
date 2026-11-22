const ALLOWED_COMMANDS = new Set([
  "session.revoke",
  "device.lock",
  "location.refresh",
  "recovery.checkin"
]);

const MAX_TTL_MS = 15 * 60 * 1000;
const ID_PATTERN = /^[a-zA-Z0-9._:-]{8,128}$/;

export function createCommandEnvelope(input, now = Date.now()) {
  const { commandId, deviceId, command, issuedAt, expiresAt, nonce, requestedBy } = input ?? {};
  if (![commandId, deviceId, nonce, requestedBy].every((v) => typeof v === "string" && ID_PATTERN.test(v))) {
    throw new TypeError("Invalid command, device, nonce or requester identifier");
  }
  if (!ALLOWED_COMMANDS.has(command)) throw new TypeError("Command is not allowlisted");
  const issued = Date.parse(issuedAt);
  const expires = Date.parse(expiresAt);
  if (!Number.isFinite(issued) || !Number.isFinite(expires)) throw new TypeError("Invalid command timestamps");
  if (issued > now + 30_000) throw new RangeError("Command issue time is in the future");
  if (expires <= now || expires <= issued) throw new RangeError("Command is expired or has an invalid expiry");
  if (expires - issued > MAX_TTL_MS) throw new RangeError("Command lifetime exceeds 15 minutes");
  return Object.freeze({
    version: 1, commandId, deviceId, command, issuedAt: new Date(issued).toISOString(),
    expiresAt: new Date(expires).toISOString(), nonce, requestedBy
  });
}

export function isCommandAllowlisted(command) {
  return ALLOWED_COMMANDS.has(command);
}
