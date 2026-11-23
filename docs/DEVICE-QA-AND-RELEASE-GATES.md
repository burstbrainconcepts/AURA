# AURA device QA and release gates

This checklist separates CI compilation from installed-app behavior. A platform is not considered release-ready until its applicable device checks pass.

## Current runtime topology

| Platform | Inference path | Model lifecycle | Current delivery state |
| --- | --- | --- | --- |
| Android | Embedded llama.cpp JNI runtime | In-app starter-model download with partial-file resume and SHA-256 verification | Debug APK; physical-device test still required |
| Desktop | Native Rust bridge to a local llama.cpp HTTP server | User installs/runs the server and model separately | Unsigned development installers; model lifecycle UI is a follow-up |
| iOS | SwiftUI client to a configured OpenAI-compatible llama.cpp endpoint | Model/server runs on the iPhone only if a compatible local runtime is independently provided; documented baseline is a trusted-LAN computer server | Unsigned Simulator artifact; physical-device signing and test required |

Do not describe desktop or iOS as self-contained offline inference until the app owns and verifies the complete runtime and model lifecycle on that platform. A LAN-hosted endpoint is local-network inference, but it is not on-device inference.

## Release gates

### Gate 1 — CI and artifact integrity

- [ ] Core workspace tests pass.
- [ ] Android native/JNI and Kotlin build passes; APK signature and SHA-256 are recorded.
- [ ] Desktop Windows, macOS and Linux bundle jobs pass independently.
- [ ] iOS Simulator build passes; a separate signed device build is produced for device testing.
- [ ] Artifacts are downloaded and inspected; filenames, architecture and hashes are recorded.
- [ ] No secrets, API tokens, model weights or private user data are included in artifacts.

### Gate 2 — First launch and setup

- [ ] App opens to a useful state when no model/server is installed or reachable.
- [ ] Missing runtime is explained in plain language with a clear setup action.
- [ ] Large downloads require an explicit user action and clearly show expected size.
- [ ] Download pause, resume, cancel, restart-on-invalid-range, checksum failure and app-relaunch recovery are tested where supported.
- [ ] The app never marks an incomplete or unverified model as ready.
- [ ] Setup failures preserve the rest of the app and provide actionable diagnostics.

### Gate 3 — Inference and offline behavior

- [ ] Send a short prompt and receive a response on each target device.
- [ ] Stop the inference server during a request; the app reports the failure and recovers after restart.
- [ ] Test a longer prompt, a long response and repeated messages without UI lockup.
- [ ] Disconnect internet while keeping the local runtime available; inference continues without a cloud fallback.
- [ ] Disconnect the local runtime; AURA clearly reports that inference is unavailable.
- [ ] Confirm the configured endpoint is the only destination used for inference.
- [ ] Verify model memory requirements and thermal/battery behavior on representative hardware.

### Gate 4 — Voice, memory and privacy

- [ ] Microphone permission is requested only when the user starts voice input.
- [ ] Speech transcript is shown for review before it is sent.
- [ ] No audio is sent to an online transcription service.
- [ ] Voice output uses the configured local/system voice and can be disabled.
- [ ] Memory is opt-in, manually saved and encrypted at rest.
- [ ] Locking memory clears in-memory passphrases and decrypted items.
- [ ] Wrong passphrase, damaged vault and missing encryption key fail safely without deleting existing encrypted data.
- [ ] App logs do not contain prompts, transcripts, passphrases, account tokens or recovery credentials.

### Gate 5 — Guard and multi-device recovery

- [ ] Enroll a device and verify its identity in the account console.
- [ ] Confirm commands are device-bound, signed, expiring and replay-resistant.
- [ ] Destructive or sensitive actions require explicit confirmation.
- [ ] A queued command is not reported as executed until the target acknowledges it.
- [ ] Test offline target, expired command, revoked credential and duplicate delivery.
- [ ] Verify location sharing is off until explicitly enabled and can be cleared/revoked.
- [ ] Run a multi-device recovery drill before enabling production use.

### Gate 6 — Packaging and distribution

- [ ] Test install, launch, update and uninstall on each supported OS.
- [ ] Confirm Windows x64, macOS architecture and Linux x86_64 package formats are labelled accurately.
- [ ] Configure production signing, notarization and provisioning where required.
- [ ] Verify permissions, privacy disclosures, license notices and third-party notices.
- [ ] Publish checksums and a versioned release note with known limitations.
- [ ] Keep debug/simulator artifacts clearly separate from production installers.

## Platform-specific smoke tests

### Android

1. Install the debug APK on a supported ARM64 phone.
2. Launch with no model present; defer the model download and confirm the rest of the UI remains usable.
3. Start the starter-model download, pause it, close/reopen AURA and continue.
4. Complete verification and runtime loading; send a prompt with mobile data and Wi-Fi disabled.
5. Test airplane mode after the model is installed.
6. Test Quick Settings tile and optional floating launcher; deny overlay permission and confirm the app still works.
7. Confirm no microphone capture starts merely because the app or overlay is visible.

### Desktop

1. Install the correct package for the host OS.
2. Launch with llama.cpp stopped; confirm a clear disconnected state.
3. Start llama-server on loopback and connect using the default endpoint.
4. Send prompts, stop the server mid-session, restart it and retry.
5. Test local Whisper setup and push-to-talk; review transcript before sending.
6. Lock and unlock the encrypted memory vault; verify wrong-passphrase behavior.
7. Test Device Guard only against a staging recovery API and test account.

### iOS

1. Install a signed development build on a physical iPhone.
2. Confirm Siri/App Shortcut invocation and normal app launch.
3. Connect to a llama.cpp server on a trusted private Wi-Fi network using an API key.
4. Deny Local Network permission and confirm a useful recovery path.
5. Test app background/foreground, Wi-Fi loss and server restart.
6. Test on-device speech recognition support and verify no silent online fallback.
7. Test Keychain-backed memory and recovery credential behavior after app restart and device reboot.

## Known follow-up engineering work

- Desktop: add a first-run Setup Center and managed local model/server lifecycle; the current UI expects a separately started llama.cpp server.
- iOS: decide and implement a supported embedded on-device inference runtime if fully standalone iPhone inference is a product requirement. Until then, retain clear LAN-server wording.
- Android: validate actual model load, chat, resume behavior and thermal/resource usage on physical ARM64 devices.
- All platforms: define shared capability/status contracts so UI labels reflect what the current device, OS, account and deployment actually support.
- All platforms: run an end-to-end recovery drill and production security review before enabling remote device actions broadly.

## Status vocabulary

Use these terms consistently:

- **CI passed** — compilation/tests/package jobs succeeded.
- **Artifact verified** — downloaded package contents and digest were inspected.
- **Installed tested** — app installed and launched on a real supported device/OS.
- **Feature tested** — the named workflow passed on that device.
- **Release ready** — all applicable gates, signing, privacy, security and distribution checks passed.

Never promote a status based on a lower-level result.
