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
- `ios/` — native SwiftUI application project for iPhone and iPad, including Siri/App Shortcuts and a generated branded AppIcon.
- `docs/PRESENCE-MODE.md` — cross-platform summon, rest and visibility behavior, including OS permission boundaries.
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
- `packages/recovery-server/` — PostgreSQL device registry, challenge-based enrollment, server-signed commands, acknowledgements, account-scoped command history, audit events and opt-in location check-ins.
- `recovery-console/` — browser console for enrolled devices and user-confirmed recovery actions.
- `android/` — native llama.cpp JNI runtime, verified small-model download, on-device voice, encrypted opt-in memory and recovery polling.
- `desktop/` — Tauri desktop app with native Rust bridges to local llama.cpp and whisper.cpp, passphrase-encrypted AES-256-GCM memory vault, push-to-talk mono-WAV speech input, optional local system voice output, Assistant, Memory, Device Guard and Settings views; native bundles are configured for Windows, macOS and Linux. Run `scripts/setup-whisper-server.sh` to build a local multilingual speech server.
- `ios/` — SwiftUI app with local llama.cpp chat, on-device speech recognition and TTS, Keychain-backed AES-GCM opt-in memory, Keychain-stored recovery account token, Guard device management and Settings, plus an Xcode project and simulator build workflow.

## Verification status

**Verified CI results (23 November 2026):**

- **Core:** passing. The latest workflow completed dependency installation, recovery-server JavaScript syntax checks, the full `npm test` workspace suite and the recovery API Docker image build.
- **Desktop:** the latest Windows, macOS and Linux matrix builds all passed with the WAV voice-input integration. GitHub uploaded separate desktop bundle artifacts.
- **iOS:** the SwiftUI simulator app compiled, packaged and uploaded successfully after correcting the App Shortcut utterance. The AppIcon is generated during CI from a native Swift script. This is an unsigned Simulator artifact, not a physical-device IPA.
- **Android:** the native debug APK build passed on the earlier Quick Settings revision. The optional floating AURA launcher and explicit overlay-permission flow are now implemented and awaiting the updated APK build result. The workflow fetches pinned llama.cpp source, compiles JNI/runtime and Kotlin app, verifies the APK, prints its SHA-256 digest and uploads the `aura-android-debug` artifact.

The desktop app has native Rust localhost bridges for llama.cpp inference and whisper.cpp transcription, a passphrase-encrypted AES-256-GCM memory vault, optional local system voice output and authenticated Device Guard controls. Voice recordings are converted to mono WAV in memory, sent only to the configured localhost Whisper server, and inserted as editable text for review before sending. The iOS app has local llama.cpp HTTP chat, offline-capability-checked speech input, local speech output, Keychain-backed AES-GCM opt-in memory, device-bound Ed25519 self-enrollment and Guard controls. Android includes the in-process llama.cpp JNI runtime, verified starter-model download, offline voice, encrypted opt-in memory and signed recovery polling.

**Not yet end-to-end verified:** physical-device installation and testing on each platform (the Android debug APK is CI-built but has not yet been tested on a physical phone); iPhone-native recovery enrollment (now implemented but not yet physical-device tested), on-device model execution; desktop microphone/Whisper end-to-end testing on physical machines; live recovery API deployment and multi-device recovery drills; cross-device memory sync; and production signing/distribution. These are distinct from the passing CI builds. Platform approvals and signing requirements must be checked against the actual release configuration at operationalization.

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

See [Presence Mode](docs/PRESENCE-MODE.md), [Local inference setup](docs/LOCAL-INFERENCE-SETUP.md), [Architecture](docs/ARCHITECTURE.md), [Security boundaries](docs/SECURITY-BOUNDARIES.md), [Upstream manifest](UPSTREAM_MANIFEST.md) and [Third-party notices](THIRD_PARTY_NOTICES.md).
