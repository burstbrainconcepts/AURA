import test from "node:test";
import assert from "node:assert/strict";
import { createPublicKey, generateKeyPairSync, sign, verify } from "node:crypto";
import {
  canonicalCommand,
  createDeviceToken,
  exportCommandPublicKey,
  loadCommandSigner,
  sha256,
  signCommand,
  verifyEnrollmentProof
} from "../src/security.js";

test("device tokens are high entropy and stored as one-way hashes", () => {
  const token = createDeviceToken();
  assert.equal(token.length, 43);
  assert.notEqual(sha256(token), token);
  assert.equal(sha256(token).length, 64);
});

test("enrollment proof verifies against the registered device key", () => {
  const pair = generateKeyPairSync("ed25519");
  const publicKeyPem = pair.publicKey.export({ type: "spki", format: "pem" }).toString();
  const message = "AURA-ENROLL:v1:challenge-id:device-id:expires:nonce";
  const signature = sign(null, Buffer.from(message), pair.privateKey).toString("base64");
  assert.equal(verifyEnrollmentProof({ publicKeyPem, message, signature }), true);
  assert.equal(verifyEnrollmentProof({ publicKeyPem, message: message + "x", signature }), false);
});

test("server signs canonical command envelopes with its own Ed25519 key", () => {
  const pair = generateKeyPairSync("ed25519");
  const privatePem = pair.privateKey.export({ type: "pkcs8", format: "pem" }).toString();
  const privateKey = loadCommandSigner(Buffer.from(privatePem).toString("base64"));
  const publicPem = exportCommandPublicKey(privateKey);
  const envelope = {
    version: 1,
    commandId: "command-123456",
    deviceId: "device-123456",
    command: "recovery.checkin",
    issuedAt: "2026-11-22T12:00:00.000Z",
    expiresAt: "2026-11-22T12:05:00.000Z",
    nonce: "nonce-123456",
    requestedBy: "account-123456"
  };
  const signed = signCommand(envelope, privateKey);
  const valid = verify(
    null,
    Buffer.from(canonicalCommand(envelope)),
    createPublicKey(publicPem),
    Buffer.from(signed.signature, "base64")
  );
  assert.equal(valid, true);
  assert.equal(signed.algorithm, "Ed25519");
});
