# AURA architecture (initial)

## Product boundary

AURA is a cross-platform local-first assistant with a separate device-protection and recovery capability.

### AURA Assistant

- Voice input, speech recognition and speech output
- Local model runtime with optional cloud providers
- Conversation and user-controlled long-term memory
- Offline knowledge retrieval
- Command planning and permission-aware execution
- Android, iOS and desktop clients

### AURA Guard

- Account and device registry
- Device-bound credentials and passkeys
- Session revocation for AURA services
- Lost-device state and recovery workflow
- Signed, expiring remote-command queue
- Audit trail and user notifications

## Proposed high-level layers

1. **Clients:** native Android (Kotlin), native iOS (Swift), desktop (Tauri + web UI).
2. **Shared contracts:** versioned command schema, capability model, event schema and API types.
3. **Local core:** Rust where shared low-level logic is useful; platform-specific adapters remain native.
4. **Intelligence:** pluggable local inference runtime, optional remote model providers, retrieval and tool policy.
5. **Voice:** replaceable STT, wake-word and TTS engines.
6. **Storage:** encrypted local database, explicit retention and export/delete controls.
7. **Optional services:** account/device registry, push relay and encrypted command delivery.

## Security model

- Never treat a voice match as sufficient authorization for sensitive operations.
- Require platform biometrics, passkeys, device PIN or a recovery factor for high-risk actions.
- Sign remote commands; bind each command to a device, account, nonce, expiry and allowed action.
- Reject replayed, expired, malformed or out-of-scope commands.
- Keep immutable audit events for security-sensitive transitions.
- Collect location or evidence only with transparent user consent and platform permissions.
- Do not implement covert camera capture, hidden tracking or bypasses of OS protections.

## Platform constraints

- Android capabilities depend on runtime permissions, special access and (for some device-management actions) device-owner provisioning.
- iOS third-party apps cannot arbitrarily reset a device passcode, silently enable a camera or override system Find My controls. Integrate with supported Apple capabilities instead.
- A remote command cannot reach an offline device. Queue it securely and process it after authenticated reconnection.
- Cellular calls and SMS still require carrier service or an available network path.
- Offline maps and AI models must be downloaded in advance.

## Initial implementation sequence

1. Verify and pin upstream candidates; complete licence and security reviews.
2. Define capability registry, command envelope and local event model.
3. Build Android proof of concept around the best permitted assistant and voice components.
4. Add encrypted local storage and policy-gated action execution.
5. Build AURA Guard account/device registration and signed command queue.
6. Add iOS and desktop clients using platform-native security APIs.
7. Run threat modelling, permission review, offline tests and recovery drills.
