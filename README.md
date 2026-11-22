# AURA

**AURA** is a cross-platform, offline-first personal AI assistant and device-protection platform.

- **AURA Assistant** — local-first intelligence, voice interaction, memory, user-approved automation and offline knowledge.
- **AURA Guard** — device registration, recovery credentials, session protection, lost-device workflows and authenticated remote-command queuing.

## Repository structure

- `packages/core/` — AURA-owned shared command and capability contracts.
- `packages/runtime/` — local inference runtime adapters.
- `packages/assistant/` — assistant orchestration and sessions.
- `packages/memory/` — opt-in memory contracts and storage.
- `packages/guard/` — signed device command security.
- `packages/recovery-server/` — device enrollment, command queue, acknowledgements and audit API.
- `recovery-console/` — browser-based device management console.
- `android/` — native Android application and llama.cpp JNI integration.
- `desktop/` — Tauri desktop application for Windows, macOS and Linux.
- `ios/` — native SwiftUI application project for iPhone and iPad.
- `upstream/engines/` — approved third-party inference and voice engines, registered as pinned Git submodules.
- `integrations/` — platform adapters around approved upstream components.
- `audits/` — technical, licence and security reviews.
- `docs/` — architecture and security decisions.

## First principles

1. Offline operation is a first-class design requirement.
2. Models, maps and voice assets are downloaded separately; never commit large model weights or private datasets.
3. Remote commands must be signed, device-bound, expiring, replay-resistant and auditable.
4. Sensitive actions require explicit user authorization.
5. Platform capabilities are discovered through native adapters and verified against the current OS, device, account, entitlements, agreements and deployment context. Do not hard-code today's restrictions as permanent product limits.
6. Where an integration requires approval, entitlement, managed deployment or a partner agreement, track and pursue that path. Report capability status honestly for each deployment.
7. Keep upstream source isolated from AURA-owned code; complete distribution and licence review before release without blocking prototype work.

## Current development

- `packages/core/` — command envelopes, capability allowlists and deny-by-default policy.
- `packages/runtime/` — local llama.cpp HTTP adapter for desktop/server development.
- `packages/assistant/` — bounded assistant sessions and local inference composition.
- `packages/memory/` — opt-in encrypted memory store.
- `packages/guard/` — Ed25519 device-command security and capability enforcement.
- `packages/recovery-server/` — PostgreSQL device registry, challenge-based enrollment, server-signed commands, acknowledgements, audit events and opt-in location check-ins.
- `recovery-console/` — browser console for enrolled devices and user-confirmed recovery actions.
- `android/` — native llama.cpp JNI runtime, verified small-model download, on-device voice, encrypted opt-in memory and recovery polling.
- `desktop/` — Tauri desktop app with a native Rust bridge to a local llama.cpp server, passphrase-encrypted AES-256-GCM memory vault, Assistant, Memory, Device Guard and Settings views; native bundles are configured for Windows, macOS and Linux.
- `ios/` — SwiftUI app with local llama.cpp chat, Keychain-backed AES-GCM opt-in memory, Keychain-stored recovery account token, Guard device management and Settings, plus an Xcode project and simulator build workflow.

## Verification status

The desktop and iOS targets now connect to a local llama.cpp HTTP server. The iOS target also includes opt-in AES-GCM memory with a device-only Keychain key and a Guard controller for listing devices, queueing approved lock/location commands, clearing stored locations and revoking devices. The desktop app has a native Rust localhost inference bridge, passphrase-encrypted AES-256-GCM memory vault and authenticated Device Guard controller for enrolled-device management and confirmed recovery actions. Cloud workflows have been added, but their first CI builds must pass before either target is considered build-verified. The iOS workflow produces an unsigned Simulator app; a signed physical-device build and distribution require the applicable signing configuration. Desktop installers are produced independently for each runner OS.

The Android native APK build and physical-device test have not yet been confirmed. The recovery API is deployable with Docker Compose or Railway but is not yet confirmed deployed to a live server. iOS Guard control depends on that service being deployed and configured; native iPhone enrollment and local iOS inference against a real computer-hosted model still need end-to-end testing. Desktop and iOS voice integration, native iPhone enrollment, end-to-end recovery testing and cross-device memory sync remain outstanding.

Run workspace tests from a local clone:

```sh
npm install
npm test
```

For recovery deployment instructions, see `packages/recovery-server/README.md`. The local secret bootstrap script is `scripts/bootstrap-recovery-secrets.sh`.

## Upstream dependencies

The initial audit has approved three runtime candidates, pinned in `UPSTREAM_MANIFEST.md`:

- llama.cpp — local LLM inference (MIT)
- whisper.cpp — offline speech-to-text (MIT)
- openWakeWord — wake-word detection (Apache-2.0)

From a local Git clone, run `bash scripts/bootstrap-upstreams.sh` to register and check out the pinned submodules. This requires Git and network access. The script has not been executed by the GitHub connector, so the submodule source trees are not yet present in this repository.

## Platform integration policy

AURA's product roadmap describes intended capabilities. Each platform adapter must inspect and report what the current device and deployment can actually provide. If a capability depends on an OS entitlement, developer agreement, enterprise/managed-device program, regional availability, hardware, or partner approval, record the requirement and pursue it during operationalization. Keep the interface and shared contract capable of representing approval-pending and deployment-specific states.

See [Local inference setup](docs/LOCAL-INFERENCE-SETUP.md), [Architecture](docs/ARCHITECTURE.md), [Security boundaries](docs/SECURITY-BOUNDARIES.md), [Upstream manifest](UPSTREAM_MANIFEST.md) and [Third-party notices](THIRD_PARTY_NOTICES.md).
