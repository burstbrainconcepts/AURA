# @aura/core

Dependency-free JavaScript contracts for AURA's shared command and capability layer.

Run tests from the repository root with `npm test`.

Current scope: finite command allowlist, expiring command-envelope validation, per-device capability observations, deny-by-default local policy and explicit approval for sensitive actions.

Security boundary: this is not a complete remote command security system. It does not yet implement cryptographic signature verification, hardware-backed device identity, persistent nonce/replay protection, secure transport, encrypted storage or native Android/iOS execution. A production receiver must verify those controls before dispatch. This package does not execute commands.
