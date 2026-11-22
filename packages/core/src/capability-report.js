/**
 * Capability identifiers describe product features, not assumptions about a platform.
 * Native adapters probe the current device/account/deployment and report effective state.
 * Approval-dependent capabilities remain in the registry.
 */
export const CAPABILITIES = Object.freeze([
  "assistant.chat.local", "assistant.inference.local", "assistant.voice.listen",
  "assistant.voice.speak", "assistant.voice.offline", "memory.encrypted", "memory.sync",
  "device.session.revoke", "device.lock.supported", "device.remote_lock",
  "device.location.user_consented", "device.location.background", "device.recovery.queue",
  "device.management.enrollment", "automation.alarm", "automation.reminder",
  "automation.background", "desktop.system_tray", "desktop.startup", "desktop.file_access"
]);
export const CAPABILITY_STATES = Object.freeze(["available", "approval_required", "configuration_required", "temporarily_unavailable", "not_available_in_context", "unknown"]);

export function createCapabilitySnapshot({ deviceId, platform, observedAt, granted = [] }) {
  if (!deviceId || !platform || !Number.isFinite(Date.parse(observedAt))) throw new TypeError("deviceId, platform and a valid observedAt are required");
  const unique = [...new Set(granted)];
  const unknown = unique.filter((item) => !CAPABILITIES.includes(item));
  if (unknown.length) throw new TypeError(`Unknown capabilities: ${unknown.join(", ")}`);
  return Object.freeze({ deviceId, platform, observedAt: new Date(observedAt).toISOString(), granted: Object.freeze(unique) });
}

/** Approval-required remains an actionable integration state, not a permanent denial. */
export function createCapabilityReport({ deviceId, platform, observedAt, capabilities = [] }) {
  if (!deviceId || !platform || !Number.isFinite(Date.parse(observedAt))) throw new TypeError("deviceId, platform and a valid observedAt are required");
  if (!Array.isArray(capabilities)) throw new TypeError("capabilities must be an array");
  const seen = new Set();
  const entries = capabilities.map((entry) => {
    if (!entry || typeof entry !== "object") throw new TypeError("Each capability entry must be an object");
    const { id, state, reason = "", requirements = [], source = "" } = entry;
    if (!CAPABILITIES.includes(id)) throw new TypeError(`Unknown capability: ${id}`);
    if (seen.has(id)) throw new TypeError(`Duplicate capability: ${id}`);
    if (!CAPABILITY_STATES.includes(state)) throw new TypeError(`Invalid capability state: ${state}`);
    if (typeof reason !== "string" || typeof source !== "string" || !Array.isArray(requirements) || requirements.some((v) => typeof v !== "string")) throw new TypeError("Invalid capability metadata");
    seen.add(id);
    return Object.freeze({ id, state, reason, requirements: Object.freeze([...requirements]), source });
  });
  return Object.freeze({ deviceId, platform, observedAt: new Date(observedAt).toISOString(), capabilities: Object.freeze(entries) });
}

export function getCapabilityState(report, capabilityId) {
  return report?.capabilities?.find((entry) => entry.id === capabilityId)?.state ?? "unknown";
}
export function isCapabilityAvailable(report, capabilityId) {
  return getCapabilityState(report, capabilityId) === "available";
}