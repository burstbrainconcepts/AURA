/**
 * Device capability declarations are observations, not permissions.
 * A capability is usable only when the OS grants it and AURA policy allows it.
 */
export const CAPABILITIES = Object.freeze([
  "assistant.chat.local",
  "assistant.inference.local",
  "assistant.voice.listen",
  "assistant.voice.speak",
  "assistant.voice.offline",
  "memory.encrypted",
  "memory.sync",
  "device.session.revoke",
  "device.lock.supported",
  "device.remote_lock",
  "device.location.user_consented",
  "device.location.background",
  "device.recovery.queue",
  "device.management.enrollment",
  "automation.alarm",
  "automation.reminder",
  "automation.background",
  "desktop.system_tray",
  "desktop.startup",
  "desktop.file_access"
]);

export function createCapabilitySnapshot({ deviceId, platform, observedAt, granted = [] }) {
  if (!deviceId || !platform || !Number.isFinite(Date.parse(observedAt))) {
    throw new TypeError("deviceId, platform and a valid observedAt are required");
  }
  const unique = [...new Set(granted)];
  const unknown = unique.filter((item) => !CAPABILITIES.includes(item));
  if (unknown.length) throw new TypeError(`Unknown capabilities: ${unknown.join(", ")}`);
  return Object.freeze({
    deviceId,
    platform,
    observedAt: new Date(observedAt).toISOString(),
    granted: Object.freeze(unique)
  });
}
