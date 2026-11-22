# @aura/core

Dependency-free JavaScript contracts for AURA's shared command and capability layer.

Run tests from the repository root with `npm test`.

Current scope: finite command allowlist, expiring command-envelope validation, per-device capability observations, deny-by-default local policy and explicit approval for sensitive actions.

Security boundary: this is not a complete remote command security system. It does not yet implement cryptographic signature verification, hardware-backed device identity, persistent nonce/replay protection, secure transport, encrypted storage or native Android/iOS execution. A production receiver must verify those controls before dispatch. This package does not execute commands.

## Runtime capability discovery

`createCapabilityReport()` records each capability as one of:

- `available`
- `approval_required`
- `configuration_required`
- `temporarily_unavailable`
- `not_available_in_context`
- `unknown`

An approval-dependent capability remains in the product contract while its entitlement, agreement, managed deployment or platform approval is pursued. A snapshot of currently granted capabilities is an observation for a particular device and time; it is not a permanent platform-wide verdict.
