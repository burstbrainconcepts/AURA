import test from "node:test";
import assert from "node:assert/strict";
import { createCapabilitySnapshot, createCommandEnvelope, evaluateCommand } from "../src/index.js";

const now = Date.parse("2026-11-22T12:00:00.000Z");
const envelopeInput = {
  commandId: "cmd-00000001", deviceId: "device-0001", command: "device.lock",
  issuedAt: "2026-11-22T11:59:00.000Z", expiresAt: "2026-11-22T12:05:00.000Z",
  nonce: "nonce-000001", requestedBy: "account-0001"
};

test("capability snapshot validates and freezes grants", () => {
  const snapshot = createCapabilitySnapshot({
    deviceId: "device-0001", platform: "android", observedAt: "2026-11-22T12:00:00Z",
    granted: ["device.lock.supported"]
  });
  assert.deepEqual(snapshot.granted, ["device.lock.supported"]);
  assert.throws(() => createCapabilitySnapshot({
    deviceId: "d", platform: "android", observedAt: "2026-11-22T12:00:00Z",
    granted: ["camera.hidden"]
  }), /Unknown capabilities/);
});

test("command envelope rejects unknown commands and excessive lifetime", () => {
  assert.throws(() => createCommandEnvelope({...envelopeInput, command:"shell.exec"}, now), /allowlisted/);
  assert.throws(() => createCommandEnvelope({...envelopeInput, expiresAt:"2026-11-22T13:00:00Z"}, now), /15 minutes/);
});

test("device lock requires capability and explicit approval", () => {
  const envelope = createCommandEnvelope(envelopeInput, now);
  const capabilities = createCapabilitySnapshot({
    deviceId:"device-0001", platform:"android", observedAt:"2026-11-22T12:00:00Z",
    granted:["device.lock.supported"]
  });
  assert.equal(evaluateCommand({envelope, capabilities, now}).reason, "user_approval_required");
  assert.equal(evaluateCommand({envelope, capabilities, approvals:["device.lock"], now}).allowed, true);
});

test("commands cannot target a different device", () => {
  const envelope = createCommandEnvelope(envelopeInput, now);
  const capabilities = createCapabilitySnapshot({
    deviceId:"device-0002", platform:"android", observedAt:"2026-11-22T12:00:00Z",
    granted:["device.lock.supported"]
  });
  assert.equal(evaluateCommand({envelope, capabilities, approvals:["device.lock"], now}).reason, "wrong_device");
});
