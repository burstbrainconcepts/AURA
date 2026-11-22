# AURA Android

AURA is a native Kotlin Android app with a CPU-first llama.cpp JNI runtime.

## Implemented Android features

- The cloud build fetches the pinned llama.cpp revision and compiles it through the Android NDK/CMake toolchain.
- The app offers an explicit first-run download of the Qwen2.5 0.5B Instruct Q4_K_M GGUF starter model (about 398 MB). The model repository revision is pinned and its SHA-256 is verified before loading.
- Chat inference runs in-process through JNI. Conversation text is not sent to a remote inference API.
- The model uses a 2,048-token context and a conservative CPU thread limit.
- Optional speech input uses Android's on-device speech recognizer only; AURA does not fall back to a cloud recognizer.
- Speech output uses an installed voice only when Android reports that voice does not require a network connection.
- Memory is off by default. The user can explicitly enable it, save an individual exchange, review saved items, clear them, or disable memory. Saved memory uses AES-GCM with a key held by Android Keystore.
- Optional screen-lock protection requests only Android's force-lock Device Admin policy. Each local lock action asks for confirmation.
- Remote recovery enrollment uses a device-generated Ed25519 key and a one-time challenge. Device keys, account/device tokens, the server verification key, and replay state are encrypted at rest with Android Keystore.
- Remote commands are signed by the recovery server and verified on-device before processing. The Android client currently executes only the lock command (when Device Admin is enabled) and the recovery check-in command.
- WorkManager checks the recovery queue periodically when Android grants network execution time. The app also has a manual check button.
- Optional lost-device location check-ins are off by default. The user must opt in, grant foreground and background location permissions, and enroll the location capability. Fresh GPS/network fixes are sent over HTTPS during periodic check-ins. Turning the feature off removes the capability and asks the server to clear the last stored location.

## Remote recovery setup

The recovery service is in `packages/recovery-server`. Deploy it behind HTTPS, provision its PostgreSQL database, run migrations, and configure the server signing key and account token. In AURA, select **Link remote recovery**, enter the HTTPS endpoint and account token, and complete enrollment.

The current Android remote-recovery client requires Android 13+ for the platform Ed25519 provider. Background polling is best-effort and can be delayed by Android battery and background-execution policies.

## Known runtime boundaries

- The starter model is deliberately small; response quality and speed are lower than larger hosted models.
- On-device speech recognition and offline TTS depend on compatible language services/voices already installed on the phone.
- A device with insufficient free RAM may fail to load the model.
- Remote session revocation, remote wipe, passcode reset, camera activation, and covert monitoring are not implemented.
- The debug APK is not a production-signed release.

## Cloud build

The **Android cloud build** GitHub Actions workflow fetches the exact pinned llama.cpp commit, installs Android NDK/CMake, runs `gradle :app:assembleDebug`, and uploads `aura-android-debug` as a workflow artifact.
