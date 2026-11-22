# AURA

**AURA** is a cross-platform, offline-first personal AI assistant and device-protection platform.

The project is designed around two connected product areas:

- **AURA Assistant** — local-first intelligence, voice interaction, memory, user-approved automation and offline knowledge.
- **AURA Guard** — device registration, recovery credentials, session protection, lost-device workflows and authenticated remote-command queuing.

## Repository strategy

This repository keeps upstream projects separate from AURA-owned code. Upstreams are to be pinned at exact commits and integrated through narrow adapters. Do not copy source code from an upstream project until its current licence and the intended distribution model have been reviewed.

### Planned layout

```text
AURA/
├── upstream/                 # Git submodules; original projects remain intact
│   ├── android/
│   ├── cross-platform/
│   └── engines/
├── aurora/                   # AURA-owned product code (internal folder name retained)
│   ├── apps/
│   ├── core/
│   ├── intelligence/
│   ├── voice/
│   ├── memory/
│   ├── automation/
│   ├── device-security/
│   └── recovery/
├── integrations/             # Adapters around approved upstream components
├── audits/                   # Technical, licence and security reviews
├── docs/
└── scripts/
```

The GitHub repository is named **AURA**. The internal source directory is currently named `aurora/` to distinguish product code from the repository root; this can be renamed later without changing the GitHub repository name.

## First principles

1. Offline operation is a first-class design requirement.
2. Downloaded models and maps must be optional assets, not committed into Git.
3. Remote commands must be signed, scoped, expiring, replay-resistant and auditable.
4. Sensitive actions require explicit user authorization.
5. AURA must respect Android and iOS platform security boundaries. It cannot promise capabilities the operating system does not expose.
6. Every third-party component must have a recorded source URL, pinned revision, licence and integration status.

## Getting started

See:
- [Architecture](docs/ARCHITECTURE.md)
- [Upstream manifest](UPSTREAM_MANIFEST.md)
- [Third-party notices](THIRD_PARTY_NOTICES.md)
- [Bootstrap script](scripts/bootstrap-upstreams.sh)

The bootstrap script clones the approved upstream candidates as Git submodules. Run it from a local Git environment after reviewing the manifest.

## Current status

This is the initial repository foundation. No upstream source code has been copied into AURA-owned directories yet. The next step is to verify current upstream revisions and licences, then initialize the pinned submodules.
