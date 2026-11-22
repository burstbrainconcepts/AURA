# AURA implementation roadmap

This roadmap is the delivery sequence for the product. A phase is complete only when its acceptance checks pass; a document or empty folder is not an implementation.

## Phase 0 — Repository and governance foundation

- [x] Establish the AURA repository and owned-code / upstream separation.
- [x] Document architecture and OS capability boundaries.
- [x] Audit the initial runtime candidates and pin the three approved upstream revisions.
- [x] Add the first dependency-free core package and four policy tests.
- [x] Add a CI workflow definition.
- [ ] Run the pinned-submodule bootstrap from a Git-capable environment and commit .gitmodules/gitlinks.
- [ ] Confirm GitHub Actions reports a passing run on the default branch.

## Phase 1 — Core contracts and security

- [x] Capability snapshot contract.
- [x] Expiring, allowlisted command envelope validation.
- [x] Deny-by-default policy and explicit approval for sensitive actions.
- [ ] Add parameter schemas per command and canonical serialization.
- [ ] Add cryptographic signing and verification with key IDs and rotation.
- [ ] Add device-bound credentials and hardware-backed key storage where supported.
- [ ] Add durable nonce / command-ID replay protection.
- [ ] Add encrypted local persistence and a tamper-evident audit log.
- [ ] Add threat model, abuse cases, security tests and dependency scanning.

Acceptance: invalid signatures, replayed commands, expired commands, wrong-device commands, unapproved actions and unknown parameters are all rejected. No generic shell or arbitrary script execution is exposed.

## Phase 2 — Android foundation (first production client)

- [ ] Create a native Kotlin / Jetpack Compose Android application.
- [ ] Implement onboarding, local profile, device identity and permission explanation.
- [ ] Implement capability discovery and an in-app capability status screen.
- [ ] Integrate llama.cpp behind an AURA-owned inference adapter.
- [ ] Integrate whisper.cpp and wake-word detection behind voice adapters.
- [ ] Add model catalogue, download manager, checksums, storage budget and model deletion.
- [ ] Add local encrypted conversation and settings storage.
- [ ] Add AURA Guard Device Admin flow only where permitted by Android; clearly explain Device Owner provisioning separately.
- [ ] Add offline-first reminders, alarms and local actions using Android APIs.
- [ ] Add accessibility and battery/background execution tests.

Acceptance: chat and supported voice flows work with airplane mode enabled after model download; denied permissions degrade gracefully; unsupported lock or background features are reported accurately.

## Phase 3 — AURA Assistant product features

- [ ] Conversation manager and streaming response UI.
- [ ] Memory store with user-visible review, correction, export and deletion.
- [ ] Retrieval over user-selected local files with source attribution.
- [ ] Tool registry with typed schemas, least privilege and per-action approval.
- [ ] Task/reminder/calendar adapters with explicit permission.
- [ ] Local-first settings, backup/export and restore.
- [ ] Optional remote model providers, disabled by default and clearly disclosed.

Acceptance: assistant works without network for all downloaded local capabilities; sensitive tool calls cannot execute without policy approval; user data remains local unless the user enables a remote provider or sync.

## Phase 4 — AURA Guard and recovery service

- [ ] Device registry and device revocation.
- [ ] Passkey-based account authentication and recovery-code ceremony.
- [ ] Signed command creation, delivery queue and expiration.
- [ ] Device check-in and last-seen / last-known-location status with consent.
- [ ] Separate requested, queued, delivered, approved, executed, failed and expired states.
- [ ] Session revocation and account/device security events.
- [ ] Abuse prevention, rate limits, anomaly detection and recovery audit trail.

Acceptance: disconnected devices show queued / expired states accurately; no remote action is described as completed until a signed device acknowledgement arrives; recovery cannot bypass platform security controls.

## Phase 5 — Cross-platform clients

- [ ] iOS native Swift / SwiftUI client with only Apple-supported capabilities.
- [ ] Desktop client using Tauri and a shared web UI where appropriate.
- [ ] Shared command schemas and adapter contract tests.
- [ ] Secure pairing and optional encrypted synchronization.
- [ ] Per-platform feature matrix and graceful fallback.

Acceptance: each platform exposes only capabilities it actually supports; cross-platform contract tests pass; iOS does not claim arbitrary passcode reset, forced camera capture or Find My replacement.

## Phase 6 — Production readiness

- [ ] Reproducible builds, SBOM and third-party notices.
- [ ] Release signing and secure key custody.
- [ ] Backup/restore, migration and data deletion tests.
- [ ] Performance profiling on low-memory CPU-only devices.
- [ ] Accessibility, localization and privacy review.
- [ ] Penetration test and incident-response runbook.
- [ ] Staged release, crash reporting opt-in and rollback plan.

## Resource strategy

- Keep the shared core dependency-light and testable on CPU.
- Use quantized small models first; make larger models optional downloads.
- Treat model size, RAM use, latency, battery and thermal behaviour as measured acceptance criteria.
- Do not require a paid GPU for the first release.
- Use optional cloud inference only as an opt-in fallback, never as a hidden dependency for core offline features.

## Current truthful status

The repository contains Phase 0 documentation and the first core contract prototype. The first four core tests pass in a local Node.js workspace check. The pinned upstreams have not yet been cloned into this repository; GitHub file APIs cannot run `git submodule add` or create actual gitlink objects. Native clients, cloud services and device-level execution are not implemented yet.
