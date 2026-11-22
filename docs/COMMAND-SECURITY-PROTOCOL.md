# AURA command security protocol — implementation requirements

This document is a design contract. It does not claim that the complete protocol is implemented.

## Command lifecycle

1. An authenticated account requests an action for one registered device.
2. The control plane checks account authorization and creates a canonical command payload.
3. The payload is signed by an authorized service key; the command includes a unique ID, target device ID, issued-at time, short expiry, nonce, action and requester.
4. The client authenticates the transport, verifies the signature against a pinned/rotatable trust root, checks the target device, expiry, nonce and policy.
5. The client checks the locally observed OS capability and obtains user approval for sensitive actions.
6. The client executes only a platform adapter's allowlisted operation, records an audit event and returns a signed result.
7. The server records delivery and execution as separate states.

## Required envelope fields

- protocol version
- command ID
- target device ID
- action name and validated parameters
- issued-at and expiry timestamps
- cryptographically random nonce
- requester/account identity
- signing key ID and signature

The current `packages/core` prototype validates a subset of these fields. It does not yet implement signatures, key rotation, parameter schemas, nonce persistence, transport authentication or execution.

## Mandatory controls before remote execution

- Verify signature before policy evaluation.
- Bind credentials and keys to the intended device; do not trust a client-supplied device ID alone.
- Persist consumed nonces and command IDs transactionally to prevent replay across restarts.
- Reject expired commands and commands with excessive lifetime.
- Enforce a strict action and parameter allowlist; never expose a generic shell or arbitrary script command.
- Require explicit approval for location refresh, device lock and other sensitive operations unless a narrowly scoped, user-configured policy explicitly permits them.
- Record requested, delivered, approved, rejected, executed and failed states separately.
- Use TLS and authenticated device sessions; encrypt sensitive local data at rest.
- Support key revocation, account recovery and device removal.
- Do not collect location, audio, images or other evidence without clear user consent and platform permission.

## Offline behaviour

A remote command cannot reach a disconnected device. The server may queue it until reconnection, but the client must reject it if it expires before delivery. The UI must distinguish queued, delivered, approved, executed, rejected and expired states.

## Platform limits

The Android adapter may lock only when the app has the required Device Admin / Device Owner authority and the OS version supports the operation. iOS consumer applications cannot arbitrarily reset a passcode, force a camera capture or override Find My. AURA must report unsupported actions honestly.
