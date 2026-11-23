# AURA first-run download and setup experience

## Product contract

Every AURA client (Android, iOS and desktop) must present a consistent Setup Center for large optional assets. The interface must distinguish download progress from processing progress and must never imply that an asset is ready before verification and runtime initialization finish.

Each asset has an explicit manifest entry containing:
- stable asset identifier and version
- platform/architecture compatibility
- HTTPS source URL and pinned revision
- expected size (when known)
- SHA-256 digest
- license and attribution metadata
- destination and runtime loader identifier

Do not execute downloaded code or load a model until its manifest, size and SHA-256 checks pass.

## Required user experience

1. Show asset name, purpose, download size, downloaded bytes, percentage, transfer speed and estimated time remaining where the server provides enough information.
2. Provide Pause, Continue and Cancel controls. A paused or interrupted transfer retains a partial file and resumes with HTTP Range requests (or the platform's equivalent resume token).
3. If the server does not support range requests or returns a full response to a range request, safely restart that asset from byte zero rather than corrupting the file.
4. Retry transient network failures with bounded exponential backoff. Preserve partial bytes; expose the retry state and allow manual continuation.
5. Persist transfer state across app restarts. Never mark a partial file as installed.
6. Show distinct processing stages: checksum verification, unpacking/placement, runtime loading, and ready/error.
7. Verify final size and SHA-256 before promoting a partial file to the installed asset name.
8. Allow the user to defer large downloads and continue using features that do not require the missing asset. Never silently consume mobile data.
9. Keep model assets separate from app installers. The installer should contain only the client, native runtime required to start, and essential resources.

## Platform implementation notes

### Android
The native Android client embeds the llama.cpp JNI runtime. Its starter GGUF model is a separately downloaded asset. The current implementation uses a private partial file, HTTP Range continuation, visible progress, explicit pause/continue, bounded retry after network interruption, SHA-256 verification and runtime-load status. The direct-download phone APK is ARM64-only to avoid shipping an unused x86-64 native library. A separate emulator build can be generated if needed.

### Desktop
The current Tauri client calls a locally running llama.cpp server and a separately configured local whisper.cpp server. It does not yet own the model lifecycle in its UI. The desktop Setup Center must add a native Tauri download service with resumable HTTP transfers, progress events, pause/cancel state, atomic file promotion and checksum verification. The model manager must then start/stop the local inference process and report its health rather than assuming an externally launched server exists. Voice model downloads should use the same asset manager.

### iOS
The current SwiftUI client sends inference requests to a configured local OpenAI-compatible endpoint; it does not yet embed llama.cpp or own an on-device model runtime. A model download alone would not make local AI work. First integrate and verify the iOS native inference runtime and its supported device/architecture configuration. Then add a URLSession-based asset manager with live progress, persisted resume data, pause/cancel/continue, checksum verification and runtime-load status. Until that runtime integration exists, show the actual configured inference connection state rather than presenting a model download as usable.

## Acceptance tests

- Interrupt Wi-Fi halfway through a model transfer; reconnect; continuation starts from the saved byte offset without redownloading completed bytes.
- Pause, close and relaunch the app; the saved transfer is visible and can continue.
- Cancel removes the partial asset and leaves the app usable.
- A server that ignores Range causes a clean restart, not a corrupt append.
- A wrong SHA-256 never reaches the model loader and the bad partial is removed.
- A verified asset that fails runtime initialization reports a load error and can be retried without downloading again.
- Progress UI remains responsive while download, hashing and model loading run off the main/UI thread.
- Mobile users can defer large downloads and are never forced to use metered data without an explicit choice.