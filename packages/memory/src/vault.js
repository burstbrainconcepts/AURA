import { pbkdf2Sync, randomBytes, createCipheriv, createDecipheriv } from "node:crypto";

const ITERATIONS = 310_000;
const KEY_BYTES = 32;
const SALT_BYTES = 16;
const IV_BYTES = 12;
const ALGORITHM = "aes-256-gcm";

function deriveKey(passphrase, salt) {
  if (typeof passphrase !== "string" || passphrase.length < 12) {
    throw new TypeError("Vault passphrase must contain at least 12 characters.");
  }
  return pbkdf2Sync(passphrase, salt, ITERATIONS, KEY_BYTES, "sha256");
}

/** Encrypts a memory export. The passphrase is never stored in the envelope. */
export function encryptMemorySnapshot(snapshot, passphrase) {
  if (!snapshot || snapshot.version !== 1 || !Array.isArray(snapshot.items)) {
    throw new TypeError("Invalid memory snapshot.");
  }
  const salt = randomBytes(SALT_BYTES);
  const iv = randomBytes(IV_BYTES);
  const cipher = createCipheriv(ALGORITHM, deriveKey(passphrase, salt), iv);
  const plaintext = Buffer.from(JSON.stringify(snapshot), "utf8");
  const ciphertext = Buffer.concat([cipher.update(plaintext), cipher.final()]);
  const tag = cipher.getAuthTag();
  return {
    format: "aura-memory-vault",
    version: 1,
    kdf: "pbkdf2-sha256",
    iterations: ITERATIONS,
    cipher: ALGORITHM,
    salt: salt.toString("base64"),
    iv: iv.toString("base64"),
    tag: tag.toString("base64"),
    data: ciphertext.toString("base64")
  };
}

export function decryptMemorySnapshot(envelope, passphrase) {
  if (!envelope || envelope.format !== "aura-memory-vault" || envelope.version !== 1 ||
      envelope.kdf !== "pbkdf2-sha256" || envelope.cipher !== ALGORITHM ||
      envelope.iterations !== ITERATIONS) {
    throw new TypeError("Unsupported or invalid memory vault.");
  }
  try {
    const decipher = createDecipheriv(
      ALGORITHM,
      deriveKey(passphrase, Buffer.from(envelope.salt, "base64")),
      Buffer.from(envelope.iv, "base64")
    );
    decipher.setAuthTag(Buffer.from(envelope.tag, "base64"));
    const plaintext = Buffer.concat([
      decipher.update(Buffer.from(envelope.data, "base64")),
      decipher.final()
    ]);
    const snapshot = JSON.parse(plaintext.toString("utf8"));
    if (!snapshot || snapshot.version !== 1 || !Array.isArray(snapshot.items)) {
      throw new Error("Invalid snapshot payload.");
    }
    return snapshot;
  } catch {
    throw new Error("Unable to decrypt memory vault. Check the passphrase or file integrity.");
  }
}
