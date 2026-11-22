const RULES = Object.freeze({
  "session.revoke": { capability: "device.session.revoke", network: false, approval: false },
  "device.lock": { capability: "device.lock.supported", network: false, approval: true },
  "location.refresh": { capability: "device.location.user_consented", network: false, approval: true },
  "recovery.checkin": { capability: "device.recovery.queue", network: false, approval: false }
});

/**
 * Evaluates local policy only. It does not execute the command or verify signatures.
 * Signature verification, device binding and replay storage must happen before this call.
 */
export function evaluateCommand({ envelope, capabilities, approvals = [], now = Date.now() }) {
  const rule = RULES[envelope?.command];
  if (!rule) return { allowed: false, reason: "command_not_allowed" };
  if (Date.parse(envelope.expiresAt) <= now) return { allowed: false, reason: "command_expired" };
  if (envelope.deviceId !== capabilities?.deviceId) return { allowed: false, reason: "wrong_device" };
  if (!capabilities.granted.includes(rule.capability)) return { allowed: false, reason: "capability_not_granted" };
  if (rule.approval && !approvals.includes(envelope.command)) {
    return { allowed: false, reason: "user_approval_required" };
  }
  return { allowed: true, reason: "policy_satisfied", requiresNetwork: rule.network };
}
