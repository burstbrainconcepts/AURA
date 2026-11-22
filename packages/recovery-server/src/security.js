import {
  createHash,
  createPrivateKey,
  createPublicKey,
  randomBytes,
  sign,
  verify
} from "node:crypto";

export function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

export function createDeviceToken() {
  return randomBytes(32).toString("base64url");
}

export function parseEd25519PublicKey(pem) {
  const key = createPublicKey(pem);
  if (key.asymmetricKeyType !== "ed25519") {
    throw new TypeError("Device public key must use Ed25519.");
  }
  return key;
}

export function createEnrollmentMessage({
  challengeId, deviceId, publicKeySha256, capabilities, platform, label, expiresAt, challenge
}) {
  return JSON.stringify({
    version: 1, challengeId, deviceId, publicKeySha256,
    capabilities, platform, label, expiresAt, challenge
  });
}

export function verifyEnrollmentProof({ publicKeyPem, message, signature }) {
  const key = parseEd25519PublicKey(publicKeyPem);
  if (typeof signature !== "string" || signature.length > 512) return false;
  try {
    return verify(null, Buffer.from(message, "utf8"), key, Buffer.from(signature, "base64"));
  } catch {
    return false;
  }
}

export function loadCommandSigner(privateKeyBase64) {
  if (!privateKeyBase64) throw new Error("AURA_COMMAND_PRIVATE_KEY_B64 is required.");
  const key = createPrivateKey(Buffer.from(privateKeyBase64, "base64"));
  if (key.asymmetricKeyType !== "ed25519") {
    throw new TypeError("AURA command signing key must use Ed25519.");
  }
  return key;
}

export function exportCommandPublicKey(privateKey) {
  return createPublicKey(privateKey).export({ type: "spki", format: "pem" }).toString();
}

export function canonicalCommand(envelope) {
  return JSON.stringify({
    version: envelope.version,
    commandId: envelope.commandId,
    deviceId: envelope.deviceId,
    command: envelope.command,
    issuedAt: envelope.issuedAt,
    expiresAt: envelope.expiresAt,
    nonce: envelope.nonce,
    requestedBy: envelope.requestedBy
  });
}

export function signCommand(envelope, privateKey) {
  return Object.freeze({
    envelope,
    signature: sign(null, Buffer.from(canonicalCommand(envelope)), privateKey).toString("base64"),
    algorithm: "Ed25519"
  });
}
