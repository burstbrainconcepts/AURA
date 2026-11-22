# AURA

**AURA** is a cross-platform, offline-first personal AI assistant and device-protection platform.

- **AURA Assistant** — local-first intelligence, voice interaction, memory, user-approved automation and offline knowledge.
- **AURA Guard** — device registration, recovery credentials, session protection, lost-device workflows and authenticated remote-command queuing.

## Repository structure

- `packages/core/` — AURA-owned shared command and capability contracts.
- `upstream/engines/` — approved third-party inference and voice engines, registered as pinned Git submodules.
- `aurora/` — planned product applications and feature modules.
- `integrations/` — platform adapters around approved upstream components.
- `audits/` — technical, licence and security reviews.
- `docs/` — architecture and security decisions.

## First principles

1. Offline operation is a first-class design requirement.
2. Models, maps and voice assets are downloaded separately; never commit large model weights or private datasets.
3. Remote commands must be signed, device-bound, expiring, replay-resistant and auditable.
4. Sensitive actions require explicit user authorization.
5. AURA respects Android and iOS security boundaries; it does not promise OS capabilities that apps are not granted.
6. Keep upstream source isolated from AURA-owned code; complete distribution and licence review before release without blocking prototype work.

## Current development

The repository now contains working implementation layers rather than only architecture placeholders:

- `packages/core/` — command envelopes, capability allowlists and deny-by-default policy.
- `packages/runtime/` — local llama.cpp HTTP adapter for desktop/server development.
- `packages/assistant/` — bounded assistant sessions and local inference composition.
- `packages/guard/` — Ed25519 device-command security and capability enforcement.
- `packages/recovery-server/` — PostgreSQL device registry, challenge-based enrollment, server-signed commands, acknowledgements, audit events and opt-in location check-ins.
- `recovery-console/` — browser console for enrolled devices, confirmed lock/location requests, revocation and location clearing.
- `android/` — native llama.cpp JNI runtime, verified small-model download, on-device voice, encrypted opt-in memory, Android Device Admin lock, encrypted recovery credentials and WorkManager polling.

The Android remote-recovery client currently targets Android 13+ for its Ed25519 provider. Screen locking requires the user to enable Android's lock-only Device Admin policy. Background location is off by default and requires explicit consent plus Android's background-location permission. Remote location refresh and screen lock are signed, expiring commands; devices must be online and Android may delay background work.

### Verification status

- Focused AURA core, runtime and assistant tests were previously exercised locally.
- Three recovery cryptography tests have been run locally and pass.
- CI now syntax-checks the recovery service, runs workspace tests and builds the recovery Docker image.
- A separate GitHub Actions workflow is configured to compile the Android debug APK against the pinned llama.cpp revision and upload it as an artifact.
- A full repository CI run, native Android APK build and physical-device test have not yet been confirmed.

The recovery API is deployable with Docker Compose or Railway. It is not yet deployed to a live server. iOS native recovery integration is not yet implemented.

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

Research-only candidates and licensing restrictions are documented in the manifest. Never incorporate a project with an unverified or incompatible licence into a distributed build.

## Platform reality

Android and iOS have different device-management permissions. AURA Guard will expose only capabilities actually granted by the operating system. Offline devices cannot receive remote commands until connectivity returns; AURA queues expiring commands and reports their delivery state rather than pretending they were executed.

See [Architecture](docs/ARCHITECTURE.md), [Security boundaries](docs/SECURITY-BOUNDARIES.md), [Upstream manifest](UPSTREAM_MANIFEST.md) and [Third-party notices](THIRD_PARTY_NOTICES.md).
