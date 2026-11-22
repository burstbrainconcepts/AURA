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
6. Every third-party component has a source URL, pinned revision, licence gate and integration status.

## Current development

The first dependency-free AURA Core contracts are in `packages/core/`. They validate command envelopes, model device capability observations and enforce a deny-by-default approval policy. Run:

```sh
npm test
```

The core is not yet a complete remote-command security implementation: cryptographic signature verification, persistent replay protection, secure transport, encrypted storage and native command execution remain to be implemented and tested.

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
