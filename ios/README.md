# AURA for iOS

Native SwiftUI application target for iPhone and iPad.

## Implemented in this target

- Local assistant chat through the OpenAI-compatible llama.cpp HTTP API.
- Push-to-talk speech input using on-device speech recognition when the current locale and device support it; AURA does not silently fall back to online transcription.
- Optional local speech output using the device's installed text-to-speech voice.
- Configurable local inference URL and optional API key. The key remains in app memory and is not saved in preferences.
- Opt-in encrypted memory using AES-GCM. The encryption key is stored in the iOS Keychain with device-only accessibility; encrypted memory data is stored with complete file protection. Memory is manually saved, reviewable, deletable and can be disabled.
- AURA Guard controller for listing registered devices, queuing explicitly confirmed lock and location-refresh commands, clearing a stored location and revoking device credentials. The account API token is stored in the Keychain after a successful connection.
- Capability-first platform design: approval-dependent features remain in the roadmap and are evaluated against the current device, account, entitlements and agreements.

## Connect local AI

See [Local inference setup](../docs/LOCAL-INFERENCE-SETUP.md). A computer-hosted llama.cpp server must be reachable from the iPhone on a trusted local network. Use API-key authentication when exposing the server to other devices on that network.

## Connect AURA Guard

Deploy the recovery API with PostgreSQL and HTTPS as described in [the recovery server guide](../packages/recovery-server/README.md). In the Guard tab, enter the service URL and account bootstrap token. The app stores the token in the Keychain after it successfully lists devices.

Remote commands are signed and expiring. Lock and location-refresh requests require a confirmation in the app and the target device must be enrolled with the matching capability and online to receive the command. Revocation also requires confirmation. A queued command is not proof that the target device executed it.

The current iOS Guard target is an account controller for other enrolled devices. Native enrollment of the iPhone itself, device-specific recovery execution and additional platform management integrations remain future work.

## Build

Open `AURA.xcodeproj` in Xcode on macOS and build the `AURA` scheme for an iOS Simulator. The GitHub Actions workflow produces an unsigned Simulator app artifact. A signed physical-device build and distribution require the applicable signing configuration and provisioning.
