# Security boundaries and platform capability contract

AURA must describe capabilities honestly and degrade safely when an operating system does not expose an action.

| Capability | Android | iOS |
|---|---|---|
| Local AI inference | Available with compatible runtime/device resources | Available with compatible runtime/device resources |
| Offline speech recognition | Available with downloaded models | Available with supported on-device models/runtime |
| Background wake listening | Restricted by OS power/privacy policies and permissions | Highly restricted; do not promise unrestricted always-on listening |
| App-controlled lock | Possible only under supported device-admin/device-owner conditions | No arbitrary third-party device lock; use supported Apple lost-device features |
| Remote recovery command | Requires network connectivity to receive command | Requires network connectivity and supported Apple/app APIs |
| Camera evidence capture | Must be visible, permissioned and policy-compliant | Must follow system camera and background-execution restrictions |
| Location | Requires permission and may be approximate | Requires permission and background-location eligibility |

## Command envelope

Every remote command should contain:

- command ID and unique nonce
- account ID and target device ID
- issued-at and expires-at timestamps
- action identifier and strictly typed parameters
- minimum client version and policy version
- cryptographic signature

The client verifies signature, device binding, expiry, nonce uniqueness, current account state and local policy before execution. High-risk commands require fresh local user authentication or a separately defined recovery authorization.

## Recovery principles

- AURA can revoke its own sessions and protect its own encrypted data.
- AURA cannot replace the operating system's native passcode or bypass device encryption.
- Use Apple Find My / supported Apple lost-device functionality and Google's supported device-finding ecosystem where applicable.
- Queue commands while offline; never display them as delivered until acknowledged by the target device.
