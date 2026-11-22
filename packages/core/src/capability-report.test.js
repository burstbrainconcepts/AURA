import test from "node:test";
import assert from "node:assert/strict";
import { createCapabilityReport, getCapabilityState, isCapabilityAvailable } from "./capability-report.js";
const base = { deviceId: "device-1", platform: "ios", observedAt: "2026-11-22T12:00:00.000Z" };
test("approval-dependent capabilities remain actionable", () => {
  const report = createCapabilityReport({ ...base, capabilities: [{ id: "device.remote_lock", state: "approval_required", requirements: ["Verify current platform entitlement", "Obtain applicable approval"] }] });
  assert.equal(getCapabilityState(report, "device.remote_lock"), "approval_required");
  assert.equal(isCapabilityAvailable(report, "device.remote_lock"), false);
  assert.equal(report.capabilities[0].requirements.length, 2);
});
test("capability reports distinguish available, unavailable-in-context and unknown", () => {
  const report = createCapabilityReport({ ...base, capabilities: [{ id: "assistant.inference.local", state: "available" }, { id: "device.location.background", state: "not_available_in_context" }] });
  assert.equal(isCapabilityAvailable(report, "assistant.inference.local"), true);
  assert.equal(getCapabilityState(report, "device.location.background"), "not_available_in_context");
  assert.equal(getCapabilityState(report, "memory.encrypted"), "unknown");
});
test("capability reports reject duplicate and unknown identifiers", () => {
  assert.throws(() => createCapabilityReport({ ...base, capabilities: [{ id: "device.remote_lock", state: "available" }, { id: "device.remote_lock", state: "unknown" }] }), /Duplicate capability/);
  assert.throws(() => createCapabilityReport({ ...base, capabilities: [{ id: "device.magic", state: "available" }] }), /Unknown capability/);
});