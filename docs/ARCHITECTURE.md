# AURA architecture

## Product boundary

AURA is a cross-platform local-first assistant with a separate device-protection and recovery capability.

### AURA Assistant

- Local and optional remote model inference through replaceable runtime adapters.
- Voice input, speech recognition and speech output with explicit runtime capability reporting.
- User-controlled conversation and long-term memory.
- Offline knowledge retrieval.
- Command planning and permission-aware execution.
- Android, iOS and desktop clients.

### AURA Guard

- Account and device registry.
- Device-bound credentials and passkeys.
- AURA-session revocation and supported device-protection workflows.
- Lost-device state and recovery workflow.
- Signed, expiring remote-command queue.
- Audit trail and user notifications.

## High-level layers

1. **Clients:** native Android, native iOS, and desktop (Tauri).
2. **Shared contracts:** versioned command schema, capability model, event schema and API types.
3. **Local core:** shared Rust/TypeScript logic where useful; native platform adapters remain native.
4. **Intelligence:** pluggable local inference runtime, optional remote model providers, retrieval and tool policy.
5. **Voice:** replaceable STT, wake-word and TTS engines; local/offline modes are explicit capabilities, not assumptions.
6. **Storage:** encrypted local database, explicit retention and export/delete controls.
7. **Optional services:** account/device registry, push relay and encrypted command delivery.

## Capability discovery and platform integration

AURA must not turn a point-in-time platform limitation into a permanent product rule. Each installation discovers capabilities at runtime and reports a scoped state for the current device, OS version, account, app build, region, hardware, permissions, entitlements, agreements and deployment configuration.

Use the shared capability states:

- `available`: the adapter has verified that the capability can be used now.
- `approval_required`: a user, administrator, platform team or partner approval is outstanding.
- `configuration_required`: a setting, permission, service, model or credential must be configured.
- `temporarily_unavailable`: the capability may be supported but cannot currently be used.
- `not_available_in_context`: the current deployment has verified that the capability is not available in this specific context.
- `unknown`: the adapter has not established a reliable result.

A state is not a global statement about an operating system. Include the observation time, device identifier, platform/build context, evidence and next action. Refresh observations after OS updates, permission changes, account changes, entitlement changes and app upgrades. Never silently convert `unknown` or `approval_required` into a permanent denial.

For approval-dependent capabilities, keep the product contract, adapter interface, UI state and test plan in place while the required operational discussions or agreements proceed. At release time, verify the then-current requirements with the relevant platform team and record the actual approvals and signing configuration. Do not assume an approval exists, and do not abandon an integration solely because an approval may be required.

## Security model

- Never treat a voice match as sufficient authorization for sensitive operations.
- Require supported platform authentication, passkeys, device PIN or a recovery factor for high-risk actions.
- Sign remote commands; bind each command to a device, account, nonce, expiry and allowed action.
- Reject replayed, expired, malformed or out-of-scope commands.
- Keep auditable records for security-sensitive transitions.
- Collect location or evidence only with transparent user consent and verified platform permissions.
- Do not implement covert camera capture, hidden tracking or security bypasses.
- Treat a queued remote command as pending until the target device acknowledges and reports its outcome.

## Integration sequence

1. Verify and pin upstream candidates; complete licence and security reviews.
2. Maintain shared capability, command-envelope and event contracts.
3. Build native adapters for local inference, voice, storage and device management.
4. Add encrypted local storage and policy-gated action execution.
5. Deploy AURA Guard with device enrollment, signed commands and audit events.
6. Integrate iOS, Android and desktop clients against the same service contracts.
7. Run threat modelling, permission review, offline tests, recovery drills and device-matrix CI.
8. At operationalization, verify platform requirements, signing, entitlements, agreements and partner approvals for each target configuration.

## Verification standard

A feature is not complete merely because its UI or API exists. Record separately:

- implementation present;
- unit and integration tests passing;
- CI build passing for the target;
- runtime capability observed on a named test configuration;
- end-to-end behavior verified on physical hardware where applicable;
- release approvals, signing and distribution configuration completed.

Do not describe a queued command as executed, a simulator build as a signed device release, or a configured workflow as a passing build.
