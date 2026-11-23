# Security boundaries and platform capability contract

AURA describes product capabilities through a runtime-discovered contract. A platform name alone is not enough to determine whether a capability is available. Availability can depend on the exact OS release, device hardware, app build, account type, region, user permissions, managed-device configuration, entitlements, developer agreements, partner approvals and service deployment.

## Capability state model

| State | Meaning | Product behavior |
|---|---|---|
| `available` | Verified usable in this context | Enable the capability subject to AURA policy and user authorization |
| `approval_required` | A required approval or entitlement is pending | Keep the integration visible and show the approval path; do not attempt the protected operation |
| `configuration_required` | Permission, model, service, credential or setting is missing | Guide the user or administrator through configuration |
| `temporarily_unavailable` | Supported path cannot be used at this moment | Retry safely and preserve user data |
| `not_available_in_context` | Verified unavailable for this specific device/deployment context | Explain the evidence and offer a supported alternative |
| `unknown` | Not yet verified | Do not claim support or permanent impossibility; run discovery |

Capability observations must be scoped and timestamped. Refresh them when the device, OS, app, account, permissions, entitlements or deployment changes. Preserve an approval-dependent integration path while the relevant platform or partner team confirms current requirements. Re-evaluate requirements at release time rather than relying on static assumptions in this document.

## Capability discovery checklist

For each platform adapter, discover and test:

- OS and build version, hardware model and relevant hardware support.
- App permissions and user-consent state.
- Account eligibility, region and managed-device status where relevant.
- Entitlements, signing configuration, provisioning and developer-program agreements.
- Required system services, background execution eligibility and power constraints.
- Local model, voice model, language pack and storage availability.
- Network state, recovery API reachability and device enrollment state.
- Required partner or enterprise approvals and the owner responsible for obtaining them.

Keep the capability interface and user experience ready while approvals are pending. Do not bypass platform security or claim an approval has been granted when it has not.

## Command envelope

Every remote command should contain:

- command ID and unique nonce;
- account ID and target device ID;
- issued-at and expires-at timestamps;
- action identifier and strictly typed parameters;
- minimum client version and policy version;
- cryptographic signature.

The client verifies signature, device binding, expiry, nonce uniqueness, current account state and local policy before execution. High-risk commands require fresh local user authentication or a separately defined recovery authorization.

## Recovery principles

- AURA can revoke its own sessions and protect its own encrypted data.
- AURA must use verified, supported device-management and recovery interfaces for operating-system-level actions.
- AURA must never claim that it can replace an OS passcode, bypass device encryption or override a platform control without an explicitly approved and verified integration.
- Queue commands while offline; never display them as delivered until acknowledged by the target device.
- Location and evidence collection must be transparent, consented, purpose-limited and auditable.
- Do not implement covert camera capture, hidden tracking, remote wipe or security bypasses.

## Release verification

Track implementation, automated tests, CI build, physical-device behavior, platform approvals and distribution signing as separate gates. A successful simulator build is not proof of a signed device release. A queued command is not proof of execution. A configured workflow is not proof that CI passed.
